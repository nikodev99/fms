package org.gu.fms.domain.finance;

import java.util.Objects;

/**
 * Catégorie de transaction (instructions §4.4). Chaque catégorie appartient à un seul sens.
 *
 * Les catégories SYSTÈME portent un code ({@link SystemCategory}) : elles sont utilisées par les modules
 * (versements, virements, clôture, paie, entretien) et leur sens, leur nature et leur code ne changent pas ;
 * seul le libellé peut être adapté.
 *
 * @param directEntry     proposée dans le formulaire « Nouvelle transaction » ; faux pour les catégories alimentées
 *                        uniquement par un module (ex. versements des chauffeurs)
 * @param requiresVehicle le véhicule est obligatoire (ex. carburant)
 */
public record TransactionCategory(
        Long id,
        long version,
        String code,
        String name,
        TransactionDirection direction,
        TransactionNature nature,
        boolean directEntry,
        boolean requiresVehicle,
        boolean system,
        boolean active) {

    public TransactionCategory {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(nature, "nature");
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean is(SystemCategory category) {
        return category.code().equals(code);
    }

    public TransactionCategory withIdentity(long newId, long newVersion) {
        return new TransactionCategory(newId, newVersion, code, name, direction, nature, directEntry, requiresVehicle,
                system, active);
    }

    TransactionCategory withName(String newName) {
        return new TransactionCategory(id, version, code, newName, direction, nature, directEntry, requiresVehicle,
                system, active);
    }
}
