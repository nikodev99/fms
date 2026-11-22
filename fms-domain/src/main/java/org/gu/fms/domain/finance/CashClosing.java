package org.gu.fms.domain.finance;

import org.gu.fms.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Clôture d'un compte pour une journée (instructions §4.5).
 *
 * Une clôture VERROUILLE le compte jusqu'à sa date incluse : plus aucune transaction ne peut y être datée
 * (sinon le solde d'ouverture des clôtures suivantes deviendrait faux). L'écart entre le solde compté et le solde
 * théorique est passé en transaction « Excédent / Manquant de caisse », datée du jour clôturé.
 * Seule la dernière clôture d'un compte peut être rouverte, avec un motif ; son écart est alors contre-passé.
 */
public record CashClosing(
        Long id,
        long version,
        Long accountId,
        LocalDate date,
        Money openingBalance,
        Money inflows,
        Money outflows,
        Money transfersIn,
        Money transfersOut,
        Money expected,
        Money actual,
        Money difference,
        String explanation,
        Long differenceTransactionId,
        String cashier,
        Instant closedAt,
        ClosingStatus status,
        String reopenReason,
        Instant reopenedAt) {

    public boolean isNew() {
        return id == null;
    }

    public CashClosing withIdentity(long newId, long newVersion) {
        return new CashClosing(newId, newVersion, accountId, date, openingBalance, inflows, outflows, transfersIn,
                transfersOut, expected, actual, difference, explanation, differenceTransactionId, cashier, closedAt,
                status, reopenReason, reopenedAt);
    }

    public CashClosing reopened(String reason, Instant at) {
        return new CashClosing(id, version, accountId, date, openingBalance, inflows, outflows, transfersIn,
                transfersOut, expected, actual, difference, explanation, differenceTransactionId, cashier, closedAt,
                ClosingStatus.REOPENED, reason, at);
    }
}
