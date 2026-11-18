package org.gu.fms.domain.vehicle;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Véhicule de la flotte (instructions §4.1).
 * <p>
 * Immuable. {@code id} est nul tant que le véhicule n'est pas enregistré ;
 * {@code version} sert au verrouillage optimiste (deux modifications concurrentes ne s'écrasent pas).
 * Un véhicule n'est jamais supprimé : il passe au statut INACTIVE ou SOLD et reste consultable.
 * <p>
 * Champs encore à venir (autres modules) : chauffeur affecté, documents, historique d'entretien,
 * coût cumulé des réparations, valeur de revente estimée, détail du financement.
 */
public record Vehicle(
        Long id,
        long version,
        String registration,
        String make,
        String model,
        VehicleCategory category,
        VehicleStatus status,
        LocalDate acquisitionDate,
        AcquisitionMethod acquisitionMethod,
        Money purchasePrice,
        Long currentMileage,
        Money repairThreshold,
        String notes
) {
    public Vehicle {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
    }

    public boolean isNew() {
        return id == null;
    }

    public String displayName() {
        return registration + " - " + make + " " + model;
    }

    public Vehicle withStatus(VehicleStatus status) {
        return new Vehicle(
                id, version, registration, make, model, category, status, acquisitionDate, acquisitionMethod, purchasePrice,
                currentMileage, repairThreshold, notes
        );
    }

    /** Copie avec l'identité attribuée par la base après enregistrement. */
    public Vehicle withIdentity(long newId, long newVersion) {
        return new Vehicle(
                newId, newVersion, registration, make, model, category, status, acquisitionDate, acquisitionMethod, purchasePrice,
                currentMileage, repairThreshold, notes
        );
    }

    /** Copie avec les champs texte normalisés (voir {@link VehicleRules#normalize(Vehicle)}). */
    Vehicle withText(String newRegistration, String newMake, String newModel, String newNotes) {
        return new Vehicle(id, version, newRegistration, newMake, newModel, category, status, acquisitionDate,
                acquisitionMethod, purchasePrice, currentMileage, repairThreshold, newNotes);
    }


    /** Catégories commerciales (instructions §1 et §4.1). Les libellés sont traduits côté interface. */
    public enum VehicleCategory {
        TAXI,
        PRIVATE_HIRE,
        RENTAL,
        DELIVERY,
        COMPANY,
        OTHER
    }

    /** Statuts d'un véhicule (instructions §4.1). */
    public enum VehicleStatus {
        ACTIVE,
        UNDER_MAINTENANCE,
        TEMPORARILY_UNAVAILABLE,
        IMMOBILISED,
        SOLD,
        INACTIVE;

        /** Le véhicule peut-il générer des versements aujourd'hui ? */
        public boolean isOperational() {
            return this == ACTIVE;
        }

        /** Statut de fin de vie : le véhicule reste consultable, mais ne revient pas en exploitation. */
        public boolean isRetired() {
            return this == SOLD;
        }
    }

    /** Mode d'acquisition (instructions §4.1). Le détail du financement vit dans le module Prêts. */
    public enum AcquisitionMethod {
        CASH,
        LOAN,
        OTHER
    }
}
