package org.gu.fms.domain.finance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Règles des catégories de transactions. */
public final class CategoryRules {

    public static final int NAME_MAX = 60;

    private CategoryRules() {
    }

    public static TransactionCategory normalize(TransactionCategory c) {
        return c.withName(TextRules.normalizeSpaces(c.name()));
    }

    public static List<FieldError> validate(TransactionCategory c) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", c.name(), NAME_MAX);
        if (c.nature() == TransactionNature.TRANSFER && !c.system()) {
            errors.add(FieldError.of("nature", "txCategory.transferReserved"));
        }
        return errors;
    }

    /**
     * Une catégorie système ne change pas du tout (elle est alimentée par un module). Sur une catégorie utilisée, le
     * sens et la nature sont figés : les transactions passées garderaient sinon une nature différente de leur catégorie.
     */
    public static List<FieldError> validateChange(TransactionCategory before, TransactionCategory after, boolean used) {
        List<FieldError> errors = new ArrayList<>();
        boolean flowChanged = before.direction() != after.direction() || before.nature() != after.nature();
        if (before.system()) {
            errors.add(FieldError.of(null, "txCategory.systemLocked"));
        } else if (used && flowChanged) {
            errors.add(FieldError.of("direction", "txCategory.flowLocked"));
        }
        return errors;
    }
}