package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Barème de l'art. 116 G (LF 2026). Résultats attendus calculés à la main, indépendamment du code :
 * 1 200 F forfaitaires sur 0–615 000, puis 10 % / 15 % / 20 % / 30 %.
 */
class ItsScaleTest {

    private static final CurrencyUnit XAF = CurrencyUnit.XAF;
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);
    private static final Money MINIMUM = Money.of(1_200, XAF);

    static List<ItsBand> lf2026() {
        return List.of(
                new ItsBand(null, 0, EFFECTIVE, Money.of(615_000, XAF), null, Money.of(1_200, XAF)),
                new ItsBand(null, 0, EFFECTIVE, Money.of(1_500_000, XAF), Percent.of("10"), null),
                new ItsBand(null, 0, EFFECTIVE, Money.of(3_500_000, XAF), Percent.of("15"), null),
                new ItsBand(null, 0, EFFECTIVE, Money.of(5_000_000, XAF), Percent.of("20"), null),
                new ItsBand(null, 0, EFFECTIVE, null, Percent.of("30"), null));
    }

    private static Money xaf(long v) {
        return Money.of(v, XAF);
    }

    @Test
    void netTaxableDeductsPensionThenTwentyPercentAndRoundsDownToThousand() {
        // 150 000 F/mois : brut annuel 1 800 000 ; retraite salariale 4 % = 72 000
        // (1 800 000 − 72 000) × 0,80 = 1 382 400 → 1 382 000
        assertEquals(xaf(1_382_000), ItsScale.netTaxable(xaf(1_800_000), xaf(72_000), Percent.of("20")));
        assertEquals(xaf(0), ItsScale.netTaxable(xaf(50_000), xaf(60_000), Percent.of("20")));
    }

    @Test
    void scaleAppliedPerPartThenMultiplied() {
        ItsScale scale = ItsScale.of(lf2026(), XAF);
        // 1 part : 1 200 + 10 % × (1 382 000 − 615 000) = 1 200 + 76 700 = 77 900
        assertEquals(xaf(77_900), scale.annualTax(xaf(1_382_000), new BigDecimal("1"), MINIMUM));
        // 3 parts : 460 666,67 par part, 1re tranche seulement → 1 200 × 3 = 3 600
        assertEquals(xaf(3_600), scale.annualTax(xaf(1_382_000), new BigDecimal("3"), MINIMUM));
        // 2,5 parts : 3 000 000 / 2,5 = 1 200 000 → 1 200 + 58 500 = 59 700 ; × 2,5 = 149 250
        assertEquals(xaf(149_250), scale.annualTax(xaf(3_000_000), new BigDecimal("2.5"), MINIMUM));
        // 1 part, 6 000 000 : 1 200 + 88 500 + 300 000 + 300 000 + 300 000 = 989 700
        assertEquals(xaf(989_700), scale.annualTax(xaf(6_000_000), new BigDecimal("1"), MINIMUM));
    }

    @Test
    void minimumTaxAndBandBoundaries() {
        ItsScale scale = ItsScale.of(lf2026(), XAF);
        assertEquals(MINIMUM, scale.annualTax(xaf(0), new BigDecimal("1"), MINIMUM));
        assertEquals(xaf(1_200), scale.annualTax(xaf(615_000), BigDecimal.ONE, MINIMUM), "fin de la 1re tranche");
        assertEquals(xaf(89_700), scale.annualTax(xaf(1_500_000), BigDecimal.ONE, MINIMUM), "1 200 + 88 500");
    }

    @Test
    void refusesInconsistentScale() {
        List<ItsBand> noOpenBand = lf2026().subList(0, 4);
        assertThrows(IllegalArgumentException.class, () -> ItsScale.of(noOpenBand, XAF));
        List<ItsBand> both = List.of(new ItsBand(null, 0, EFFECTIVE, null, Percent.of("10"), xaf(5)));
        assertEquals("its.band.rateOrFixed", ItsScale.validate(both).get(0).messageKey());
    }
}
