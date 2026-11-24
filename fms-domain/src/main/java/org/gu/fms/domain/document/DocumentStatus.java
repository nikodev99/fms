package org.gu.fms.domain.document;

/**
 * Conformité d'un document, du plus grave au moins grave (ordre utilisé pour l'état global d'un titulaire).
 */
public enum DocumentStatus {
    /** Date d'expiration dépassée. */
    EXPIRED,
    /** Document obligatoire jamais enregistré. */
    MISSING,
    /** Expire dans le délai d'alerte (Paramètres › Flotte). */
    EXPIRING,
    /** À jour, ou sans échéance. */
    VALID;

    /** Le plus grave des deux. */
    public DocumentStatus worst(DocumentStatus other) {
        return other == null || ordinal() <= other.ordinal() ? this : other;
    }
}
