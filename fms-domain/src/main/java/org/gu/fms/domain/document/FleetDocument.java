package org.gu.fms.domain.document;

import java.time.LocalDate;

/**
 * Document d'un véhicule (assurance, vignette, contrôle technique, licence…) ou d'un chauffeur (permis, certificat
 * médical…) — instructions §8.
 * <p>
 * Un renouvellement crée un nouveau document qui référence celui qu'il remplace ({@code replacesId}) ; l'ancien est
 * marqué {@code superseded} et reste dans l'historique. Le document « en cours » d'un titulaire pour un type est celui
 * qui n'est pas remplacé.
 *
 * @param expiresOn date d'expiration ; null pour un document sans échéance
 */
public record FleetDocument(
        Long id,
        long version,
        Long typeId,
        DocumentType.DocumentSubject subject,
        Long vehicleId,
        Long driverId,
        String number,
        String issuer,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Long replacesId,
        boolean superseded,
        String notes) {

    public boolean isNew() {
        return id == null;
    }

    public boolean isCurrent() {
        return !superseded;
    }

    /** Identifiant du titulaire (véhicule ou chauffeur selon le sujet). */
    public Long ownerId() {
        return subject == DocumentType.DocumentSubject.VEHICLE ? vehicleId : driverId;
    }

    public FleetDocument withIdentity(long newId, long newVersion) {
        return new FleetDocument(newId, newVersion, typeId, subject, vehicleId, driverId, number, issuer, issuedOn,
                expiresOn, replacesId, superseded, notes);
    }

    /** Renouvellement de {@code previous}. */
    public FleetDocument replacing(Long previousId) {
        return new FleetDocument(id, version, typeId, subject, vehicleId, driverId, number, issuer, issuedOn,
                expiresOn, previousId, false, notes);
    }

    /** Même document rattaché à un titulaire (création d'un véhicule ou d'un chauffeur avec ses documents). */
    public FleetDocument forOwner(Long ownerId) {
        return subject == DocumentType.DocumentSubject.VEHICLE
                ? new FleetDocument(id, version, typeId, subject, ownerId, null, number, issuer, issuedOn, expiresOn,
                replacesId, superseded, notes)
                : new FleetDocument(id, version, typeId, subject, null, ownerId, number, issuer, issuedOn, expiresOn,
                replacesId, superseded, notes);
    }
}
