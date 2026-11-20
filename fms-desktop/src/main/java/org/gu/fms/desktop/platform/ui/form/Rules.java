package org.gu.fms.desktop.platform.ui.form;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Règles courantes, réutilisables dans tous les formulaires.
 * <p>
 * Ces règles servent au retour immédiat à l'écran. Les règles métier font toujours foi
 * dans le domaine partagé : le service les revérifie avant d'écrire.
 */
public final class Rules {

    private Rules() {
    }

    public static Rule<String> maxLength(int max) {
        return v -> v.length() > max ? Rule.Issue.of("validation.maxLength", max) : null;
    }

    public static Rule<String> pattern(Pattern pattern, String messageKey) {
        return v -> pattern.matcher(v).matches() ? null : Rule.Issue.of(messageKey);
    }

    public static Rule<Money> notNegativeMoney() {
        return v -> v.isNegative() ? Rule.Issue.of("validation.notNegative") : null;
    }

    public static Rule<Long> positive() {
        return v -> v <= 0 ? Rule.Issue.of("validation.positive") : null;
    }

    public static Rule<Long> range(long min, long max) {
        return v -> v < min || v > max ? Rule.Issue.of("validation.range", min, max) : null;
    }

    public static Rule<Long> notNegative() {
        return v -> v < 0 ? Rule.Issue.of("validation.notNegative") : null;
    }

    public static Rule<LocalDate> notAfter(Supplier<LocalDate> limit) {
        return v -> v.isAfter(limit.get()) ? Rule.Issue.of("validation.notInFuture") : null;
    }

    public static <T> Rule<T> that(Predicate<T> acceptable, String messageKey, Object... args) {
        return v -> acceptable.test(v) ? null : Rule.Issue.of(messageKey, args);
    }
}
