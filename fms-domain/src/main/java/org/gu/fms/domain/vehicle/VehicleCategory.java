package org.gu.fms.domain.vehicle;

/**
 * Catégorie commerciale de véhicule (instructions §1 et §4.1), définie par l'utilisateur.
 *
 * Les six catégories d'origine (Taxi, VTC, Location, Livraison, Véhicule de société, Autre) sont créées
 * par la migration et marquées {@code system} : elles peuvent être renommées ou désactivées, jamais supprimées.
 *
 * Règle de conception : aucun calcul ne dépend du NOM d'une catégorie. Si un comportement doit un jour
 * varier selon le type d'exploitation (location, versements…), il reposera sur un champ fixe dédié,
 * pas sur ce libellé librement modifiable.
 *
 * Une catégorie désactivée n'est plus proposée pour un nouveau véhicule ; les véhicules qui l'utilisent la gardent.
 */
public record VehicleCategory(
        Long id,
        long version,
        String name,
        int sortOrder,
        boolean active,
        boolean system
) {
    public boolean isNew() {
        return id == null;
    }

    public VehicleCategory withIdentity(long newId, long newVersion) {
        return new VehicleCategory(newId, newVersion, name, sortOrder, active, system);
    }

    VehicleCategory withName(String newName) {
        return new VehicleCategory(id, version, newName, sortOrder, active, system);
    }
}
