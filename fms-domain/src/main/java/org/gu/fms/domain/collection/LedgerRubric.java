package org.gu.fms.domain.collection;

/**
 * Rubriques du compte chauffeur (ce que le chauffeur doit à l'entreprise). Les cautions viendront plus tard
 * (dette envers le chauffeur, jamais une recette).
 */
public enum LedgerRubric {
    SHORTAGE,
    ADVANCE,
    OTHER_DEBT
}
