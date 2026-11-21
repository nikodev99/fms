package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Règles et calcul d'échéance des plans d'entretien.
 *
 * Échéance :
 *  - EN RETARD si la date du jour est après la date d'échéance, ou si le kilométrage actuel a atteint
 *    le kilométrage d'échéance ;
 *  - BIENTÔT si l'on est à {@code leadDays} jours ou {@code leadKm} km (ou moins) de l'échéance ;
 *  - le premier critère atteint l'emporte (date OU kilométrage).
 */
public final class MaintenancePlanRules {

    public static final int NOTES_MAX = 500;

    private MaintenancePlanRules() {
    }

    public static MaintenancePlan normalize(MaintenancePlan p) {
        return p.withNotes(TextRules.normalize(p.notes()));
    }

    public static List<FieldError> validate(MaintenancePlan p, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "vehicleId", p.vehicleId());
        TextRules.required(errors, "typeId", p.typeId());
        if (p.intervalDays() == null && p.intervalKm() == null) {
            errors.add(FieldError.of("intervalDays", "maintenancePlan.intervalRequired"));
        }
        TextRules.positive(errors, "intervalDays", p.intervalDays());
        TextRules.positive(errors, "intervalKm", p.intervalKm());
        if (p.lastDoneDate() != null && p.lastDoneDate().isAfter(today)) {
            errors.add(FieldError.of("lastDoneDate", "validation.notInFuture"));
        }
        if (p.lastDoneKm() != null && p.lastDoneKm() < 0) {
            errors.add(FieldError.of("lastDoneKm", "validation.notNegative"));
        }
        TextRules.maxLength(errors, "notes", p.notes(), NOTES_MAX);
        return errors;
    }

    /**
     * @param currentKm kilométrage actuel du véhicule (peut être nul)
     * @param leadDays  délai d'alerte en jours (paramètre)
     * @param leadKm    délai d'alerte en kilomètres (paramètre)
     */
    public static DueStatus status(MaintenancePlan p, LocalDate today, Long currentKm, long leadDays, long leadKm) {
        if (!p.active()) {
            return DueStatus.INACTIVE;
        }
        LocalDate dueDate = p.nextDueDate();
        Long dueKm = p.nextDueKm();
        boolean kmKnown = dueKm != null && currentKm != null;
        if (dueDate == null && !kmKnown) {
            return DueStatus.NOT_STARTED;
        }
        if ((dueDate != null && today.isAfter(dueDate)) || (kmKnown && currentKm >= dueKm)) {
            return DueStatus.OVERDUE;
        }
        if ((dueDate != null && !today.isBefore(dueDate.minusDays(leadDays)))
                || (kmKnown && currentKm >= dueKm - leadKm)) {
            return DueStatus.DUE_SOON;
        }
        return DueStatus.OK;
    }

    /**
     * Plan complété par l'historique : la dernière réalisation retenue est la plus récente entre la référence
     * saisie dans le plan ({@code lastDoneDate}/{@code lastDoneKm}, ex. dernier entretien connu avant FMS)
     * et les interventions NON ANNULÉES du même type sur le même véhicule.
     *
     * Rien n'est recopié dans le plan : annuler une intervention erronée recalcule l'échéance d'elle-même.
     */
    public static MaintenancePlan withHistory(MaintenancePlan p, List<MaintenanceEvent> events) {
        MaintenanceEvent latest = null;
        for (MaintenanceEvent e : events) {
            if (!e.counts() || !p.vehicleId().equals(e.vehicleId()) || !p.typeId().equals(e.typeId())
                    || e.date() == null) {
                continue;
            }
            if (latest == null || e.date().isAfter(latest.date())
                    || (e.date().equals(latest.date()) && km(e) > km(latest))) {
                latest = e;
            }
        }
        if (latest == null || (p.lastDoneDate() != null && p.lastDoneDate().isAfter(latest.date()))) {
            return p;
        }
        Long doneKm = latest.mileage() != null ? latest.mileage() : p.lastDoneKm();
        return p.withLastDone(latest.date(), doneKm);
    }

    private static long km(MaintenanceEvent e) {
        return e.mileage() == null ? -1 : e.mileage();
    }
}
