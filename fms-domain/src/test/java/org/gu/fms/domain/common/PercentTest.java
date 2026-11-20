package org.gu.fms.domain.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PercentTest {

    @Test
    void storesBasisPointsExactly() {
        assertEquals(1003, Percent.of("10.03").basisPoints());
        assertEquals(Percent.of("10.03"), Percent.ofBasisPoints(1003));
        assertEquals(Percent.of("20"), Percent.of("20.00"));
        assertEquals(new BigDecimal("0.2000"), Percent.of("20").asFraction());
    }

    @Test
    void refusesOutOfRangeOrTooPrecise() {
        assertThrows(IllegalArgumentException.class, () -> Percent.of("-1"));
        assertThrows(IllegalArgumentException.class, () -> Percent.of("100.01"));
        assertThrows(IllegalArgumentException.class, () -> Percent.of("2.275"));
    }
}
