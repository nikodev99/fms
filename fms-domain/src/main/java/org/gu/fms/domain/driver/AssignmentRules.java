package org.gu.fms.domain.driver;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Règles des affectations chauffeur ↔ véhicule.
 *
 * Conflits interdits (périodes qui se chevauchent, fin vide = sans limite) :
 *  1. deux chauffeurs PRINCIPAUX sur le même véhicule ;
 *  2. un même chauffeur PRINCIPAL sur deux véhicules ;
 *  3. deux affectations du même chauffeur sur le même véhicule (quel que soit le rôle).
 * Remplaçants et chauffeurs de nuit peuvent sinon se cumuler librement.
 */
public final class AssignmentRules {

    public static final int NOTES_MAX = 500;

    private AssignmentRules() {
    }

    public static DriverAssignment normalize(DriverAssignment a) {
        return a.withNotes(TextRules.normalize(a.notes()));
    }

    public static List<FieldError> validate(DriverAssignment a) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "driverId", a.driverId());
        TextRules.required(errors, "vehicleId", a.vehicleId());
        TextRules.required(errors, "role", a.role());
        TextRules.required(errors, "startDate", a.startDate());
        if (a.startDate() != null && a.endDate() != null && a.endDate().isBefore(a.startDate())) {
            errors.add(FieldError.of("endDate", "assignment.endBeforeStart"));
        }
        TextRules.maxLength(errors, "notes", a.notes(), NOTES_MAX);
        return errors;
    }

    /** Les deux périodes ont-elles au moins un jour en commun ? */
    public static boolean overlaps(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        LocalDate e1 = end1 == null ? LocalDate.MAX : end1;
        LocalDate e2 = end2 == null ? LocalDate.MAX : end2;
        return !start1.isAfter(e2) && !start2.isAfter(e1);
    }

    /**
     * Conflits entre {@code candidate} et les affectations existantes (la candidate elle-même, si elle est
     * déjà enregistrée, est ignorée). Chaque conflit renvoie la clé et l'identifiant de l'affectation en cause ;
     * le service ajoute les noms lisibles.
     */
    public static List<Conflict> conflicts(DriverAssignment candidate, List<DriverAssignment> existing) {
        List<Conflict> result = new ArrayList<>();
        for (DriverAssignment other : existing) {
            if (Objects.equals(other.id(), candidate.id())
                    || !overlaps(candidate.startDate(), candidate.endDate(), other.startDate(), other.endDate())) {
                continue;
            }
            boolean sameVehicle = Objects.equals(other.vehicleId(), candidate.vehicleId());
            boolean sameDriver = Objects.equals(other.driverId(), candidate.driverId());
            boolean bothPrimary = candidate.role() == DriverAssignment.AssignmentRole.PRIMARY && other.role() == DriverAssignment.AssignmentRole.PRIMARY;
            if (sameVehicle && sameDriver) {
                result.add(new Conflict(Kind.DUPLICATE, other));
            } else if (sameVehicle && bothPrimary) {
                result.add(new Conflict(Kind.VEHICLE_HAS_PRIMARY, other));
            } else if (sameDriver && bothPrimary) {
                result.add(new Conflict(Kind.DRIVER_ALREADY_PRIMARY, other));
            }
        }
        return result;
    }

    public enum Kind {
        DUPLICATE("assignment.duplicate"),
        VEHICLE_HAS_PRIMARY("assignment.vehicleHasPrimary"),
        DRIVER_ALREADY_PRIMARY("assignment.driverAlreadyPrimary");

        private final String messageKey;

        Kind(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public record Conflict(Kind kind, DriverAssignment with) {
    }
}
