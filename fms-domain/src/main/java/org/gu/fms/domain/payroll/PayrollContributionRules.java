package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

public final class PayrollContributionRules {

    public static final int CODE_MAX = 40;
    public static final int NAME_MAX = 80;
    public static final int SOURCE_MAX = 300;
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    private PayrollContributionRules() {
    }

    /** Code en majuscules, espaces et tirets remplacés par « _ ». */
    public static PayrollContribution normalize(PayrollContribution c) {
        String code = TextRules.normalize(c.code());
        if (code != null) {
            code = code.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        }
        return c.withText(code, TextRules.normalizeSpaces(c.name()), TextRules.normalize(c.legalSource()));
    }

    public static List<FieldError> validate(PayrollContribution c) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "code", c.code(), CODE_MAX);
        if (c.code() != null && !CODE.matcher(c.code()).matches()) {
            errors.add(FieldError.of("code", "contribution.code.invalid"));
        }
        TextRules.requireText(errors, "name", c.name(), NAME_MAX);
        TextRules.required(errors, "group", c.group());
        TextRules.required(errors, "payer", c.payer());
        TextRules.required(errors, "rate", c.rate());
        TextRules.required(errors, "effectiveFrom", c.effectiveFrom());
        TextRules.requireText(errors, "legalSource", c.legalSource(), SOURCE_MAX);
        TextRules.positive(errors, "monthlyCeiling", c.monthlyCeiling());
        TextRules.notNegative(errors, "monthlyFloor", c.monthlyFloor());
        if (c.monthlyCeiling() != null && c.monthlyFloor() != null
                && c.monthlyFloor().compareTo(c.monthlyCeiling()) >= 0) {
            errors.add(FieldError.of("monthlyFloor", "contribution.floorAboveCeiling"));
        }
        return errors;
    }

    /** Ligne en vigueur pour un code à une date : la plus récente dont la date d'effet ne dépasse pas {@code day}. */
    public static Optional<PayrollContribution> inForce(List<PayrollContribution> all, String code, LocalDate day) {
        return all.stream()
                .filter(c -> c.code().equals(code) && !c.effectiveFrom().isAfter(day))
                .max(Comparator.comparing(PayrollContribution::effectiveFrom));
    }
}