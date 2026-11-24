package org.gu.fms.desktop.features.loan;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.finance.ReversalHandler;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionSource;

import java.util.Set;

/**
 * Contre-passer une sortie d'échéance contre-passe l'échéance entière (capital, intérêts, assurance). Le déblocage
 * (origine LOAN_DISBURSEMENT) ne se contre-passe pas seul : on annule l'emprunt (refus dans ReversalService).
 */
@Singleton
public final class LoanReversalHandler implements ReversalHandler {

    private final LoanService loans;

    @Inject
    public LoanReversalHandler(LoanService loans) {
        this.loans = loans;
    }

    @Override
    public Set<TransactionSource> sources() {
        return Set.of(TransactionSource.LOAN);
    }

    @Override
    public FinancialTransaction reverse(FinancialTransaction original, String reason) {
        return loans.reversePayment(original.sourceId(), reason, original.id());
    }
}
