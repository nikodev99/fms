package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.features.finance.TransactionRepository;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.SystemCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionSource;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.Map;

/**
 * Paiement d'une intervention (décision ADR-002 : coût sur l'intervention, paiement lié plus tard). Le paiement est
 * une sortie « Entretien et réparations » liée à l'intervention ; paiements partiels possibles, jamais au-delà du
 * coût. Un paiement erroné se contre-passe depuis l'écran Transactions.
 */
@Singleton
public final class MaintenancePaymentService {

    private final MaintenanceEventRepository events;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final Ledger ledger;
    private final TransactionManager tx;
    private final Formats formats;
    private final AppSettings settings;

    @Inject
    public MaintenancePaymentService(MaintenanceEventRepository events, TransactionRepository transactions,
                                     CategoryRepository categories, Ledger ledger, TransactionManager tx,
                                     Formats formats, AppSettings settings) {
        this.events = events;
        this.transactions = transactions;
        this.categories = categories;
        this.ledger = ledger;
        this.tx = tx;
        this.formats = formats;
        this.settings = settings;
    }

    /** Montant déjà payé par intervention. */
    public Map<Long, Long> paidByEvent() {
        return transactions.paidBySource(TransactionSource.MAINTENANCE);
    }

    public Money remaining(MaintenanceEvent e) {
        return e.cost().minus(Money.of(transactions.paidFor(TransactionSource.MAINTENANCE, e.id()), settings.currency()));
    }

    public FinancialTransaction pay(long eventId, Long accountId, LocalDate date, Money amount, String reference) {
        return tx.inTransaction(c -> {
            MaintenanceEvent e = events.findById(eventId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "maintenance.event.unknown")));
            if (!e.counts()) {
                throw ValidationException.of(FieldError.of(null, "maintenance.payment.cancelledEvent"));
            }
            Money remaining = remaining(e);
            if (amount != null && amount.compareTo(remaining) > 0) {
                throw ValidationException.of(FieldError.of("amount", "maintenance.payment.aboveRemaining",
                        formats.money(remaining)));
            }
            String label = "Intervention du " + formats.date(e.date())
                    + (e.supplier() == null ? "" : " — " + e.supplier());
            return ledger.post(new FinancialTransaction(null, 0, date, null, TransactionDirection.OUTFLOW,
                    categories.system(SystemCategory.MAINTENANCE).id(), null, accountId, amount, e.vehicleId(), null,
                    label, TextRules.normalizeSpaces(reference), TransactionSource.MAINTENANCE, e.id(), null, null, null));
        });
    }
}
