package org.gu.fms.domain.document;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Type de document à échéance (assurance, vignette, contrôle technique, licence, permis…) — Instructions §8.
 * Même modèle que l'édition Excel (« Obligatoire pour » Véhicule / Chauffeur).
 * Le suivi des documents eux-mêmes (dates d'expiration par véhicule et par chauffeur) viendra avec le module Documents.
 *
 * @param mandatory un document obligatoire expiré ou manquant déclenche une alerte
 */
public record DocumentType(Long id, long version, String name, DocumentSubject subject, boolean mandatory, boolean active) {

    public static final int NAME_MAX = 60;

    public boolean isNew() {
        return id == null;
    }

    public DocumentType withIdentity(long newId, long newVersion) {
        return new DocumentType(newId, newVersion, name, subject, mandatory, active);
    }

    public static DocumentType normalize(DocumentType t) {
        return new DocumentType(t.id, t.version, TextRules.normalizeSpaces(t.name), t.subject, t.mandatory, t.active);
    }

    public static List<FieldError> validate(DocumentType t) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", t.name, NAME_MAX);
        TextRules.required(errors, "subject", t.subject);
        return errors;
    }

    /** Ce que concerne un document à échéance (instructions §8). */
    public enum DocumentSubject {
        VEHICLE,
        DRIVER
    }
}
