package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollContributionRulesTest {

    private static PayrollContribution c(Long id, String code, String rate, LocalDate from) {
        return new PayrollContribution(id, 0, code, "Retraite", ContributionGroup.CNSS, ContributionPayer.EMPLOYEE,
                Percent.of(rate), Money.of(1_200_000, CurrencyUnit.XAF), null, true, from, "CLEISS 2023", false);
    }

    @Test
    void normalizesCodeAndRequiresSource() {
        PayrollContribution n = PayrollContributionRules.normalize(c(null, " cnss retraite-salarie ", "4", LocalDate.of(2023, 1, 1)));
        assertEquals("CNSS_RETRAITE_SALARIE", n.code());
        assertTrue(PayrollContributionRules.validate(n).isEmpty());
        PayrollContribution noSource = new PayrollContribution(null, 0, "X", "X", ContributionGroup.OTHER,
                ContributionPayer.EMPLOYER, Percent.of("1"), null, null, true, LocalDate.of(2026, 1, 1), " ", false);
        assertEquals("legalSource", PayrollContributionRules.validate(PayrollContributionRules.normalize(noSource)).get(0).field());
    }

    @Test
    void inForcePicksLatestEffectiveDateNotAfterDay() {
        List<PayrollContribution> all = List.of(
                c(1L, "CNSS_PENSION_EMPLOYEE", "4", LocalDate.of(2023, 1, 1)),
                c(2L, "CNSS_PENSION_EMPLOYEE", "5", LocalDate.of(2027, 1, 1)));
        assertEquals(1L, PayrollContributionRules.inForce(all, "CNSS_PENSION_EMPLOYEE", LocalDate.of(2026, 12, 31)).orElseThrow().id());
        assertEquals(2L, PayrollContributionRules.inForce(all, "CNSS_PENSION_EMPLOYEE", LocalDate.of(2027, 1, 1)).orElseThrow().id());
        assertTrue(PayrollContributionRules.inForce(all, "CNSS_PENSION_EMPLOYEE", LocalDate.of(2022, 1, 1)).isEmpty());
    }
}
