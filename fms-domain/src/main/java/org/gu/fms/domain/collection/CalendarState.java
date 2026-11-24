package org.gu.fms.domain.collection;

/**
 * État d'une case du calendrier des versements (un chauffeur, un jour).
 */
public enum CalendarState {
    /** Montant attendu versé en entier (vert). */
    FULL,
    /** Versement entre le seuil rouge et 100 % de l'attendu (orange). */
    PARTIAL,
    /** Versement sous le seuil rouge, ou rien (rouge). */
    MISSED,
    /** Exempté au point journalier : panne, repos accordé… ne compte pas comme manquant (gris-bleu, 0). */
    EXEMPT,
    /** Jour de repos de la flotte sans versement (gris). */
    REST,
    /** Versement reçu un jour où rien n'était attendu (repos, exemption) : compte comme un plus (vert). */
    BONUS,
    /** Journée en cours, pas encore complète : rien n'est conclu. */
    PENDING,
    /** Pas d'affectation ce jour-là, ou jour à venir (vide). */
    NONE
}
