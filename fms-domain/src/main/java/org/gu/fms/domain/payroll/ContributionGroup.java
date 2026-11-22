package org.gu.fms.domain.payroll;

/**
 * Famille d'une cotisation. Les cotisations CNSS ne s'appliquent qu'aux chauffeurs déclarés à la CNSS
 * (case « Déclaré à la CNSS » de la fiche chauffeur, instructions §4.7).
 */
public enum ContributionGroup {
    CNSS,
    CAMU,
    PAYROLL_TAX,
    OTHER
}
