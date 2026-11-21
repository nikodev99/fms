package org.gu.fms.domain.maintenance;

/** Échéance d'un plan d'entretien. */
public enum DueStatus {
    /** Date ou kilométrage dépassé. */
    OVERDUE,
    /** Dans le délai d'alerte (jours ou km) défini dans les paramètres. */
    DUE_SOON,
    OK,
    /** Jamais effectué : aucune référence pour calculer l'échéance. */
    NOT_STARTED,
    /** Plan désactivé. */
    INACTIVE
}