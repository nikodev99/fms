package org.gu.fms.desktop.features.loan;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.SystemCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionSource;
import org.gu.fms.domain.loan.*;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Emprunts (instructions §7) : tableau d'amortissement, déblocage des fonds, paiement des échéances.
 *
 * <ul>
 *   <li>tant qu'aucune échéance n'est payée, les conditions se corrigent et le tableau est recalculé ;</li>
 *   <li>une échéance payée produit trois sorties liées : capital (financement), intérêts et assurance (charges
 *       financières) — le capital remboursé n'est jamais une charge d'exploitation ;</li>
 *   <li>les échéances se paient dans l'ordre ; un paiement se contre-passe (le dernier d'abord), l'échéance redevient
 *       à payer ;</li>
 *   <li>un emprunt sans échéance payée s'annule avec un motif : le déblocage est contre-passé.</li>
 * </ul>
 */
@Singleton
public final class LoanService implements CrudService<Loan> {

    static final String AUDIT_TYPE = "loan";

    private final LoanRepository loans;
    private final UserSession session;
    private final LoanInstalmentRepository instalments;
    private final VehicleRepository vehicles;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Ledger ledger;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Formats formats;
    private final AppSettings settings;

    @Inject
    public LoanService(LoanRepository loans, LoanInstalmentRepository instalments, VehicleRepository vehicles,
                       AccountRepository accounts, CategoryRepository categories, Ledger ledger, TransactionManager tx,
                       AuditLog audit, Formats formats, AppSettings settings, UserSession session) {
        this.session = session;
        this.loans = loans;
        this.instalments = instalments;
        this.vehicles = vehicles;
        this.accounts = accounts;
        this.categories = categories;
        this.ledger = ledger;
        this.tx = tx;
        this.audit = audit;
        this.formats = formats;
        this.settings = settings;
    }

    @Override
    public List<Loan> findAll() {
        return loans.findAll();
    }

    public List<LoanInstalment> instalments(long loanId) {
        return instalments.findByLoan(loanId);
    }

    /** Aperçu du tableau (formulaire) ; null si les conditions sont incomplètes ou incohérentes. */
    public static LoanSchedule.Result preview(Loan loan) {
        Loan l = LoanRules.normalize(loan);
        if (!LoanRules.validate(l).isEmpty()) {
            return null;
        }
        try {
            return LoanRules.schedule(l);
        } catch (IllegalArgumentException | ArithmeticException e) {
            return null;
        }
    }

    @Override
    public Loan save(Loan input) {
        Loan loan = LoanRules.normalize(input);
        List<FieldError> errors = new ArrayList<>(LoanRules.validate(loan));
        return tx.inTransaction(c -> {
            Loan before = loan.id() == null ? null : loans.findById(loan.id())
                    .orElseThrow(() -> new StaleDataException(AUDIT_TYPE, loan.id(), loan.version()));
            for (Long v : loan.vehicleIds()) {
                if (vehicles.findById(v).isEmpty()) {
                    errors.add(FieldError.of("vehicleIds", "loan.vehicleUnknown"));
                }
            }
            if (loan.disbursementAccountId() != null && accounts.findById(loan.disbursementAccountId()).isEmpty()) {
                errors.add(FieldError.of("disbursementAccountId", "loan.accountUnknown"));
            }
            if (before != null) {
                checkChange(before, loan, errors);
            }
            ValidationException.throwIfAny(errors);
            return before == null ? create(loan) : update(before, loan);
        });
    }

    private void checkChange(Loan before, Loan after, List<FieldError> errors) {
        if (before.status() != LoanStatus.ACTIVE && (LoanRules.termsChanged(before, after)
                || LoanRules.disbursementChanged(before, after))) {
            errors.add(FieldError.of(null, "loan.closedLocked"));
        }
        if (LoanRules.termsChanged(before, after) && instalments.countPaid(before.id()) > 0) {
            errors.add(FieldError.of(null, "loan.termsLocked"));
        }
        if (LoanRules.disbursementChanged(before, after) && before.disbursementTransactionId() != null) {
            errors.add(FieldError.of("disbursementAccountId", "loan.disbursementLocked"));
        }
    }

    private Loan create(Loan loan) {
        Loan ready = loan.withStatus(LoanStatus.ACTIVE, null).withDisbursementTransaction(null);
        long id = loans.insert(ready);
        Loan saved = ready.withIdentity(id, 0);
        loans.replaceVehicles(id, saved.vehicleIds());
        writeSchedule(saved);
        audit.created(AUDIT_TYPE, id, loans.snapshot(saved));
        return postDisbursementIfNeeded(saved);
    }

