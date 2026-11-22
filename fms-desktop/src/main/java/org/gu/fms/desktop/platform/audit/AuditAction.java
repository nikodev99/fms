package org.gu.fms.desktop.platform.audit;

/** Types d'opérations tracées. */
public enum AuditAction {
    CREATE,
    UPDATE,
    /** Contre-passation, avoir, virement annulé. */
    REVERSE,
    /** Clôture de caisse, point journalier. */
    CLOSE,
    /** Réouverture d'une clôture. */
    REOPEN,
    /** Validation d'un bulletin. */
    VALIDATE,
    /** Paiement enregistré. */
    PAY,
    /** Annulation d'un bulletin. */
    CANCEL
}
