package org.gu.fms.domain.collection;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Règles des mouvements saisis à la main au compte chauffeur (ajustements). */
public final class LedgerRules {

    public static final int LABEL_MAX = 200;

    private LedgerRules() {
    }

    public static List<FieldError> validateManual(DriverLedgerEntry e, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "driverId", e.driverId());
        TextRules.required(errors, "rubric", e.rubric());
        TextRules.required(errors, "date", e.date());
        if (e.date() != null && e.date().isAfter(today)) {
            errors.add(FieldError.of("date", "validation.notInFuture"));
        }
        if (e.amount() == null) {
            errors.add(FieldError.of("amount", "validation.required"));
        } else if (e.amount().isZero()) {
            errors.add(FieldError.of("amount", "validation.positive"));
        }
        if (e.rubric() == LedgerRubric.ADVANCE) {
            errors.add(FieldError.of("rubric", "ledger.advanceThroughCash"));
        }
        TextRules.requireText(errors, "label", TextRules.normalize(e.label()), LABEL_MAX);
        return errors;
    }
}
