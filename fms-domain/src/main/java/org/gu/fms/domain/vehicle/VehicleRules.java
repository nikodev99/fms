package org.gu.fms.domain.vehicle;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Règles de validation d'un véhicule, partagées par le Desktop et le SaaS.
 * <p>
 * Le format exact des plaques congolaises n'est pas imposé (non vérifié) : seuls la longueur
 * et les caractères admis sont contrôlés. L'unicité de l'immatriculation dépend de la base
 * et est vérifiée par le service applicatif.
 */
public final class VehicleRules {
    public static final int REGISTRATION_MAX = 20;
    public static final int MAKE_MAX = 40;
    public static final int MODEL_MAX = 40;
    public static final int NOTES_MAX = 1000;

    private static final Pattern REGISTRATION_CHARS = Pattern.compile("[A-Z0-9 -]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public VehicleRules() {
    }

    public static String normalizeRegistration(String raw) {
        if(raw == null) return null;

        String value = SPACES.matcher(raw.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /** Texte libre : espaces de début et de fin retirés, chaîne vide → null. */
    public static String normalizeText(String raw) {
        if (raw == null) return null;

        String value = raw.strip();
        return value.isEmpty() ? null : value;
    }

    public static Vehicle normalize(Vehicle v) {
        return v.withText(
                normalizeRegistration(v.registration()),
                normalizeText(v.make()),
                normalizeText(v.model()),
                normalizeText(v.notes())
        );
    }

    /**
     * Valide un véhicule déjà normalisé.
     *
     * @param today date du jour (injectée pour rester testable)
     */
    public static List<FieldError> validate(Vehicle v, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();

        if (v.registration() == null) {
            errors.add(FieldError.of("registration", "validation.required"));
        }else {
            if (v.registration().length() > REGISTRATION_MAX) {
                errors.add(FieldError.of("registration", "validation.maxLength", REGISTRATION_MAX));
            }
            if (!REGISTRATION_CHARS.matcher(v.registration()).matches()) {
                errors.add(FieldError.of("registration", "vehicle.registration.invalidChars"));
            }
        }

        requireText(errors, "make", v.make(), MAKE_MAX);
        requireText(errors, "model", v.model(), MODEL_MAX);

        if (v.notes() != null && v.notes().length() > NOTES_MAX) {
            errors.add(FieldError.of("notes", "validation.maxLength", NOTES_MAX));
        }
        if (v.acquisitionDate() != null && v.acquisitionDate().isAfter(today)) {
            errors.add(FieldError.of("acquisitionDate", "validation.notInFuture"));
        }
        requireNotNegative(errors, "purchasePrice", v.purchasePrice());
        requireNotNegative(errors, "repairThreshold", v.repairThreshold());
        if (v.currentMileage() != null && v.currentMileage() < 0) {
            errors.add(FieldError.of("currentMileage", "validation.notNegative"));
        }
        return errors;
    }

    private static void requireText(List<FieldError> errors, String field, String value, int max) {
        if (value == null) {
            errors.add(FieldError.of(field, "validation.required"));
        } else if (value.length() > max) {
            errors.add(FieldError.of(field, "validation.maxLength", max));
        }
    }

    private static void requireNotNegative(List<FieldError> errors, String field, Money value) {
        if (value != null && value.isNegative()) {
            errors.add(FieldError.of(field, "validation.notNegative"));
        }
    }
}
