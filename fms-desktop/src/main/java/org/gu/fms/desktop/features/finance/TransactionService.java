package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionCategory;
import org.gu.fms.domain.finance.TransactionSource;

import java.time.LocalDate;
import java.util.List;

/**
 * Saisie directe de transactions (écran Transactions). Création seulement : une transaction enregistrée ne se
 * modifie jamais ; elle se contre-passe ({@link ReversalService}).
 */
@Singleton
public final class TransactionService implements CrudService<FinancialTransaction> {

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final Ledger ledger;

    @Inject
    public TransactionService(TransactionRepository transactions, CategoryRepository categories, Ledger ledger) {
        this.transactions = transactions;
        this.categories = categories;
        this.ledger = ledger;
    }

    @Override
    public List<FinancialTransaction> findAll() {
        return transactions.findAll();
    }

    public List<FinancialTransaction> findBetween(LocalDate from, LocalDate to) {
        return transactions.findBetween(from, to);
    }

    @Override
    public FinancialTransaction save(FinancialTransaction t) {
        if (!t.isNew()) {
            throw ValidationException.of(FieldError.of(null, "transaction.immutable"));
        }
        if (t.source() != TransactionSource.MANUAL || t.isReversal()) {
            throw ValidationException.of(FieldError.of(null, "transaction.manualOnly"));
        }
        TransactionCategory category = t.categoryId() == null ? null : categories.findById(t.categoryId()).orElse(null);
        if (category != null && !category.directEntry()) {
            throw ValidationException.of(FieldError.of("categoryId", "transaction.category.notDirect", category.name()));
        }
        return ledger.post(t);
    }
}
