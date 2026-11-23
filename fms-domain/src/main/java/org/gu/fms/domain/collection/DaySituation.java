package org.gu.fms.domain.collection;

/**
 * Situation d'un chauffeur pour une journée au point journalier.
 * EXEMPT (véhicule immobilisé, maladie, jour de repos accordé…) : aucun montant attendu ce jour-là, avec motif.
 */
public enum DaySituation {
    NORMAL,
    EXEMPT
}
