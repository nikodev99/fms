package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

/**
 * Cotisation calculée sur un bulletin. Recopie code, libellé, taux et assiette appliqués : le bulletin reste
 * la preuve du calcul même si les paramètres changent ensuite.
 */
public record PayslipLine(
        String code,
        String name,
        ContributionGroup group,
        ContributionPayer payer,
        Money base,
        Percent rate,
        Money amount) {
}
