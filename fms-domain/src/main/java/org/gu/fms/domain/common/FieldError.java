package org.gu.fms.domain.common;

import java.util.List;
import java.util.Objects;

public record FieldError(String field, String messageKey, List<Object> args) {
    public FieldError {
        Objects.requireNonNull(messageKey, "messageKey");
        args = List.copyOf(args);
    }

    public static FieldError of (String field, String messageKey, Object... args) {
        return new FieldError(field, messageKey, List.of(args));
    }

    public boolean isGlobal() {
        return field == null;
    }
}
