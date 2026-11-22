package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d'entrée UNIQUE de toutes les écritures de trésorerie (versements, virements, clôtures, paie, saisies
 * directes). Chaque écriture passe les mêmes contrôles :
 * <ul>
 *   <li>règles du domaine ({@link TransactionRules}) et catégorie du bon sens ;</li>
 *   <li>compte actif, date ni antérieure à l'ouverture du compte ni dans une journée clôturée ;</li>
 *   <li>solde jamais négatif à aucune date à partir de l'écriture, sauf découvert autorisé ;</li>
 *   <li>nature recopiée de la catégorie, auteur et horodatage, trace d'audit dans la même transaction.</li>
 * </ul>
 * Les appels se font dans la transaction SQL de l'appelant (ou en ouvrent une) : tout ou rien.
 */
@Singleton
public final class Ledger {

    static final String AUDIT_TYPE = "financial_transaction";

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final ClosingRepository closings;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final UserSession session;
    private final Clock clock;
    private final AppSettings settings;
    private final Formats formats;

    @Inject
    public Ledger(TransactionRepository transactions, AccountRepository accounts, CategoryRepository categories,
                  ClosingRepository closings, TransactionManager tx, AuditLog audit, UserSession session, Clock clock,
                  AppSettings settings, Formats formats) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.closings = closings;
        this.tx = tx;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
        this.settings = settings;
        this.formats = formats;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    public String user() {
        return session.currentUser().displayName();
    }

    /** Enregistre une transaction après tous les contrôles ; renvoie la transaction enregistrée. */
    public FinancialTransaction post(FinancialTransaction draft) {
        return tx.inTransaction(c -> {
            FinancialTransaction t = TransactionRules.normalize(draft).withRecording(clock.instant(), user());
            TransactionCategory category = t.categoryId() == null ? null
                    : categories.findById(t.categoryId()).orElse(null);
            List<FieldError> errors = new ArrayList<>(TransactionRules.validate(t, category, today()));
            if (category != null && !category.active() && !t.isReversal()) {
                errors.add(FieldError.of("categoryId", "transaction.category.inactive", category.name()));
            }
            FinancialAccount account = t.accountId() == null ? null : accounts.findById(t.accountId()).orElse(null);
            if (t.accountId() != null && account == null) {
                errors.add(FieldError.of("accountId", "transaction.account.unknown"));
            }
            if (account != null && t.date() != null && t.amount() != null && errors.isEmpty()) {
                checkAccount(account, t.date(), t.isReversal(), ownClosingDifference(t), "accountId", errors);
                checkBalance(account, t.date(), t.signedMinor(), "amount", errors);
            }
            ValidationException.throwIfAny(errors);
            FinancialTransaction ready = t.withNature(category.nature());
            long id = transactions.insert(ready);
            FinancialTransaction saved = ready.withIdentity(id, 0);
            audit.created(AUDIT_TYPE, id, transactions.snapshot(saved));
            return saved;
        });
    }

