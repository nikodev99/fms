package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.util.ArrayList;
import java.util.List;

public final class MaintenanceTypeRules {

    public static final int NAME_MAX = 60;

    private MaintenanceTypeRules() {
    }

    public static MaintenanceType normalize(MaintenanceType t) {
        return t.withName(TextRules.normalizeSpaces(t.name()));
    }

    public static List<FieldError> validate(MaintenanceType t) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", t.name(), NAME_MAX);
        TextRules.required(errors, "kind", t.kind());
        TextRules.positive(errors, "defaultIntervalDays", t.defaultIntervalDays());
        TextRules.positive(errors, "defaultIntervalKm", t.defaultIntervalKm());
        return errors;
    }
}
