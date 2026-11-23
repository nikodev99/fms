package org.gu.fms.domain.collection;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Règles des versements (instructions §4.3), partagées Desktop / SaaS.
 *
 * Modification directe possible seulement pour un versement du JOUR, valide, dont la journée n'est ni pointée pour
 * ce chauffeur ni clôturée pour ce compte ; motif obligatoire, ancienne et nouvelle valeur tracées.
 * Dans tous les autres cas : avoir.
 */
public final class CollectionRules {

    public static final int NOTES_MAX = 500;
    public static final int REASON_MAX = 300;

    private CollectionRules() {
    }

    public static CollectionEntry normalize(CollectionEntry c) {
        return c.withText(TextRules.normalize(c.reason()), TextRules.normalize(c.notes()));
    }

    public static List<FieldError> validate(CollectionEntry c, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "date", c.date());
        if (c.date() != null && c.date().isAfter(today)) {
            errors.add(FieldError.of("date", "validation.notInFuture"));
        }
        TextRules.required(errors, "driverId", c.driverId());
        TextRules.required(errors, "vehicleId", c.vehicleId());
        TextRules.required(errors, "accountId", c.accountId());
        TextRules.required(errors, "amount", c.amount());
        TextRules.positive(errors, "amount", c.amount());
        TextRules.maxLength(errors, "reason", c.reason(), REASON_MAX);
        TextRules.maxLength(errors, "notes", c.notes(), NOTES_MAX);
        return errors;
    }

    /** Pourquoi le versement ne peut-il pas être modifié directement ? null = modifiable. */
    public static String lockReason(CollectionEntry c, LocalDate today, boolean dayPointed, boolean accountClosed) {
        if (c.isCreditNote()) {
            return "collection.locked.creditNote";
        }
        if (c.status() != CollectionStatus.VALID) {
            return "collection.locked.credited";
        }
        if (!today.equals(c.date())) {
            return "collection.locked.pastDay";
        }
        if (dayPointed) {
            return "collection.locked.pointed";
        }
        if (accountClosed) {
            return "collection.locked.closed";
        }
        return null;
    }

    /** Une modification du jour : date figée, motif obligatoire, un changement réel. */
    public static List<FieldError> validateChange(CollectionEntry before, CollectionEntry after) {
        List<FieldError> errors = new ArrayList<>();
        if (!Objects.equals(before.date(), after.date())) {
            errors.add(FieldError.of("date", "collection.dateLocked"));
        }
        if (after.reason() == null) {
            errors.add(FieldError.of("reason", "collection.reasonRequired"));
        }
        return errors;
    }

    /** Avoir annulant {@code origin} en totalité, daté du jour, attribué au même chauffeur, véhicule et compte. */
    public static CollectionEntry creditNoteFor(CollectionEntry origin, LocalDate today, Instant now, String user, String reason) {
        if (origin.isCreditNote() || origin.status() != CollectionStatus.VALID) {
            throw new IllegalArgumentException("Versement non annulable : " + origin.id());
        }
        return new CollectionEntry(null, 0, today, now, origin.driverId(), origin.vehicleId(), origin.accountId(),
                origin.amount(), CollectionKind.CREDIT_NOTE, CollectionStatus.VALID, origin.id(), null, user,
                TextRules.normalize(reason), null);
    }
}
