package org.gu.fms.domain.finance;

/** Une clôture rouverte reste dans l'historique ; une nouvelle clôture la remplace. */
public enum ClosingStatus {
    CLOSED,
    REOPENED
}
