package org.gu.fms.domain.vehicle;


import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.util.ArrayList;
import java.util.List;

/** Règles d'une catégorie de véhicule. L'unicité du nom (sans casse) est vérifiée par le service. */
public final class VehicleCategoryRules {

    public static final int NAME_MAX = 40;
    public static final int SORT_MAX = 999;

    private VehicleCategoryRules() {
    }

    public static VehicleCategory normalize(VehicleCategory c) {
        return c.withName(TextRules.normalizeSpaces(c.name()));
    }

    public static List<FieldError> validate(VehicleCategory c) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", c.name(), NAME_MAX);
        if (c.sortOrder() < 0 || c.sortOrder() > SORT_MAX) {
            errors.add(FieldError.of("sortOrder", "validation.range", 0, SORT_MAX));
        }
        return errors;
    }
}
