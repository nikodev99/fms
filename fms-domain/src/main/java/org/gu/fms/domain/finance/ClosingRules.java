package org.gu.fms.domain.finance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Règles de la clôture de caisse. */
public final class ClosingRules {

    public static final int EXPLANATION_MAX = 300;

    private ClosingRules() {
    }

    /**
     * @param actual      solde réellement compté ou relevé
     * @param explanation obligatoire dès qu'il y a un écart
     */
    public static List<FieldError> validate(LocalDate date, LocalDate today, FinancialAccount account,
                                            ClosingFigures figures, Money actual, String explanation) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "date", date);
        if (date != null && date.isAfter(today)) {
            errors.add(FieldError.of("date", "validation.notInFuture"));
        }
        if (date != null && account.openingDate() != null && date.isBefore(account.openingDate())) {
            errors.add(FieldError.of("date", "closing.beforeOpening"));
        }
        TextRules.required(errors, "actual", actual);
        if (actual != null && !AccountRules.balanceAllowed(account, actual)) {
            errors.add(FieldError.of("actual", "account.negativeNotAllowed"));
        }
        String text = TextRules.normalize(explanation);
        if (actual != null && figures != null && !actual.equals(figures.expected()) && text == null) {
            errors.add(FieldError.of("explanation", "closing.explanationRequired"));
        }
        TextRules.maxLength(errors, "explanation", text, EXPLANATION_MAX);
        return errors;
    }
}
