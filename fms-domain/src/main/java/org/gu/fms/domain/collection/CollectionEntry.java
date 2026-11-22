package org.gu.fms.domain.collection;

import org.gu.fms.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Versement d'un chauffeur, ou avoir (instructions §4.3).
 *
 * Toujours attribué au chauffeur qui a versé ET au véhicule concerné, jamais déduit du chauffeur principal.
 * Chaque versement produit une entrée de trésorerie liée ({@code transactionId}) ; un avoir produit sa
 * contre-passation. Un versement d'un jour passé, pointé ou clôturé ne se modifie pas : on émet un avoir (daté du
 * jour, il référence le versement d'origine) puis, si besoin, un nouveau versement.
 *
 * @param amount   montant toujours positif ; un avoir diminue les sommes reçues
 * @param originId pour un avoir : le versement annulé
 * @param reason   motif de l'avoir ou de la dernière modification du jour
 */
public record CollectionEntry(
        Long id,
        long version,
        LocalDate date,
        Instant recordedAt,
        Long driverId,
        Long vehicleId,
        Long accountId,
        Money amount,
        CollectionKind kind,
        CollectionStatus status,
        Long originId,
        Long transactionId,
        String cashier,
        String reason,
        String notes) {

    public CollectionEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean isCreditNote() {
        return kind == CollectionKind.CREDIT_NOTE;
    }

    /** Effet sur les sommes reçues du chauffeur, en unités mineures. */
    public long signedMinor() {
        return isCreditNote() ? -amount.minor() : amount.minor();
    }

    public CollectionEntry withIdentity(long newId, long newVersion) {
        return new CollectionEntry(newId, newVersion, date, recordedAt, driverId, vehicleId, accountId, amount, kind, status,
                originId, transactionId, cashier, reason, notes);
    }

    public CollectionEntry withTransaction(long txId) {
        return new CollectionEntry(id, version, date, recordedAt, driverId, vehicleId, accountId, amount, kind, status,
                originId, txId, cashier, reason, notes);
    }

    public CollectionEntry withRecording(Instant at, String user) {
        return new CollectionEntry(id, version, date, at, driverId, vehicleId, accountId, amount, kind, status,
                originId, transactionId, user, reason, notes);
    }

    public CollectionEntry credited() {
        return new CollectionEntry(id, version, date, recordedAt, driverId, vehicleId, accountId, amount, kind,
                CollectionStatus.CREDITED, originId, transactionId, cashier, reason, notes);
    }

    CollectionEntry withText(String nReason, String nNotes) {
        return new CollectionEntry(id, version, date, recordedAt, driverId, vehicleId, accountId, amount, kind, status,
                originId, transactionId, cashier, nReason, nNotes);
    }
}
