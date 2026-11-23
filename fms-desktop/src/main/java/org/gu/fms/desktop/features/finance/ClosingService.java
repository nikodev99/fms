package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.collection.DailyPointService;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
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
 * Clôture de caisse par compte et par journée (instructions §4.5), identique à l'édition Excel :
 * <ul>
 *   <li>exige le point journalier de la journée (s'il reste des chauffeurs à pointer, la clôture est refusée) ;</li>
 *   <li>solde théorique = solde de la veille + entrées − sorties ± virements ; écart = compté − théorique ;</li>
 *   <li>un écart est expliqué et passé en « Excédent / Manquant de caisse » à la date clôturée ;</li>
 *   <li>la clôture verrouille le compte jusqu'à sa date ; seule la dernière se rouvre (motif), son écart est alors
 *       contre-passé à la même date.</li>
 * </ul>
 */
@Singleton
public final class ClosingService {

    static final String AUDIT_TYPE = "cash_closing";

    private final ClosingRepository closings;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final Ledger ledger;
    private final DailyPointService dailyPoints;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;
    private final Formats formats;

    @Inject
    public ClosingService(ClosingRepository closings, AccountRepository accounts, TransactionRepository transactions,
                          CategoryRepository categories, Ledger ledger, DailyPointService dailyPoints,
                          TransactionManager tx, AuditLog audit, Clock clock, Formats formats) {
        this.closings = closings;
        this.accounts = accounts;
        this.transactions = transactions;
        this.categories = categories;
        this.ledger = ledger;
        this.dailyPoints = dailyPoints;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.formats = formats;
    }

    /** Une ligne par compte actif (ou clôturé ce jour-là) pour la journée. */
    public List<ClosingRow> rows(LocalDate day) {
        Map<Long, CashClosing> closedThatDay = new LinkedHashMap<>();
        for (CashClosing c : closings.findForDate(day)) {
            if (c.status() == ClosingStatus.CLOSED) {
                closedThatDay.put(c.accountId(), c);
            }
        }
        List<ClosingRow> rows = new ArrayList<>();
        for (FinancialAccount a : accounts.findAll()) {
            CashClosing closing = closedThatDay.get(a.id());
            if (!a.active() && closing == null) {
                continue;
            }
            LocalDate locked = closings.lockedThrough(a.id());
            boolean lockedAfter = closing == null && locked != null && !day.isAfter(locked);
            boolean latest = closing != null && day.equals(locked);
            ClosingFigures figures = day.isBefore(a.openingDate()) ? null
                    : transactions.figures(a.id(), day, a.openingBalance());
            rows.add(new ClosingRow(a, day, figures, closing, lockedAfter, latest, null, null));
        }
        return rows;
    }

    public CashClosing close(long accountId, LocalDate day, Money actual, String explanationInput) {
        return tx.inTransaction(c -> {
            FinancialAccount account = accounts.findById(accountId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "transaction.account.unknown")));
            String explanation = TextRules.normalize(explanationInput);
            ClosingFigures figures = day == null || day.isBefore(account.openingDate()) ? null
                    : transactions.figures(accountId, day, account.openingBalance());
            List<FieldError> errors = new ArrayList<>(ClosingRules.validate(day, ledger.today(), account, figures,
                    actual, explanation));
            ValidationException.throwIfAny(errors);
            LocalDate locked = closings.lockedThrough(accountId);
            if (locked != null && !day.isAfter(locked)) {
                throw ValidationException.of(FieldError.of("date", "closing.alreadyLocked", formats.date(locked)));
            }
            long pending = dailyPoints.pendingCount(day);
            if (pending > 0) {
                throw ValidationException.of(FieldError.of(null, "closing.dailyPointPending", pending));
            }
            Money difference = actual.minus(figures.expected());
            CashClosing draft = new CashClosing(null, 0, accountId, day, figures.opening(), figures.inflows(),
                    figures.outflows(), figures.transfersIn(), figures.transfersOut(), figures.expected(), actual,
                    difference, explanation, null, ledger.user(), clock.instant(), ClosingStatus.CLOSED, null, null);
            long id = closings.insert(draft);
            CashClosing saved = draft.withIdentity(id, 0);
            if (!difference.isZero()) {
                boolean surplus = difference.isPositive();
                FinancialTransaction posted = ledger.post(new FinancialTransaction(null, 0, day, null,
                        surplus ? TransactionDirection.INFLOW : TransactionDirection.OUTFLOW,
                        categories.system(surplus ? SystemCategory.CASH_SURPLUS : SystemCategory.CASH_SHORTAGE).id(),
                        null, accountId, surplus ? difference : difference.negate(), null, null,
                        "Écart de clôture — " + explanation, null, TransactionSource.CASH_CLOSING, id, null, null, null));
                closings.linkDifference(id, posted.id());
                saved = closings.findById(id).orElseThrow();
            }
            audit.event(AUDIT_TYPE, id, AuditAction.CLOSE, closings.snapshot(saved), explanation);
            return saved;
        });
    }

    /** Rouvre la DERNIÈRE clôture d'un compte ; son écart éventuel est contre-passé à la date clôturée. */
    public CashClosing reopen(long closingId, String reason) {
        return tx.inTransaction(c -> {
            CashClosing closing = closings.findById(closingId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "closing.unknown")));
            String text = TextRules.normalize(reason);
            if (text == null) {
                throw ValidationException.of(FieldError.of("reason", "closing.reopenReasonRequired"));
            }
            if (closing.status() != ClosingStatus.CLOSED) {
                throw ValidationException.of(FieldError.of(null, "closing.notClosed"));
            }
            if (!closing.date().equals(closings.lockedThrough(closing.accountId()))) {
                throw ValidationException.of(FieldError.of(null, "closing.notLatest"));
            }
            CashClosing reopened = closing.reopened(text, clock.instant());
            long version = closings.update(reopened);
            if (closing.differenceTransactionId() != null) {
                ledger.reverse(closing.differenceTransactionId(), text, closing.date());
            }
            audit.event(AUDIT_TYPE, closing.id(), AuditAction.REOPEN, Map.of("date", closing.date()), text);
            return reopened.withIdentity(closing.id(), version);
        });
    }
}
