package org.gu.fms.domain.finance;

/**
 * Origine d'une transaction : saisie directe ou module qui l'a produite. Une transaction produite par un module
 * se corrige depuis ce module (avoir de versement, réouverture de clôture…), jamais directement.
 */
public enum TransactionSource {
    MANUAL,
    COLLECTION,
    TRANSFER,
    CASH_CLOSING,
    MAINTENANCE,
    PAYROLL,
    ADVANCE;

    /** Peut-on contre-passer la transaction depuis l'écran Transactions ? */
    public boolean reversibleFromLedger() {
        return this != COLLECTION && this != CASH_CLOSING;
    }
}
