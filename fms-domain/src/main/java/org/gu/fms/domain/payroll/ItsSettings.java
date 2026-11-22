package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.money.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Paramètres de l'ITS hors barème (CGI art. 116 et 116 G, 2).
 *
 * @param enabled     ITS retenu en paie (Oui / Non) ; désactivé par défaut, comme dans l'édition Excel
 * @param abatement   abattement forfaitaire après déduction de la retraite salariale (20 % selon l'art. 116)
 * @param minimumTax  impôt annuel minimum (1 200 F CFA selon l'art. 116 G, 2)
 * @param legalSource référence du texte appliqué
 */
public record ItsSettings(boolean enabled, Percent abatement, Money minimumTax, String legalSource) {

    public static final int SOURCE_MAX = 300;

    public List<FieldError> validate() {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "abatement", abatement);
        TextRules.required(errors, "minimumTax", minimumTax);
        TextRules.notNegative(errors, "minimumTax", minimumTax);
        TextRules.requireText(errors, "legalSource", TextRules.normalize(legalSource), SOURCE_MAX);
        return errors;
    }
}
