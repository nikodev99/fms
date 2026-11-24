package org.gu.fms.domain.security;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Règles des comptes utilisateurs et des mots de passe. */
public final class UserRules {

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 30;
    public static final int NAME_MAX = 60;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;
    /** Échecs consécutifs avant verrouillage temporaire. */
    public static final int MAX_FAILURES = 5;

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]+");

    private UserRules() {
    }

    public static AppUser normalize(AppUser u) {
        String name = TextRules.normalizeSpaces(u.username());
        return new AppUser(u.id(), u.version(), name == null ? null : name.toLowerCase(Locale.ROOT),
                TextRules.normalizeSpaces(u.displayName()), u.role(), u.active(), u.mustChangePassword(),
                u.failedAttempts(), u.lockedUntil(), u.lastLoginAt(), u.modules());
    }

    public static List<FieldError> validate(AppUser u) {
        List<FieldError> errors = new ArrayList<>();
        String name = u.username();
        if (name == null) {
            errors.add(FieldError.of("username", "validation.required"));
        } else if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX || !USERNAME.matcher(name).matches()) {
            errors.add(FieldError.of("username", "user.username.invalid", USERNAME_MIN, USERNAME_MAX));
        }
        TextRules.requireText(errors, "displayName", u.displayName(), NAME_MAX);
        TextRules.required(errors, "role", u.role());
        if (u.role() == UserRole.USER && u.modules().isEmpty()) {
            errors.add(FieldError.of("modules", "user.modules.required"));
        }
        return errors;
    }

    /** Mot de passe : 8 caractères au moins, avec au moins une lettre et un chiffre. */
    public static List<FieldError> validatePassword(String field, String password) {
        List<FieldError> errors = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            errors.add(FieldError.of(field, "validation.required"));
            return errors;
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX
                || password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            errors.add(FieldError.of(field, "user.password.weak", PASSWORD_MIN));
        }
        return errors;
    }

    /** Durée du verrouillage après {@code failures} échecs consécutifs : 1 min, puis doublée, 30 min au plus. */
    public static Duration lockFor(int failures) {
        if (failures < MAX_FAILURES) {
            return Duration.ZERO;
        }
        long minutes = 1L << Math.min(failures - MAX_FAILURES, 5);
        return Duration.ofMinutes(Math.min(minutes, 30));
    }
}
