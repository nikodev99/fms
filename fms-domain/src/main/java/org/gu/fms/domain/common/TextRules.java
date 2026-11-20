package org.gu.fms.domain.common;

import org.gu.fms.domain.money.Money;

import java.util.List;
import java.util.regex.Pattern;

/** Normalisations et contrôles de champs courants, partagés par tous les référentiels. */
public final class TextRules {

    /** Téléphone : chiffres, espaces, « + » en tête, tirets et points ; 6 à 20 caractères. */
    public static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 .-]{4,18}[0-9]");
    public static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextRules() {
    }

    /** Espaces de bord retirés, chaîne vide → null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        return value.isEmpty() ? null : value;
    }

    /** Espaces de bord retirés et espaces internes réduits à un seul. */
    public static String normalizeSpaces(String raw) {
        String value = normalize(raw);
        return value == null ? null : SPACES.matcher(value).replaceAll(" ");
    }

    public static void requireText(List<FieldError> errors, String field, String value, int max) {
        if (value == null) {
            errors.add(FieldError.of(field, "validation.required"));
        } else {
            maxLength(errors, field, value, max);
        }
    }

    public static void maxLength(List<FieldError> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(FieldError.of(field, "validation.maxLength", max));
        }
    }

    public static void phone(List<FieldError> errors, String field, String value) {
        if (value != null && !PHONE.matcher(value).matches()) {
            errors.add(FieldError.of(field, "validation.phone"));
        }
    }

    public static void email(List<FieldError> errors, String field, String value) {
        if (value != null && !EMAIL.matcher(value).matches()) {
            errors.add(FieldError.of(field, "validation.email"));
        }
    }

    public static void notNegative(List<FieldError> errors, String field, Money value) {
        if (value != null && value.isNegative()) {
            errors.add(FieldError.of(field, "validation.notNegative"));
        }
    }

    public static void positive(List<FieldError> errors, String field, Money value) {
        if (value != null && !value.isPositive()) {
            errors.add(FieldError.of(field, "validation.positive"));
        }
    }

    public static void positive(List<FieldError> errors, String field, Long value) {
        if (value != null && value <= 0) {
            errors.add(FieldError.of(field, "validation.positive"));
        }
    }

    public static void required(List<FieldError> errors, String field, Object value) {
        if (value == null) {
            errors.add(FieldError.of(field, "validation.required"));
        }
    }
}