    private Loan update(Loan before, Loan loan) {
        Loan ready = new Loan(loan.id(), loan.version(), loan.lender(), loan.reference(), loan.purpose(),
                loan.principal(), loan.annualRate(), loan.termCount(), loan.frequency(), loan.firstDueDate(),
                loan.insurancePerInstalment(), loan.bankInstalment(), loan.disbursementDate(),
                loan.disbursementAccountId(), before.disbursementTransactionId(), before.status(),
                before.cancelReason(), loan.notes(), loan.vehicleIds());
        long version = loans.update(ready);
        loans.replaceVehicles(ready.id(), ready.vehicleIds());
        if (LoanRules.termsChanged(before, ready)) {
            instalments.deleteUnpaid(ready.id());
            writeSchedule(ready);
        }
        audit.updated(AUDIT_TYPE, ready.id(), loans.snapshot(before), loans.snapshot(ready), null);
        return postDisbursementIfNeeded(ready.withIdentity(ready.id(), version));
    }

    private void writeSchedule(Loan loan) {
        LoanSchedule.Result result = LoanRules.schedule(loan);
        for (LoanSchedule.Line line : result.lines()) {
            instalments.insert(new LoanInstalment(null, 0, loan.id(), line.seq(), line.dueDate(), money(line.principal()),
                    money(line.interest()), money(line.insurance()), money(line.total()), money(line.balanceAfter()),
                    InstalmentStatus.DUE, null, null, null, null, null));
        }
    }

    /** Fonds reçus sur un compte : entrée « Emprunt reçu » (financement) à la date de déblocage. */
    private Loan postDisbursementIfNeeded(Loan loan) {
        if (loan.disbursementAccountId() == null || loan.disbursementTransactionId() != null) {
            return loan;
        }
        FinancialTransaction t = ledger.post(new FinancialTransaction(null, 0, loan.disbursementDate(), null,
                TransactionDirection.INFLOW, categories.system(SystemCategory.LOAN_DISBURSEMENT).id(), null,
                loan.disbursementAccountId(), loan.principal(), singleVehicle(loan), null,
                "Déblocage — " + loan.label(), loan.reference(), TransactionSource.LOAN_DISBURSEMENT, loan.id(), null,
                null, null));
        Loan withTx = loan.withDisbursementTransaction(t.id());
        long version = loans.update(withTx);
        return withTx.withIdentity(withTx.id(), version);
    }

    private static Long singleVehicle(Loan loan) {
        return loan.vehicleIds().size() == 1 ? loan.vehicleIds().get(0) : null;
    }

