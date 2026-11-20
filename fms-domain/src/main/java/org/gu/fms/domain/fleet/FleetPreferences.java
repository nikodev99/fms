package org.gu.fms.domain.fleet;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.money.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Valeurs par défaut et délais d'alerte de la flotte (Paramètres › Flotte).
 *
 * Ce sont des préférences de gestion modifiables, pas des règles légales.
 *
 * @param defaultRepairThreshold seuil de réparations proposé à la création d'un véhicule (null = aucun)
 * @param repairWarningPercent   part du seuil à partir de laquelle un véhicule est « proche du seuil »
 * @param documentAlertDays      alerte avant l'expiration d'un document (permis, assurance…)
 * @param maintenanceAlertDays   alerte avant l'échéance d'un entretien (date)
 * @param maintenanceAlertKm     alerte avant l'échéance d'un entretien (kilométrage)
 */
public record FleetPreferences(Money defaultRepairThreshold, Percent repairWarningPercent, long documentAlertDays,
                               long maintenanceAlertDays, long maintenanceAlertKm) {

    public static final long DAYS_MAX = 365;
    public static final long KM_MAX = 50_000;

    public List<FieldError> validate() {
        List<FieldError> errors = new ArrayList<>();
        TextRules.notNegative(errors, "defaultRepairThreshold", defaultRepairThreshold);
        TextRules.required(errors, "repairWarningPercent", repairWarningPercent);
        if (repairWarningPercent != null && (repairWarningPercent.isZero()
                || repairWarningPercent.compareTo(Percent.HUNDRED) >= 0)) {
            errors.add(FieldError.of("repairWarningPercent", "fleet.warningPercent.range"));
        }
        range(errors, "documentAlertDays", documentAlertDays, DAYS_MAX);
        range(errors, "maintenanceAlertDays", maintenanceAlertDays, DAYS_MAX);
        range(errors, "maintenanceAlertKm", maintenanceAlertKm, KM_MAX);
        return errors;
    }

    private static void range(List<FieldError> errors, String field, long value, long max) {
        if (value < 0 || value > max) {
            errors.add(FieldError.of(field, "validation.range", 0, max));
        }
    }
}