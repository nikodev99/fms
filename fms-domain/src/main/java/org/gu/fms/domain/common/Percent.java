package org.gu.fms.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pourcentage exact, entre 0 et 100, avec au plus deux décimales (ex. 10,03 %).
 *
 * Jamais de {@code double} : la valeur est un {@link BigDecimal}. Stockage en base en points de base
 * (1 % = 100 points de base, 10,03 % = 1003), donc en entier.
 *
 * @param value valeur en pourcent (15 pour 15 %)
 */
public record Percent(BigDecimal value) implements Comparable<Percent> {

    public static final int MAX_DECIMALS = 2;
    public static final Percent ZERO = new Percent(BigDecimal.ZERO);
    public static final Percent HUNDRED = new Percent(BigDecimal.valueOf(100));

    public Percent {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Pourcentage hors de [0, 100] : " + value.toPlainString());
        }
        if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new IllegalArgumentException("Pourcentage trop précis (2 décimales au plus) : " + value.toPlainString());
        }
        value = value.setScale(MAX_DECIMALS, RoundingMode.UNNECESSARY);
    }

    /** « 10.03 » → 10,03 %. */
    public static Percent of(String value) {
        return new Percent(new BigDecimal(value));
    }

    public static Percent ofBasisPoints(long basisPoints) {
        return new Percent(BigDecimal.valueOf(basisPoints, MAX_DECIMALS));
    }

    /** 10,03 % → 1003. */
    public long basisPoints() {
        return value.movePointRight(MAX_DECIMALS).longValueExact();
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public Percent plus(Percent other) {
        return new Percent(value.add(other.value));
    }

    /** Fraction décimale : 20 % → 0,20. */
    public BigDecimal asFraction() {
        return value.movePointLeft(2);
    }

    @Override
    public int compareTo(Percent other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.stripTrailingZeros().toPlainString() + " %";
    }
}
