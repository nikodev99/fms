package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Intervention réalisée sur un véhicule (instructions §9).
 * <p>
 * {@code cost} est le coût de l'intervention (charge rattachée au véhicule). Le PAIEMENT n'est pas encore
 * enregistré ici : quand le module Transactions existera, il sera une sortie de trésorerie liée à cette
 * intervention. Une provision d'entretien n'est jamais confondue avec ce coût réel (§4.6).
 * <p>
 * {@code kind} est recopié depuis le type au moment de la saisie : modifier le type plus tard ne change pas
 * la nature des interventions passées.
 */
public record MaintenanceEvent(
        Long id,
        long version,
        Long vehicleId,
        Long typeId,
        MaintenanceKind kind,
        LocalDate date,
        Long mileage,
        String description,
        String supplier,
        Money cost,
        MaintenanceEventStatus status,
        String cancelReason,
        String notes) {

    public boolean isNew() {
        return id == null;
    }

    public boolean counts() {
        return status == MaintenanceEventStatus.RECORDED;
    }

    public MaintenanceEvent withIdentity(long newId, long newVersion) {
        return new MaintenanceEvent(newId, newVersion, vehicleId, typeId, kind, date, mileage, description, supplier,
                cost, status, cancelReason, notes);
    }

    public MaintenanceEvent withKind(MaintenanceKind newKind) {
        return new MaintenanceEvent(id, version, vehicleId, typeId, newKind, date, mileage, description, supplier,
                cost, status, cancelReason, notes);
    }

    MaintenanceEvent withText(String nDescription, String nSupplier, String nReason, String nNotes) {
        return new MaintenanceEvent(id, version, vehicleId, typeId, kind, date, mileage, nDescription, nSupplier,
                cost, status, nReason, nNotes);
    }

    /**
     * Nature d'une intervention.
     * <p>
     * Seules les RÉPARATIONS (pannes, casse, accident) alimentent le cumul comparé au seuil de réparations
     * du véhicule (instructions §4.1) ; l'ENTRETIEN courant (vidange, révision, pneus…) est un coût normal
     * d'exploitation et ne signale pas un véhicule à remplacer.
     */
    public enum MaintenanceKind {
        SERVICE,
        REPAIR
    }

    /**
     * État d'une intervention. Une intervention saisie par erreur est ANNULÉE (avec motif), jamais supprimée :
     * elle reste visible et n'est plus comptée dans les coûts ni dans le cumul des réparations.
     */
    public enum MaintenanceEventStatus {
        RECORDED,
        CANCELLED
    }
}