    /**
     * Contre-passe une transaction : écriture opposée datée de {@code date} (en principe aujourd'hui), liée à
     * l'originale. Ne déclenche AUCUN effet de module : utiliser {@code ReversalService} depuis l'interface.
     */
    public FinancialTransaction reverse(long transactionId, String reason, LocalDate date) {
        return tx.inTransaction(c -> {
            FinancialTransaction original = transactions.findById(transactionId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "transaction.unknown")));
            if (original.isReversal()) {
                throw ValidationException.of(FieldError.of(null, "transaction.reversal.ofReversal"));
            }
            if (original.isReversed()) {
                throw ValidationException.of(FieldError.of(null, "transaction.reversal.already"));
            }
            if (date.isBefore(original.date())) {
                throw ValidationException.of(FieldError.of(null, "transaction.reversal.beforeOriginal"));
            }
            FinancialTransaction reversal = post(TransactionRules.reversalOf(original, date, clock.instant(), user(),
                    reason));
            transactions.markReversed(original.id(), reversal.id());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reversal_id", reversal.id());
            details.put("date", date);
            details.put("amount", original.amount());
            audit.event(AUDIT_TYPE, original.id(), AuditAction.REVERSE, details, reason);
            return reversal;
        });
    }

    /**
     * Modification d'une transaction de versement du jour (seule exception à l'immuabilité, contrôlée par le
     * service Versements) : montant, compte, véhicule, chauffeur.
     */
    public FinancialTransaction amendCollection(FinancialTransaction before, FinancialTransaction after, String reason) {
        return tx.inTransaction(c -> {
            List<FieldError> errors = new ArrayList<>();
            FinancialAccount oldAccount = accounts.findById(before.accountId()).orElseThrow();
            FinancialAccount newAccount = accounts.findById(after.accountId())
                    .orElseThrow(() -> ValidationException.of(FieldError.of("accountId", "transaction.account.unknown")));
            checkAccount(newAccount, before.date(), false, false, "accountId", errors);
            checkAccount(oldAccount, before.date(), true, false, "accountId", errors);
            if (errors.isEmpty()) {
                if (oldAccount.id().equals(newAccount.id())) {
                    checkBalance(newAccount, before.date(), after.signedMinor() - before.signedMinor(), "amount", errors);
                } else {
                    checkBalance(oldAccount, before.date(), -before.signedMinor(), "amount", errors);
                    checkBalance(newAccount, before.date(), after.signedMinor(), "amount", errors);
                }
            }
            ValidationException.throwIfAny(errors);
            long version = transactions.update(after);
            audit.updated(AUDIT_TYPE, before.id(), transactions.snapshot(before), transactions.snapshot(after), reason);
            return after.withIdentity(after.id(), version);
        });
    }

    public Money balance(long accountId) {
        FinancialAccount account = accounts.findById(accountId).orElseThrow();
        return account.openingBalance().plus(Money.of(transactions.signedTotal(accountId, null), settings.currency()));
    }

    /** Solde en fin de journée. */
    public Money balanceAt(long accountId, LocalDate day) {
        FinancialAccount account = accounts.findById(accountId).orElseThrow();
        return account.openingBalance().plus(Money.of(transactions.signedTotal(accountId, day), settings.currency()));
    }

    /** Soldes actuels de tous les comptes. */
    public Map<Long, Money> balances() {
        Map<Long, Long> totals = transactions.signedTotalsByAccount();
        Map<Long, Money> result = new LinkedHashMap<>();
        for (FinancialAccount a : accounts.findAll()) {
            result.put(a.id(), a.openingBalance().plus(Money.of(totals.getOrDefault(a.id(), 0L), settings.currency())));
        }
        return result;
    }

    public LocalDate lockedThrough(long accountId) {
        return closings.lockedThrough(accountId);
    }

    /** L'écart d'une clôture est passé à la date qu'elle verrouille : seule écriture admise ce jour-là. */
    private boolean ownClosingDifference(FinancialTransaction t) {
        return t.source() == TransactionSource.CASH_CLOSING && !t.isReversal() && t.sourceId() != null
                && closings.findById(t.sourceId()).filter(c -> c.status() == ClosingStatus.CLOSED
                && c.accountId().equals(t.accountId()) && c.date().equals(t.date())).isPresent();
    }

    private void checkAccount(FinancialAccount account, LocalDate date, boolean existingMovement, boolean closingOwn,
                              String field, List<FieldError> errors) {
        if (!account.active() && !existingMovement) {
            errors.add(FieldError.of(field, "transaction.account.inactive", account.name()));
        }
        if (date.isBefore(account.openingDate())) {
            errors.add(FieldError.of("date", "transaction.beforeOpening", account.name(),
                    formats.date(account.openingDate())));
        }
        LocalDate locked = closings.lockedThrough(account.id());
        if (locked != null && !date.isAfter(locked) && !closingOwn) {
            errors.add(FieldError.of("date", "transaction.dayClosed", account.name(), formats.date(locked)));
        }
    }

    /**
     * Le solde du compte, à chaque fin de journée à partir de {@code from}, reste-t-il acceptable une fois le
     * mouvement {@code delta} ajouté ? (une sortie datée d'hier ne doit pas rendre négatif le solde d'aujourd'hui)
     */
    private void checkBalance(FinancialAccount account, LocalDate from, long delta, String field,
                              List<FieldError> errors) {
        if (delta >= 0 || account.overdraftAllowed()) {
            return;
        }
        long running = account.openingBalance().minor() + transactions.signedTotal(account.id(), from.minusDays(1));
        List<long[]> days = transactions.dailyTotalsFrom(account.id(), from);
        // fin de la journée « from » : solde de la veille s'il n'y a encore aucun mouvement ce jour-là
        long lowest = days.isEmpty() || days.get(0)[0] != from.toEpochDay() ? running : Long.MAX_VALUE;
        for (long[] day : days) {
            running += day[1];
            lowest = Math.min(lowest, running);
        }
        Money available = Money.of(lowest, settings.currency());
        if (!AccountRules.balanceAllowed(account, available.plus(Money.of(delta, settings.currency())))) {
            errors.add(FieldError.of(field, "account.insufficientBalance", account.name(),
                    formats.money(available.isNegative() ? Money.zero(settings.currency()) : available)));
        }
    }
}
