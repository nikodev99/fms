package org.gu.fms.domain.driver;

/**
 * Mode de rémunération convenu (instructions §4.7).
 * Le pourcentage s'applique aux versements du chauffeur ; le calcul lui-même appartient au module Paie.
 */
public enum PayScheme {
    FIXED,
    PERCENTAGE,
    FIXED_PLUS_COMMISSION;

    public boolean hasFixedPart() {
        return this == FIXED || this == FIXED_PLUS_COMMISSION;
    }

    public boolean hasCommission() {
        return this == PERCENTAGE || this == FIXED_PLUS_COMMISSION;
    }
}
