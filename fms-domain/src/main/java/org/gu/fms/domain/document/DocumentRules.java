package org.gu.fms.domain.document;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Règles des documents à échéance. */
public final class DocumentRules {

    public static final int NUMBER_MAX = 60;
    public static final int ISSUER_MAX = 80;
    public static final int NOTES_MAX = 500;

    private DocumentRules() {
    }

    public static FleetDocument normalize(FleetDocument d) {
        return new FleetDocument(d.id(), d.version(), d.typeId(), d.subject(), d.vehicleId(), d.driverId(),
                TextRules.normalizeSpaces(d.number()), TextRules.normalizeSpaces(d.issuer()), d.issuedOn(),
                d.expiresOn(), d.replacesId(), d.superseded(), TextRules.normalize(d.notes()));
    }

    /**
     * Un document déjà expiré peut être enregistré (régularisation d'un historique) : il sera signalé, pas refusé.
     * Un document vide (ni numéro, ni date) n'a pas de sens : au moins l'un des deux est exigé.
     */
    public static List<FieldError> validate(FleetDocument d, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "typeId", d.typeId());
        TextRules.required(errors, "subject", d.subject());
        if (d.subject() != null && d.ownerId() == null) {
            errors.add(FieldError.of("ownerId", "validation.required"));
        }
        TextRules.maxLength(errors, "number", d.number(), NUMBER_MAX);
        TextRules.maxLength(errors, "issuer", d.issuer(), ISSUER_MAX);
        TextRules.maxLength(errors, "notes", d.notes(), NOTES_MAX);
        if (d.issuedOn() != null && d.issuedOn().isAfter(today)) {
            errors.add(FieldError.of("issuedOn", "validation.notInFuture"));
        }
        if (d.issuedOn() != null && d.expiresOn() != null && d.expiresOn().isBefore(d.issuedOn())) {
            errors.add(FieldError.of("expiresOn", "document.expiresBeforeIssue"));
        }
        if (d.number() == null && d.expiresOn() == null && d.issuedOn() == null) {
            errors.add(FieldError.of("number", "document.empty"));
        }
        return errors;
    }

    /** Conformité d'un document en cours ; {@code null} = aucun document (manquant si obligatoire). */
    public static DocumentStatus status(FleetDocument current, boolean mandatory, LocalDate today, long alertDays) {
        if (current == null) {
            return mandatory ? DocumentStatus.MISSING : null;
        }
        LocalDate expiry = current.expiresOn();
        if (expiry == null) {
            return DocumentStatus.VALID;
        }
        if (expiry.isBefore(today)) {
            return DocumentStatus.EXPIRED;
        }
        return !expiry.isAfter(today.plusDays(alertDays)) ? DocumentStatus.EXPIRING : DocumentStatus.VALID;
    }

    /** Jours restants avant expiration (négatif si expiré) ; null sans échéance. */
    public static Long daysLeft(FleetDocument d, LocalDate today) {
        return d == null || d.expiresOn() == null ? null : ChronoUnit.DAYS.between(today, d.expiresOn());
    }

    /** Un renouvellement doit expirer après le document qu'il remplace. */
    public static List<FieldError> validateRenewal(FleetDocument previous, FleetDocument next) {
        List<FieldError> errors = new ArrayList<>();
        if (!previous.isCurrent()) {
            errors.add(FieldError.of(null, "document.alreadyReplaced"));
        }
        if (!previous.typeId().equals(next.typeId()) || previous.subject() != next.subject()
                || !previous.ownerId().equals(next.ownerId())) {
            errors.add(FieldError.of(null, "document.renewalMismatch"));
        }
        if (previous.expiresOn() != null && next.expiresOn() != null && !next.expiresOn().isAfter(previous.expiresOn())) {
            errors.add(FieldError.of("expiresOn", "document.renewalNotLater"));
        }
        return errors;
    }
}
