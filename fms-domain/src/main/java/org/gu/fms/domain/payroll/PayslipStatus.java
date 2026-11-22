package org.gu.fms.domain.payroll;

/**
 * Cycle d'un bulletin : BROUILLON (recalculable, ajustable) → VALIDÉ (figé ; retenues portées au compte chauffeur)
 * → PAYÉ (paiement enregistré : sortie de trésorerie liée). ANNULÉ depuis brouillon ou validé non payé ; un bulletin
 * payé s'annule en contre-passant d'abord son paiement.
 */
public enum PayslipStatus {
    DRAFT,
    VALIDATED,
    PAID,
    CANCELLED
}
