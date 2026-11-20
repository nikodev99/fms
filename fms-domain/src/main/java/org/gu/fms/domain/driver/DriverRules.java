package org.gu.fms.domain.driver;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Règles d'un chauffeur, partagées Desktop / SaaS.
 *
 * Accord de rémunération :
 *  - FIXE : salaire fixe obligatoire (&gt; 0), pas de pourcentage ;
 *  - POURCENTAGE : pourcentage obligatoire (&gt; 0), pas de fixe ;
 *  - FIXE + COMMISSION : les deux.
 * Les valeurs qui ne correspondent pas au mode choisi sont effacées par {@link #normalize(Driver)}
 * (pas d'ancien fixe oublié dans un accord au pourcentage).
 */
public final class DriverRules {

    public static final int NAME_MAX = 60;
    public static final int ADDRESS_MAX = 200;
    public static final int ID_NUMBER_MAX = 40;
    public static final int LICENCE_MAX = 30;
    public static final int CNSS_MAX = 30;
    public static final int NOTES_MAX = 1000;
    public static final int CHILDREN_MAX = 30;

    private DriverRules() {
    }

    public static Driver normalize(Driver d) {
        PayScheme scheme = d.payScheme();
        Money fixed = scheme != null && scheme.hasFixedPart() ? d.fixedSalary() : null;
        Percent rate = scheme != null && scheme.hasCommission() ? d.commissionRate() : null;
        String licence = TextRules.normalizeSpaces(d.licenceNumber());
        return d.normalized(
                upper(TextRules.normalizeSpaces(d.lastName())),
                TextRules.normalizeSpaces(d.firstName()),
                TextRules.normalizeSpaces(d.phone()),
                TextRules.normalize(d.address()),
                TextRules.normalizeSpaces(d.idNumber()),
                licence == null ? null : licence.toUpperCase(Locale.ROOT),
                fixed,
                rate,
                d.cnssSubject() ? TextRules.normalizeSpaces(d.cnssNumber()) : null,
                TextRules.normalize(d.notes()));
    }

    /**
     * Valide un chauffeur déjà normalisé.
     *
     * Un permis expiré n'est PAS une erreur : il doit être signalé (alerte), pas empêcher l'enregistrement.
     */
    public static List<FieldError> validate(Driver d, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "lastName", d.lastName(), NAME_MAX);
        TextRules.requireText(errors, "firstName", d.firstName(), NAME_MAX);
        TextRules.required(errors, "phone", d.phone());
        TextRules.phone(errors, "phone", d.phone());
        TextRules.maxLength(errors, "address", d.address(), ADDRESS_MAX);
        TextRules.maxLength(errors, "idNumber", d.idNumber(), ID_NUMBER_MAX);
        TextRules.maxLength(errors, "licenceNumber", d.licenceNumber(), LICENCE_MAX);
        TextRules.maxLength(errors, "cnssNumber", d.cnssNumber(), CNSS_MAX);
        TextRules.maxLength(errors, "notes", d.notes(), NOTES_MAX);
        if (d.hireDate() != null && d.hireDate().isAfter(today)) {
            errors.add(FieldError.of("hireDate", "validation.notInFuture"));
        }

        if (d.payScheme() == null) {
            errors.add(FieldError.of("payScheme", "validation.required"));
        } else {
            if (d.payScheme().hasFixedPart()) {
                TextRules.required(errors, "fixedSalary", d.fixedSalary());
                TextRules.positive(errors, "fixedSalary", d.fixedSalary());
            }
            if (d.payScheme().hasCommission()) {
                TextRules.required(errors, "commissionRate", d.commissionRate());
                if (d.commissionRate() != null && d.commissionRate().isZero()) {
                    errors.add(FieldError.of("commissionRate", "validation.positive"));
                }
            }
        }
        TextRules.notNegative(errors, "dailyTarget", d.dailyTarget());
        TextRules.notNegative(errors, "depositAgreed", d.depositAgreed());
        if (d.dependentChildren() != null && (d.dependentChildren() < 0 || d.dependentChildren() > CHILDREN_MAX)) {
            errors.add(FieldError.of("dependentChildren", "validation.range", 0, CHILDREN_MAX));
        }
        return errors;
    }

    /** Le permis est-il expiré à cette date ? (faux si la date d'expiration n'est pas renseignée) */
    public static boolean licenceExpired(Driver d, LocalDate today) {
        return d.licenceExpiry() != null && d.licenceExpiry().isBefore(today);
    }

    /** Le permis expire-t-il dans les {@code days} jours (sans être déjà expiré) ? */
    public static boolean licenceExpiresWithin(Driver d, LocalDate today, long days) {
        return d.licenceExpiry() != null && !d.licenceExpiry().isBefore(today)
                && !d.licenceExpiry().isAfter(today.plusDays(days));
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
