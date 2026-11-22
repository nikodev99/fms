package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionSource;

import java.util.Objects;
import java.util.Set;

/** Contre-passer une écriture de virement annule tout le virement (entrée, sortie et frais). */
@Singleton
public final class TransferReversalHandler implements ReversalHandler {

    private final TransferService transfers;
    private final TransactionRepository transactions;

    @Inject
    public TransferReversalHandler(TransferService transfers, TransactionRepository transactions) {
        this.transfers = transfers;
        this.transactions = transactions;
    }

    @Override
    public Set<TransactionSource> sources() {
        return Set.of(TransactionSource.TRANSFER);
    }

    @Override
    public FinancialTransaction reverse(FinancialTransaction original, String reason) {
        transfers.reverse(original.sourceId(), reason);
        FinancialTransaction reloaded = transactions.findById(original.id()).orElseThrow();
        return transactions.findById(Objects.requireNonNull(reloaded.reversedById())).orElseThrow();
    }
}