    /**
     * Paie une échéance : capital, intérêts et assurance sortent du compte choisi, à la date du paiement.
     * Les échéances se paient dans l'ordre.
     */
    public LoanInstalment pay(long instalmentId, Long accountId, LocalDate date, String reference) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "accountId", accountId);
        TextRules.required(errors, "date", date);
        ValidationException.throwIfAny(errors);
        return tx.inTransaction(c -> {
            LoanInstalment i = instalments.findById(instalmentId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "loan.instalmentUnknown")));
            Loan loan = loans.findById(i.loanId()).orElseThrow();
            if (loan.status() != LoanStatus.ACTIVE || i.status() != InstalmentStatus.DUE) {
                throw ValidationException.of(FieldError.of(null, "loan.instalmentNotDue"));
            }
            boolean earlierDue = instalments.findByLoan(loan.id()).stream()
                    .anyMatch(o -> o.seq() < i.seq() && o.status() == InstalmentStatus.DUE);
            if (earlierDue) {
                throw ValidationException.of(FieldError.of(null, "loan.payInOrder"));
            }
            String label = "Échéance " + i.seq() + "/" + loan.termCount() + " — " + loan.label();
            String ref = TextRules.normalizeSpaces(reference);
            Long vehicle = singleVehicle(loan);
            Long principalTx = post(SystemCategory.LOAN_PRINCIPAL, i.principal(), accountId, date, vehicle, label, ref, i);
            Long interestTx = post(SystemCategory.LOAN_INTEREST, i.interest(), accountId, date, vehicle, label, ref, i);
            Long insuranceTx = post(SystemCategory.LOAN_INSURANCE, i.insurance(), accountId, date, vehicle, label, ref, i);
            LoanInstalment paid = i.paid(date, accountId, principalTx, interestTx, insuranceTx);
            long version = instalments.update(paid);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("loan", loan.id());
            details.put("seq", i.seq());
            details.put("total", i.total().minor());
            details.put("account", accountId);
            details.put("date", date.toString());
            audit.event("loan_instalment", i.id(), AuditAction.PAY, details, null);
            boolean allPaid = instalments.findByLoan(loan.id()).stream()
                    .noneMatch(o -> o.status() == InstalmentStatus.DUE);
            if (allPaid) {
                loans.update(loan.withStatus(LoanStatus.REPAID, null));
                audit.event(AUDIT_TYPE, loan.id(), AuditAction.UPDATE, Map.of("status", "REPAID"), null);
            }
            return paid.withIdentity(paid.id(), version);
        });
    }

    private Long post(SystemCategory category, Money amount, Long accountId, LocalDate date, Long vehicle, String label,
                      String reference, LoanInstalment i) {
        if (amount == null || !amount.isPositive()) {
            return null;
        }
        return ledger.post(new FinancialTransaction(null, 0, date, null, TransactionDirection.OUTFLOW,
                categories.system(category).id(), null, accountId, amount, vehicle, null, label, reference,
                TransactionSource.LOAN, i.id(), null, null, null)).id();
    }

    /**
     * Contre-passe le paiement d'une échéance (les trois sorties) ; l'échéance redevient à payer. Seul le dernier
     * paiement d'un emprunt peut être contre-passé. Renvoie la contre-passation de {@code transactionId} si fourni.
     */
    public FinancialTransaction reversePayment(long instalmentId, String reason, Long transactionId) {
        session.requireOwner();
        String text = TextRules.normalize(reason);
        if (text == null) {
            throw ValidationException.of(FieldError.of("reason", "transaction.reversal.reasonRequired"));
        }
        return tx.inTransaction(c -> {
            LoanInstalment i = instalments.findById(instalmentId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "loan.instalmentUnknown")));
            if (i.status() != InstalmentStatus.PAID) {
                throw ValidationException.of(FieldError.of(null, "loan.instalmentNotPaid"));
            }
            Loan loan = loans.findById(i.loanId()).orElseThrow();
            boolean laterPaid = instalments.findByLoan(loan.id()).stream()
                    .anyMatch(o -> o.seq() > i.seq() && o.status() == InstalmentStatus.PAID);
            if (laterPaid) {
                throw ValidationException.of(FieldError.of(null, "loan.reverseLatestFirst"));
            }
            FinancialTransaction wanted = null;
            for (Long id : new Long[] {i.principalTxId(), i.interestTxId(), i.insuranceTxId()}) {
                if (id != null) {
                    FinancialTransaction r = ledger.reverse(id, text, ledger.today());
                    if (Objects.equals(id, transactionId)) {
                        wanted = r;
                    }
                }
            }
            instalments.update(i.unpaid());
            if (loan.status() == LoanStatus.REPAID) {
                loans.update(loan.withStatus(LoanStatus.ACTIVE, null));
            }
            audit.event("loan_instalment", i.id(), AuditAction.REVERSE, Map.of("loan", loan.id(), "seq", i.seq()), text);
            return wanted;
        });
    }

    /** Annule un emprunt saisi par erreur ou jamais débloqué : aucune échéance ne doit être payée. */
    public Loan cancel(long loanId, String reason) {
        session.requireOwner();
        String text = TextRules.normalize(reason);
        if (text == null) {
            throw ValidationException.of(FieldError.of("reason", "loan.cancel.reasonRequired"));
        }
        return tx.inTransaction(c -> {
            Loan loan = loans.findById(loanId).orElseThrow();
            if (loan.status() == LoanStatus.CANCELLED) {
                throw ValidationException.of(FieldError.of(null, "loan.alreadyCancelled"));
            }
            if (instalments.countPaid(loanId) > 0) {
                throw ValidationException.of(FieldError.of(null, "loan.cancelHasPayments"));
            }
            if (loan.disbursementTransactionId() != null) {
                ledger.reverse(loan.disbursementTransactionId(), text, ledger.today());
            }
            instalments.cancelUnpaid(loanId);
            Loan cancelled = loan.withStatus(LoanStatus.CANCELLED, text);
            long version = loans.update(cancelled);
            audit.event(AUDIT_TYPE, loanId, AuditAction.CANCEL, Map.of("status", "CANCELLED"), text);
            return cancelled.withIdentity(loanId, version);
        });
    }

    /** Capital restant dû d'un emprunt en cours (après la dernière échéance payée). */
    public static Money outstanding(Loan loan, List<LoanInstalment> schedule, Money zero) {
        if (loan.status() != LoanStatus.ACTIVE) {
            return zero;
        }
        long paidPrincipal = schedule.stream().filter(LoanInstalment::isPaid).mapToLong(i -> i.principal().minor()).sum();
        return Money.of(loan.principal().minor() - paidPrincipal, zero.currency());
    }

    private Money money(long minor) {
        return Money.of(minor, settings.currency());
    }

    String formatMoney(Money m) {
        return formats.money(m);
    }
}
