package org.gu.fms.domain.payroll;

import org.gu.fms.domain.money.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat du calcul d'un bulletin.
 *
 * @param maxRecoveries plafond de chaque retenue (proposition) ; une retenue demandée ne peut que le diminuer
 * @param warnings      clés de message à afficher (ex. ITS désactivé, barème absent)
 */
public record PayrollResult(
        Money fixedPart,
        Money commission,
        Money gross,
        List<PayslipLine> lines,
        Money employeeContributions,
        Money its,
        BigDecimal itsParts,
        Money netBeforeRecoveries,
        Recoveries maxRecoveries,
        Recoveries recoveries,
        Money netPayable,
        Money employerCharges,
        Money employerCost,
        List<String> warnings) {
}
