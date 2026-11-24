package org.gu.fms.domain.loan;

public enum LoanStatus {
    /** Échéances en cours. */
    ACTIVE,
    /** Toutes les échéances sont payées. */
    REPAID,
    /** Saisi par erreur ou jamais débloqué : annulé avec un motif, aucune échéance payée. */
    CANCELLED
}
