package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class MaintenanceEventRules {

    public static final int DESCRIPTION_MAX = 200;
    public static final int SUPPLIER_MAX = 100;
    public static final int REASON_MAX = 300;
    public static final int NOTES_MAX = 1000;

    private MaintenanceEventRules() {
    }

    public static MaintenanceEvent normalize(MaintenanceEvent e) {
        String reason = e.status() == MaintenanceEvent.MaintenanceEventStatus.CANCELLED ? TextRules.normalize(e.cancelReason()) : null;
        return e.withText(TextRules.normalize(e.description()), TextRules.normalizeSpaces(e.supplier()), reason,
                TextRules.normalize(e.notes()));
    }

    public static List<FieldError> validate(MaintenanceEvent e, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "vehicleId", e.vehicleId());
        TextRules.required(errors, "typeId", e.typeId());
        TextRules.required(errors, "date", e.date());
        if (e.date() != null && e.date().isAfter(today)) {
            errors.add(FieldError.of("date", "validation.notInFuture"));
        }
        if (e.mileage() != null && e.mileage() < 0) {
            errors.add(FieldError.of("mileage", "validation.notNegative"));
        }
        TextRules.required(errors, "cost", e.cost());
        TextRules.notNegative(errors, "cost", e.cost());
        TextRules.maxLength(errors, "description", e.description(), DESCRIPTION_MAX);
        TextRules.maxLength(errors, "supplier", e.supplier(), SUPPLIER_MAX);
        TextRules.maxLength(errors, "notes", e.notes(), NOTES_MAX);
        if (e.status() == MaintenanceEvent.MaintenanceEventStatus.CANCELLED) {
            TextRules.requireText(errors, "cancelReason", e.cancelReason(), REASON_MAX);
        }
        return errors;
    }

    /**
     * Modification d'une intervention existante. Une intervention annulée est figée : elle ne peut être
     * ni modifiée ni rétablie (on saisit une nouvelle intervention à la place).
     */
    public static List<FieldError> validateChange(MaintenanceEvent before, MaintenanceEvent after) {
        List<FieldError> errors = new ArrayList<>();
        if (before.status() == MaintenanceEvent.MaintenanceEventStatus.CANCELLED) {
            errors.add(FieldError.of(null, "maintenanceEvent.cancelledIsFinal"));
        }
        if (!before.vehicleId().equals(after.vehicleId())) {
            errors.add(FieldError.of("vehicleId", "maintenanceEvent.vehicleFixed"));
        }
        return errors;
    }
}
