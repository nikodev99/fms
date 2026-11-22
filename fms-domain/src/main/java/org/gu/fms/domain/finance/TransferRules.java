package org.gu.fms.domain.finance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TransferRules {

    private TransferRules() {
    }

    public static List<FieldError> validate(AccountTransfer t, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "date", t.date());
        if (t.date() != null && t.date().isAfter(today)) {
            errors.add(FieldError.of("date", "validation.notInFuture"));
        }
        TextRules.required(errors, "fromAccountId", t.fromAccountId());
        TextRules.required(errors, "toAccountId", t.toAccountId());
        if (t.fromAccountId() != null && Objects.equals(t.fromAccountId(), t.toAccountId())) {
            errors.add(FieldError.of("toAccountId", "transfer.sameAccount"));
        }
        TextRules.required(errors, "amount", t.amount());
        TextRules.positive(errors, "amount", t.amount());
        TextRules.notNegative(errors, "fee", t.fee());
        TextRules.maxLength(errors, "label", TextRules.normalize(t.label()), TransactionRules.LABEL_MAX);
        TextRules.maxLength(errors, "reference", TextRules.normalize(t.reference()), TransactionRules.REFERENCE_MAX);
        return errors;
    }
}
