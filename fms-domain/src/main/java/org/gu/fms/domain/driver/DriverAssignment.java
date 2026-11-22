package org.gu.fms.domain.driver;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Affectation d'un chauffeur à un véhicule sur une période (instructions §4.2).
 * <p>
 * Historisée : une affectation n'est jamais supprimée, on la termine ({@code endDate}). C'est elle qui
 * permettra d'attribuer chaque versement au chauffeur et au véhicule réellement concernés.
 * Le chauffeur, le véhicule et le rôle sont figés après création ; pour changer, on termine et on recrée.
 *
 * @param endDate dernier jour inclus ; null = affectation en cours
 */
public record DriverAssignment(
        Long id,
        long version,
        Long driverId,
        Long vehicleId,
        AssignmentRole role,
        LocalDate startDate,
        LocalDate endDate,
        Money expectedDaily,
        String notes) {

    public boolean isNew() {
        return id == null;
    }

    /** En cours à cette date ? */
    public boolean isActiveOn(LocalDate day) {
        return startDate != null && !day.isBefore(startDate) && (endDate == null || !day.isAfter(endDate));
    }

    /** Montant attendu pour un jour d'activité (zéro si aucun). */
    public long expectedMinor() {
        return expectedDaily == null ? 0 : expectedDaily.minor();
    }

    public DriverAssignment withIdentity(long newId, long newVersion) {
        return new DriverAssignment(newId, newVersion, driverId, vehicleId, role, startDate, endDate, expectedDaily,
                notes);
    }

    public DriverAssignment withNotes(String newNotes) {
        return new DriverAssignment(id, version, driverId, vehicleId, role, startDate, endDate, expectedDaily,
                newNotes);
    }

    /**
     * Rôle d'un chauffeur sur un véhicule (instructions §4.2).
     * Un seul chauffeur principal actif par véhicule à un moment donné ; remplaçants et chauffeurs de nuit en plus.
     */
    public enum AssignmentRole {
        PRIMARY,
        REPLACEMENT,
        NIGHT
    }
}
