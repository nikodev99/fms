package org.gu.fms.domain.loan;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Échéance d'un emprunt : capital + intérêts + assurance. Une échéance payée produit jusqu'à trois transactions
 * (capital : financement ; intérêts et assurance : charges financières), toutes contre-passables ensemble.
 */
public record LoanInstalment(
        Long id,
        long version,
        Long loanId,
        int seq,
        LocalDate dueDate,
        Money principal,
        Money interest,
        Money insurance,
        Money total,
        Money balanceAfter,
        InstalmentStatus status,
        LocalDate paidOn,
        Long accountId,
        Long principalTxId,
        Long interestTxId,
        Long insuranceTxId) {

    public boolean isPaid() {
        return status == InstalmentStatus.PAID;
    }

    public boolean isOverdue(LocalDate today) {
        return status == InstalmentStatus.DUE && dueDate.isBefore(today);
    }

    public LoanInstalment withIdentity(long newId, long newVersion) {
        return new LoanInstalment(newId, newVersion, loanId, seq, dueDate, principal, interest, insurance, total,
                balanceAfter, status, paidOn, accountId, principalTxId, interestTxId, insuranceTxId);
    }

    public LoanInstalment paid(LocalDate date, Long account, Long principalTx, Long interestTx, Long insuranceTx) {
        return new LoanInstalment(id, version, loanId, seq, dueDate, principal, interest, insurance, total,
                balanceAfter, InstalmentStatus.PAID, date, account, principalTx, interestTx, insuranceTx);
    }

    public LoanInstalment unpaid() {
        return new LoanInstalment(id, version, loanId, seq, dueDate, principal, interest, insurance, total,
                balanceAfter, InstalmentStatus.DUE, null, null, null, null, null);
    }
}
