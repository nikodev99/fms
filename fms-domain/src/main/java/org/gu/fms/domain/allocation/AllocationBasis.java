package org.gu.fms.domain.allocation;

/**
 * Base de calcul d'une provision (instructions §4.6 : « pas forcément un pourcentage des versements »).
 */
public enum AllocationBasis {
    /** Pourcentage des versements encaissés. */
    COLLECTIONS_PERCENT(true),
    /** Pourcentage des versements après rémunération des chauffeurs. */
    NET_COLLECTIONS_PERCENT(true),
    /** Montant fixe par mois pour toute la flotte. */
    FIXED_MONTHLY(false),
    /** Montant fixe par mois et par véhicule en activité. */
    FIXED_MONTHLY_PER_VEHICLE(false),
    /** Montant par kilomètre parcouru. */
    PER_KM(false);

    private final boolean percentage;

    AllocationBasis(boolean percentage) {
        this.percentage = percentage;
    }

    public boolean isPercentage() {
        return percentage;
    }
}
