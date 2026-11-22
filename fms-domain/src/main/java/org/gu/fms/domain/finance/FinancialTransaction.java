package org.gu.fms.domain.finance;

import org.gu.fms.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Mouvement de trésorerie du grand livre unique (instructions §4.4, §4.5).
 * <p>
 * Immuable une fois enregistré : une transaction non exécutée est ANNULÉE par une contre-passation (même montant,
 * sens opposé, datée du jour) qui la référence ; les deux restent visibles et se compensent dans les soldes.
 * Seule exception, gérée par le module Versements : le versement du jour, avant point journalier et clôture.
 *
 * @param amount       montant toujours positif ; le sens donne le signe
 * @param nature       recopiée de la catégorie à l'enregistrement (les rapports ne dépendent pas d'un changement
 *                     ultérieur de la catégorie)
 * @param sourceId     identifiant dans le module d'origine (versement, virement, bulletin…) ; null pour MANUAL
 * @param reversalOfId pour une contre-passation : la transaction annulée
 * @param reversedById pour une transaction annulée : sa contre-passation
 */
public record FinancialTransaction(
        Long id,
        long version,
        LocalDate date,
        Instant recordedAt,
        TransactionDirection direction,
        Long categoryId,
        TransactionNature nature,
        Long accountId,
        Money amount,
        Long vehicleId,
        Long driverId,
        String label,
        String reference,
        TransactionSource source,
        Long sourceId,
        Long reversalOfId,
        Long reversedById,
        String userName) {

    public FinancialTransaction {
        Objects.requireNonNull(source, "source");
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean isReversal() {
        return reversalOfId != null;
    }

    public boolean isReversed() {
        return reversedById != null;
    }

    /** Effet sur le solde du compte, en unités mineures (+ entrée, − sortie). */
    public long signedMinor() {
        return direction.sign() * amount.minor();
    }

    public FinancialTransaction withIdentity(long newId, long newVersion) {
        return new FinancialTransaction(newId, newVersion, date, recordedAt, direction, categoryId, nature, accountId,
                amount, vehicleId, driverId, label, reference, source, sourceId, reversalOfId, reversedById, userName);
    }

    public FinancialTransaction withNature(TransactionNature newNature) {
        return new FinancialTransaction(id, version, date, recordedAt, direction, categoryId, newNature, accountId,
                amount, vehicleId, driverId, label, reference, source, sourceId, reversalOfId, reversedById, userName);
    }

    public FinancialTransaction withRecording(Instant at, String user) {
        return new FinancialTransaction(id, version, date, at, direction, categoryId, nature, accountId,
                amount, vehicleId, driverId, label, reference, source, sourceId, reversalOfId, reversedById, user);
    }

    FinancialTransaction withText(String nLabel, String nReference) {
        return new FinancialTransaction(id, version, date, recordedAt, direction, categoryId, nature, accountId,
                amount, vehicleId, driverId, nLabel, nReference, source, sourceId, reversalOfId, reversedById, userName);
    }
}
