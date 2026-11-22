package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.collection.CollectionRepository;
import org.gu.fms.desktop.features.collection.DriverLedgerService;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.LedgerRubric;
import org.gu.fms.domain.collection.LedgerSource;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.Driver.DriverStatus;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.SystemCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionSource;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.payroll.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Paie mensuelle des chauffeurs (instructions §4.7). Calcul dans le domaine ({@link PayrollCalculator}) ; ici :
 * données d'entrée, cycle du bulletin et effets financiers.
 *
 * <ul>
 *   <li>Préparer : un bulletin brouillon par chauffeur actif (ou ayant versé sur le mois).</li>
 *   <li>Brouillon : ajustement du brut (motif) et retenues diminuables (note obligatoire) ; recalculable.</li>
 *   <li>Valider : recalcul de contrôle ; si un montant a changé, le brouillon est mis à jour et l'utilisateur
 *       doit le revoir. Sinon les montants sont figés et les retenues portées au compte chauffeur.</li>
 *   <li>Payer : sortie de trésorerie « Salaires nets versés » (paiement fait hors application).</li>
 *   <li>Annuler : brouillon, ou validé non payé (retenues rendues au compte chauffeur).</li>
 * </ul>
 */
@Singleton
public final class PayrollService implements CrudService<Payslip> {

    static final String AUDIT_TYPE = "payslip";

    private final PayslipRepository payslips;
    private final DriverRepository drivers;
    private final CollectionRepository collections;
    private final PayrollContributionRepository contributions;
    private final ItsBandRepository itsBands;
    private final ItsSettingsService its;
    private final RecoveryPolicyService policy;
    private final DriverLedgerService driverLedger;
    private final CategoryRepository categories;
    private final Ledger ledger;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;
    private final AppSettings settings;

