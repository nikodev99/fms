package org.gu.fms.domain.finance;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Virement interne entre deux comptes (instructions §4.5) : une sortie du compte source et une entrée du compte
 * destination, de nature TRANSFER, donc sans effet sur la trésorerie consolidée ni sur le résultat. Les frais
 * éventuels (banque, Mobile Money) sont une vraie dépense, enregistrée à part sur le compte source.
 */
public record AccountTransfer(
        Long id,
        long version,
        LocalDate date,
        Long fromAccountId,
        Long toAccountId,
        Money amount,
        Money fee,
        String label,
        String reference,
        Long outTransactionId,
        Long inTransactionId,
        Long feeTransactionId,
        TransferStatus status,
        String reversalReason,
        String userName) {

    public boolean isNew() {
        return id == null;
    }

    public boolean hasFee() {
        return fee != null && fee.isPositive();
    }

    public AccountTransfer withIdentity(long newId, long newVersion) {
        return new AccountTransfer(newId, newVersion, date, fromAccountId, toAccountId, amount, fee, label, reference,
                outTransactionId, inTransactionId, feeTransactionId, status, reversalReason, userName);
    }

    public AccountTransfer withTransactions(long out, long in, Long feeTx) {
        return new AccountTransfer(id, version, date, fromAccountId, toAccountId, amount, fee, label, reference,
                out, in, feeTx, status, reversalReason, userName);
    }

    public AccountTransfer reversed(String reason) {
        return new AccountTransfer(id, version, date, fromAccountId, toAccountId, amount, fee, label, reference,
                outTransactionId, inTransactionId, feeTransactionId, TransferStatus.REVERSED, reason, userName);
    }
}
