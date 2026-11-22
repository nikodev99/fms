package org.gu.fms.domain.payroll;

import org.gu.fms.domain.driver.MaritalStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Nombre de parts du quotient familial de l'ITS — CGI, art. 116 B (loi n° 42-2025 du 31/12/2025,
 * loi de finances 2026, Journal officiel du 5 janvier 2026). Texte vérifié :
 * <pre>
 *  Célibataire, divorcé ou veuf sans enfant à charge ....... 1 part
 *  Marié sans enfant à charge ............................... 2 parts
 *  Célibataire ou divorcé ayant 1 enfant à charge ........... 2 parts   (puis + 0,5 par enfant)
 *  Marié ou veuf ayant 1 enfant à charge .................... 2,5 parts (puis + 0,5 par enfant)
 *  « jusqu’à un maximum de 6,5 parts »
 * </pre>
 * Non couverts (à traiter par une saisie manuelle lors du module Paie) : veuf dans les deux ans suivant
 * le décès du conjoint (2 parts), cas particuliers de l'art. 116 C (division par 1,5, enfant infirme majeur…).
 */
public final class FamilyParts {

    public static final BigDecimal MAX = new BigDecimal("6.5");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private FamilyParts() {
    }

    public static BigDecimal of(MaritalStatus status, int dependentChildren) {
        if (dependentChildren < 0) {
            throw new IllegalArgumentException("Nombre d'enfants négatif : " + dependentChildren);
        }
        MaritalStatus s = status == null ? MaritalStatus.SINGLE : status;
        BigDecimal parts;
        if (dependentChildren == 0) {
            parts = s == MaritalStatus.MARRIED ? BigDecimal.valueOf(2) : BigDecimal.ONE;
        } else {
            BigDecimal base = s == MaritalStatus.MARRIED || s == MaritalStatus.WIDOWED
                    ? BigDecimal.valueOf(2) : new BigDecimal("1.5");
            parts = base.add(HALF.multiply(BigDecimal.valueOf(dependentChildren)));
        }
        return (parts.compareTo(MAX) > 0 ? MAX : parts).setScale(1, RoundingMode.UNNECESSARY);
    }
}
