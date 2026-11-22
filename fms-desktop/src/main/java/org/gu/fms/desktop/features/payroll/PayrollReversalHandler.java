package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.finance.ReversalHandler;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionSource;

import java.util.Set;

/** Contre-passer un paiement de salaire remet le bulletin à l'état « validé ». */
@Singleton
public final class PayrollReversalHandler implements ReversalHandler {

    private final PayrollService payroll;

    @Inject
    public PayrollReversalHandler(PayrollService payroll) {
        this.payroll = payroll;
    }

    @Override
    public Set<TransactionSource> sources() {
        return Set.of(TransactionSource.PAYROLL);
    }

    @Override
    public FinancialTransaction reverse(FinancialTransaction original, String reason) {
        return payroll.reversePayment(original.sourceId(), reason);
    }
}
