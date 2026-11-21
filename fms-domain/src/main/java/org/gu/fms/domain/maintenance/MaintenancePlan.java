package org.gu.fms.domain.maintenance;

import java.time.LocalDate;

/**
 * Plan d'entretien périodique d'un véhicule (instructions §9) : par date, par kilométrage, ou les deux.
 *
 * {@code lastDoneDate} et {@code lastDoneKm} sont la RÉFÉRENCE saisie (ex. dernier entretien connu avant FMS).
 * La dernière réalisation effective et l'échéance sont calculées avec les interventions
 * (voir {@link MaintenancePlanRules#withHistory}) ; rien n'est recopié dans le plan.
 */
public record MaintenancePlan(
        Long id,
        long version,
        Long vehicleId,
        Long typeId,
        Long intervalDays,
        Long intervalKm,
        LocalDate lastDoneDate,
        Long lastDoneKm,
        boolean active,
        String notes) {

    public boolean isNew() {
        return id == null;
    }

    public LocalDate nextDueDate() {
        return intervalDays == null || lastDoneDate == null ? null : lastDoneDate.plusDays(intervalDays);
    }

    public Long nextDueKm() {
        return intervalKm == null || lastDoneKm == null ? null : lastDoneKm + intervalKm;
    }

    public MaintenancePlan withIdentity(long newId, long newVersion) {
        return new MaintenancePlan(newId, newVersion, vehicleId, typeId, intervalDays, intervalKm, lastDoneDate,
                lastDoneKm, active, notes);
    }

    public MaintenancePlan withLastDone(LocalDate date, Long km) {
        return new MaintenancePlan(id, version, vehicleId, typeId, intervalDays, intervalKm, date, km, active, notes);
    }

    MaintenancePlan withNotes(String newNotes) {
        return new MaintenancePlan(id, version, vehicleId, typeId, intervalDays, intervalKm, lastDoneDate,
                lastDoneKm, active, newNotes);
    }
}
