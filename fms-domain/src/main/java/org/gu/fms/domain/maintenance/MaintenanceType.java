package org.gu.fms.domain.maintenance;

/**
 * Type d'intervention défini par l'utilisateur (Paramètres) : vidange, freins, réparation moteur…
 *
 * @param defaultIntervalDays périodicité proposée par défaut à la création d'un plan (facultative)
 * @param defaultIntervalKm   périodicité kilométrique proposée par défaut (facultative)
 */
public record MaintenanceType(
        Long id,
        long version,
        String name,
        MaintenanceEvent.MaintenanceKind kind,
        Long defaultIntervalDays,
        Long defaultIntervalKm,
        boolean active) {

    public boolean isNew() {
        return id == null;
    }

    public MaintenanceType withIdentity(long newId, long newVersion) {
        return new MaintenanceType(newId, newVersion, name, kind, defaultIntervalDays, defaultIntervalKm, active);
    }

    MaintenanceType withName(String newName) {
        return new MaintenanceType(id, version, newName, kind, defaultIntervalDays, defaultIntervalKm, active);
    }
}