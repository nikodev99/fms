package org.gu.fms.domain.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static final CurrencyUnit XAF = CurrencyUnit.XAF;
    private static final CurrencyUnit EUR = new CurrencyUnit("EUR", 2);

    @Test
    void addsAndSubtractsExactly() {
        Money a = Money.of(8_000_000, XAF);
        Money b = Money.of(265_714, XAF);
        assertEquals(Money.of(8_265_714, XAF), a.plus(b));
        assertEquals(Money.of(7_734_286, XAF), a.minus(b));
    }

    @Test
    void percentRequiresExplicitRounding() {
        Money collection = Money.of(25_001, XAF);
        // 15 % de 25 001 = 3 750,15 → 3 750 (HALF_UP) ; 3 751 (UP)
        assertEquals(Money.of(3_750, XAF), collection.percent(new BigDecimal("15"), RoundingMode.HALF_UP));
        assertEquals(Money.of(3_751, XAF), collection.percent(new BigDecimal("15"), RoundingMode.UP));
    }

    @Test
    void refusesMixedCurrencies() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(1, XAF).plus(Money.of(1, EUR)));
        assertThrows(IllegalArgumentException.class, () -> Money.of(1, XAF).compareTo(Money.of(1, EUR)));
    }

    @Test
    void majorUnitsRespectCurrencyPrecision() {
        assertEquals(Money.of(1250, EUR), Money.ofMajor(new BigDecimal("12.50"), EUR));
        assertEquals(Money.of(12, XAF), Money.ofMajor(new BigDecimal("12"), XAF));
        assertThrows(IllegalArgumentException.class, () -> Money.ofMajor(new BigDecimal("12.5"), XAF));
        assertEquals(new BigDecimal("12.50"), Money.of(1250, EUR).toMajor());
    }

    @Test
    void detectsOverflowInsteadOfWrappingAround() {
        Money max = Money.of(Long.MAX_VALUE, XAF);
        assertThrows(ArithmeticException.class, () -> max.plus(Money.of(1, XAF)));
    }

    @Test
    void signHelpers() {
        assertTrue(Money.of(-1, XAF).isNegative());
        assertTrue(Money.zero(XAF).isZero());
        assertTrue(Money.of(1, XAF).isPositive());
    }

    @Test
    void currencyCodeMustBeIso() {
        assertThrows(IllegalArgumentException.class, () -> new CurrencyUnit("xaf", 0));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyUnit("XAF", -1));
    }
}
