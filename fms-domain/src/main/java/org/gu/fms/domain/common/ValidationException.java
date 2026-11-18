package org.gu.fms.domain.common;

import java.util.List;

public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<FieldError> errors;

    public ValidationException(List<FieldError> errors) {
        super(summary(errors));
        if (errors.isEmpty())
            throw new IllegalArgumentException("Une ValidationException doit contenir au moins une erreur");

        this.errors = errors;
    }

    public static ValidationException of(FieldError... errors) {
        return new ValidationException(List.of(errors));
    }

    public static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty())
            throw new ValidationException(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }

    private static String summary(List<FieldError> errors) {
        StringBuilder sb = new StringBuilder("Validation échouée :");
        errors.forEach(e -> sb.append(' ').append(e.field() == null ? "*" : e.field()).append("=").append(e.messageKey()));
        return sb.toString();
    }
}
