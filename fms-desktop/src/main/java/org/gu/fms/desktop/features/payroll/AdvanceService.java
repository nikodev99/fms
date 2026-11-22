package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.collection.DriverLedgerRepository;
import org.gu.fms.desktop.features.collection.DriverLedgerService;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.features.finance.ReversalHandler;
import org.gu.fms.desktop.features.finance.TransactionRepository;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.domain.collection.DriverLedgerEntry;
import org.gu.fms.domain.collection.LedgerRubric;
import org.gu.fms.domain.collection.LedgerSource;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.SystemCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionSource;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Avances et acomptes sur salaire : sortie de trésorerie (nature « compte chauffeur », hors résultat) et
 * créance au compte du chauffeur, retenue ensuite en paie. Remboursement en espèces possible.
 * <p>
 * Pour ces écritures, l'objet d'origine est le compte du chauffeur : {@code sourceId} = chauffeur ; le mouvement
 * du compte chauffeur référence la transaction.
 */
@Singleton
public final class AdvanceService implements ReversalHandler {

    private final Ledger ledger;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final DriverRepository drivers;
    private final DriverLedgerService driverLedger;
    private final DriverLedgerRepository ledgerEntries;
    private final TransactionManager tx;
    private final Formats formats;

    @Inject
    public AdvanceService(Ledger ledger, TransactionRepository transactions, CategoryRepository categories,
                          DriverRepository drivers, DriverLedgerService driverLedger,
                          DriverLedgerRepository ledgerEntries, TransactionManager tx, Formats formats) {
        this.ledger = ledger;
        this.transactions = transactions;
        this.categories = categories;
        this.drivers = drivers;
        this.driverLedger = driverLedger;
        this.ledgerEntries = ledgerEntries;
        this.tx = tx;
        this.formats = formats;
    }

    /** Avances et remboursements, contre-passations comprises (plus récents d'abord). */
    public List<FinancialTransaction> movements() {
        return transactions.findByCategories(List.of(categories.system(SystemCategory.SALARY_ADVANCE).id(),
                categories.system(SystemCategory.ADVANCE_REPAYMENT).id()));
    }

    public FinancialTransaction grant(Long driverId, Long accountId, LocalDate date, Money amount, String label) {
        return tx.inTransaction(c -> {
            Driver d = checkDriver(driverId, true);
            FinancialTransaction posted = ledger.post(movement(TransactionDirection.OUTFLOW,
                    SystemCategory.SALARY_ADVANCE, d, accountId, date, amount, label, "Avance sur salaire — "));
            driverLedger.post(d.id(), date, LedgerRubric.ADVANCE, amount.minor(), LedgerSource.ADVANCE, posted.id(),
                    posted.label());
            return posted;
        });
    }

    public FinancialTransaction repay(Long driverId, Long accountId, LocalDate date, Money amount, String label) {
        return tx.inTransaction(c -> {
            Driver d = checkDriver(driverId, false);
            Money due = driverLedger.balance(d.id()).advanceDue();
            if (amount != null && amount.compareTo(due) > 0) {
                throw ValidationException.of(FieldError.of("amount", "advance.repayAboveDue", formats.money(due)));
            }
            FinancialTransaction posted = ledger.post(movement(TransactionDirection.INFLOW,
                    SystemCategory.ADVANCE_REPAYMENT, d, accountId, date, amount, label, "Remboursement d'avance — "));
            driverLedger.post(d.id(), date, LedgerRubric.ADVANCE, -amount.minor(), LedgerSource.ADVANCE_REPAYMENT,
                    posted.id(), posted.label());
            return posted;
        });
    }

    // ---- Contre-passation depuis l'écran Transactions -------------------------------------------------

    @Override
    public Set<TransactionSource> sources() {
        return Set.of(TransactionSource.ADVANCE);
    }

    /** L'avance (ou le remboursement) annulée quitte aussi le compte du chauffeur. */
    @Override
    public FinancialTransaction reverse(FinancialTransaction original, String reason) {
        FinancialTransaction reversal = ledger.reverse(original.id(), reason, ledger.today());
        List<DriverLedgerEntry> linked = new ArrayList<>(ledgerEntries.findBySource(LedgerSource.ADVANCE, original.id()));
        linked.addAll(ledgerEntries.findBySource(LedgerSource.ADVANCE_REPAYMENT, original.id()));
        for (DriverLedgerEntry e : linked) {
            driverLedger.post(e.driverId(), ledger.today(), e.rubric(), -e.amount().minor(), e.source(), original.id(),
                    "Annulation : " + reason);
        }
        return reversal;
    }

    private Driver checkDriver(Long driverId, boolean mustBeActive) {
        if (driverId == null) {
            throw ValidationException.of(FieldError.of("driverId", "validation.required"));
        }
        Driver d = drivers.findById(driverId)
                .orElseThrow(() -> ValidationException.of(FieldError.of("driverId", "collection.driver.unknown")));
        if (mustBeActive && !d.status().canBeAssigned()) {
            throw ValidationException.of(FieldError.of("driverId", "advance.driverNotActive", d.displayName()));
        }
        return d;
    }

    private FinancialTransaction movement(TransactionDirection direction, SystemCategory category, Driver d,
                                          Long accountId, LocalDate date, Money amount, String label, String prefix) {
        String text = TextRules.normalizeSpaces(label);
        return new FinancialTransaction(null, 0, date, null, direction, categories.system(category).id(), null,
                accountId, amount, null, d.id(), text == null ? prefix + d.displayName() : text, null,
                TransactionSource.ADVANCE, d.id(), null, null, null);
    }
}