    @Inject
    public PayrollService(PayslipRepository payslips, DriverRepository drivers, CollectionRepository collections,
                          PayrollContributionRepository contributions, ItsBandRepository itsBands,
                          ItsSettingsService its, RecoveryPolicyService policy, DriverLedgerService driverLedger,
                          CategoryRepository categories, Ledger ledger, TransactionManager tx, AuditLog audit,
                          Clock clock, AppSettings settings) {
        this.payslips = payslips;
        this.drivers = drivers;
        this.collections = collections;
        this.contributions = contributions;
        this.itsBands = itsBands;
        this.its = its;
        this.policy = policy;
        this.driverLedger = driverLedger;
        this.categories = categories;
        this.ledger = ledger;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public List<Payslip> findAll() {
        return payslips.findAll();
    }

    public List<Payslip> findForPeriod(YearMonth period) {
        return payslips.findForPeriod(period);
    }

    public Payslip withLines(long id) {
        return payslips.findWithLines(id).orElseThrow();
    }

    /** Calcul actuel d'un bulletin avec ses paramètres (pour l'affichage des plafonds et alertes). */
    public PayrollResult calculation(Payslip p) {
        return PayrollCalculator.calculate(input(p.period(), driver(p.driverId()), p.adjustment(), requested(p)));
    }

    /** Crée les bulletins brouillons manquants du mois ; renvoie leur nombre. */
    public int prepare(YearMonth period) {
        if (period.atDay(1).isAfter(ledger.today())) {
            throw ValidationException.of(FieldError.of("period", "payroll.periodFuture"));
        }
        return tx.inTransaction(c -> {
            Set<Long> ids = new LinkedHashSet<>();
            for (Driver d : drivers.findAll()) {
                if (d.status() == DriverStatus.ACTIVE) {
                    ids.add(d.id());
                }
            }
            ids.addAll(collections.driversBetween(period.atDay(1), period.atEndOfMonth()));
            int created = 0;
            for (Long id : ids) {
                if (payslips.findActive(period, id).isPresent()) {
                    continue;
                }
                Driver d = driver(id);
                Money zero = Money.zero(settings.currency());
                PayrollResult r = PayrollCalculator.calculate(input(period, d, zero, null));
                Payslip draft = Payslip.draft(null, 0, period, id, d.payScheme(), base(period, id), zero, null, null, r);
                long newId = payslips.insert(draft);
                payslips.replaceLines(newId, r.lines());
                audit.created(AUDIT_TYPE, newId, payslips.snapshot(draft.withIdentity(newId, 0)));
                created++;
            }
            return created;
        });
    }

    /** Recalcule les brouillons du mois avec les données actuelles. */
    public int recomputeDrafts(YearMonth period) {
        int count = 0;
        for (Payslip p : payslips.findForPeriod(period)) {
            if (p.status() == PayslipStatus.DRAFT) {
                recompute(p.id());
                count++;
            }
        }
        return count;
    }

    /** Recalcule un brouillon (versements, cotisations, soldes du compte chauffeur ont pu changer). */
    public Payslip recompute(long id) {
        return tx.inTransaction(c -> {
            Payslip p = draftOrFail(id);
            Driver d = driver(p.driverId());
            Recoveries wanted = requested(p);
            PayrollResult probe = PayrollCalculator.calculate(input(p.period(), d, p.adjustment(), null));
            Recoveries clamped = wanted == null ? null : clamp(wanted, probe.maxRecoveries());
            PayrollResult r = clamped == null ? probe
                    : PayrollCalculator.calculate(input(p.period(), d, p.adjustment(), clamped));
            return store(p, d, p.adjustment(), p.adjustmentReason(), p.recoveryNote(), r);
        });
    }

    /** Enregistre un brouillon modifié : ajustement du brut et retenues demandées. */
    @Override
    public Payslip save(Payslip input) {
        if (input.isNew()) {
            throw ValidationException.of(FieldError.of(null, "payroll.prepareFirst"));
        }
        return tx.inTransaction(c -> {
            Payslip p = draftOrFail(input.id());
            if (p.version() != input.version()) {
                throw new StaleDataException(AUDIT_TYPE, p.id(), input.version());
            }
            Driver d = driver(p.driverId());
            Money zero = Money.zero(settings.currency());
            Money adjustment = input.adjustment() == null ? zero : input.adjustment();
            String reason = TextRules.normalize(input.adjustmentReason());
            String note = TextRules.normalize(input.recoveryNote());
            List<FieldError> errors = new ArrayList<>();
            if (!adjustment.isZero() && reason == null) {
                errors.add(FieldError.of("adjustmentReason", "payroll.adjustmentReasonRequired"));
            }
            TextRules.maxLength(errors, "adjustmentReason", reason, 300);
            TextRules.maxLength(errors, "recoveryNote", note, 300);
            PayrollResult probe = PayrollCalculator.calculate(input(p.period(), d, adjustment, null));
            Recoveries wanted = input.recoveries();
            errors.addAll(PayrollCalculator.validateRequested(wanted, probe.maxRecoveries()));
            boolean lowered = errors.isEmpty() && !wanted.equals(probe.maxRecoveries());
            if (lowered && note == null) {
                errors.add(FieldError.of("recoveryNote", "payroll.recoveryNoteRequired"));
            }
            ValidationException.throwIfAny(errors);
            PayrollResult r = lowered ? PayrollCalculator.calculate(input(p.period(), d, adjustment, wanted)) : probe;
            return store(p, d, adjustment, reason, lowered ? note : null, r);
        });
    }

    /**
     * Valide un brouillon. Un recalcul de contrôle a lieu d'abord ; s'il change un montant, le brouillon mis à jour
     * est conservé et la validation refusée pour qu'il soit revu.
     */
    public Payslip validate(long id) {
        Payslip before = draftOrFail(id);
        Payslip fresh = recompute(id);
        if (!sameAmounts(before, fresh)) {
            throw ValidationException.of(FieldError.of(null, "payroll.changedReview"));
        }
        return tx.inTransaction(c -> {
            Payslip p = draftOrFail(id);
            if (p.netPayable().isNegative()) {
                throw ValidationException.of(FieldError.of(null, "payroll.warning.negativeNet"));
            }
            Payslip validated = p.validated(ledger.user(), clock.instant());
            long version = payslips.update(validated);
            LocalDate today = ledger.today();
            String label = "Retenue sur salaire " + p.period();
            driverLedger.post(p.driverId(), today, LedgerRubric.ADVANCE, -p.advanceRecovered().minor(),
                    LedgerSource.PAYROLL, id, label);
            driverLedger.post(p.driverId(), today, LedgerRubric.SHORTAGE, -p.shortageRecovered().minor(),
                    LedgerSource.PAYROLL, id, label);
            driverLedger.post(p.driverId(), today, LedgerRubric.OTHER_DEBT, -p.otherDebtRecovered().minor(),
                    LedgerSource.PAYROLL, id, label);
            audit.event(AUDIT_TYPE, id, AuditAction.VALIDATE, Map.of("net_payable", p.netPayable()), null);
            return validated.withIdentity(id, version);
        });
    }

    /** Enregistre le paiement (fait hors application) : sortie de trésorerie liée au bulletin. */
    public Payslip pay(long id, Long accountId, LocalDate date, String reference) {
        return tx.inTransaction(c -> {
            Payslip p = payslips.findById(id)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "payroll.unknown")));
            if (p.status() != PayslipStatus.VALIDATED) {
                throw ValidationException.of(FieldError.of(null, "payroll.payValidatedOnly"));
            }
            String ref = TextRules.normalizeSpaces(reference);
            Payslip paid;
            if (p.netPayable().isZero()) {
                paid = p.paid(date == null ? ledger.today() : date, null, ref, null);
            } else {
                List<FieldError> errors = new ArrayList<>();
                TextRules.required(errors, "accountId", accountId);
                TextRules.required(errors, "date", date);
                ValidationException.throwIfAny(errors);
                Driver d = driver(p.driverId());
                FinancialTransaction posted = ledger.post(new FinancialTransaction(null, 0, date, null,
                        TransactionDirection.OUTFLOW, categories.system(SystemCategory.NET_SALARY).id(), null, accountId,
                        p.netPayable(), null, d.id(), "Salaire " + p.period() + " — " + d.displayName(), ref,
                        TransactionSource.PAYROLL, id, null, null, null));
                paid = p.paid(date, accountId, ref, posted.id());
            }
            long version = payslips.update(paid);
            audit.event(AUDIT_TYPE, id, AuditAction.PAY, Map.of("amount", p.netPayable()), ref);
            return paid.withIdentity(id, version);
        });
    }

    /** Paiement contre-passé (depuis l'écran Transactions ou le bulletin) : retour à l'état validé. */
    public FinancialTransaction reversePayment(long id, String reason) {
        return tx.inTransaction(c -> {
            Payslip p = payslips.findById(id)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "payroll.unknown")));
            if (p.status() != PayslipStatus.PAID) {
                throw ValidationException.of(FieldError.of(null, "payroll.notPaid"));
            }
            FinancialTransaction reversal = p.transactionId() == null ? null
                    : ledger.reverse(p.transactionId(), reason, ledger.today());
            payslips.update(p.unpaid());
            audit.event(AUDIT_TYPE, id, AuditAction.REVERSE, Map.of("amount", p.netPayable()), reason);
            return reversal;
        });
    }

    /** Annule un brouillon ou un bulletin validé non payé ; les retenues validées sont rendues au compte chauffeur. */
    public Payslip cancel(long id, String reason) {
        String text = TextRules.normalize(reason);
        if (text == null) {
            throw ValidationException.of(FieldError.of("reason", "payroll.cancelReasonRequired"));
        }
        return tx.inTransaction(c -> {
            Payslip p = payslips.findById(id)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "payroll.unknown")));
            if (p.status() == PayslipStatus.PAID) {
                throw ValidationException.of(FieldError.of(null, "payroll.cancelPaid"));
            }
            if (p.status() == PayslipStatus.CANCELLED) {
                throw ValidationException.of(FieldError.of(null, "payroll.alreadyCancelled"));
            }
            if (p.status() == PayslipStatus.VALIDATED) {
                driverLedger.reverseBySource(LedgerSource.PAYROLL, id, ledger.today(),
                        "Annulation du bulletin " + p.period());
            }
            Payslip cancelled = p.cancelled(text);
            long version = payslips.update(cancelled);
            audit.event(AUDIT_TYPE, id, AuditAction.CANCEL, Map.of("status_before", p.status()), text);
            return cancelled.withIdentity(id, version);
        });
    }

    // ---- Calcul ---------------------------------------------------------------------------------

    private PayrollInput input(YearMonth period, Driver d, Money adjustment, Recoveries requested) {
        return new PayrollInput(period, d, base(period, d.id()), adjustment, contributions.findAll(), its.load(),
                itsBands.findAll(), driverLedger.balance(d.id()), policy.load(), requested);
    }

    private Money base(YearMonth period, long driverId) {
        return Money.of(collections.receivedBetween(driverId, period.atDay(1), period.atEndOfMonth()),
                settings.currency());
    }

    /** Retenues choisies par l'utilisateur (note renseignée) ; null = retenues proposées. */
    private static Recoveries requested(Payslip p) {
        return p.recoveryNote() == null ? null : p.recoveries();
    }

    private Payslip store(Payslip p, Driver d, Money adjustment, String reason, String note, PayrollResult r) {
        Payslip updated = Payslip.draft(p.id(), p.version(), p.period(), p.driverId(), d.payScheme(),
                base(p.period(), d.id()), adjustment, reason, note, r);
        long version = payslips.update(updated);
        payslips.replaceLines(p.id(), r.lines());
        audit.updated(AUDIT_TYPE, p.id(), payslips.snapshot(p), payslips.snapshot(updated), null);
        return updated.withIdentity(p.id(), version);
    }

    private Payslip draftOrFail(long id) {
        Payslip p = payslips.findById(id)
                .orElseThrow(() -> ValidationException.of(FieldError.of(null, "payroll.unknown")));
        if (p.status() != PayslipStatus.DRAFT) {
            throw ValidationException.of(FieldError.of(null, "payroll.notDraft"));
        }
        return p;
    }

    private Driver driver(long id) {
        return drivers.findById(id).orElseThrow(() -> ValidationException.of(FieldError.of(null, "payroll.driverUnknown")));
    }

    private static Recoveries clamp(Recoveries wanted, Recoveries max) {
        return new Recoveries(min(wanted.advance(), max.advance()), min(wanted.shortage(), max.shortage()),
                min(wanted.otherDebt(), max.otherDebt()));
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static boolean sameAmounts(Payslip a, Payslip b) {
        Map<String, Object> x = amounts(a);
        Map<String, Object> y = amounts(b);
        return Objects.equals(x, y);
    }

    private static Map<String, Object> amounts(Payslip p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("base", p.collectionsBase());
        m.put("gross", p.gross());
        m.put("employee", p.employeeContributions());
        m.put("its", p.its());
        m.put("recoveries", p.recoveries());
        m.put("net", p.netPayable());
        m.put("employer", p.employerCharges());
        return m;
    }
}
