package org.gu.fms.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montant monétaire exact.
 * <p>
 * Le montant est stocké en unités mineures dans un {@code long} (pour XAF : en francs).
 * Aucun calcul monétaire ne passe par {@code double}. Toute opération qui produit des fractions
 * (pourcentage, répartition) impose un mode d'arrondi explicite.
 */
public record Money(
        Long minor,
        CurrencyUnit currency
) implements Comparable<Money> {
    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of (long minor, CurrencyUnit currency) {
        return new Money(minor, currency);
    }

    public static Money zero(CurrencyUnit currency) {
        return new Money(0L, currency);
    }

    /** Montant exprimé en unités majeures (ex. "12.50" EUR). Refuse une précision supérieure à celle de la devise. */
    public static Money ofMajor(BigDecimal major, CurrencyUnit currency) {
        Objects.requireNonNull(major, "major");
        BigDecimal minor = major.movePointRight(currency.fractionDigits());
        try {
            return new Money(minor.longValueExact(), currency);
        }catch (ArithmeticException e) {
            throw new IllegalArgumentException("Montant " + major.toPlainString() + " incompatible avec la précision de " + currency.code(), e);
        }
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minor), currency);
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(minor, factor), currency);
    }

    /**
     * Applique un pourcentage (ex. 15 pour 15 %). L'arrondi est obligatoire, car le résultat
     * peut tomber entre deux unités mineures.
     */
    public Money percent(BigDecimal percent, RoundingMode rounding) {
        Objects.requireNonNull(percent, "percent");
        Objects.requireNonNull(rounding, "rounding");
        BigDecimal result = BigDecimal.valueOf(minor)
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, rounding);
        return new Money(result.longValueExact(), currency);
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minor, currency.fractionDigits());
    }

    public boolean isZero() {
        return minor == 0;
    }

    public boolean isNegative() {
        return minor < 0;
    }

    public boolean isPositive() {
        return minor > 0;
    }

    @Override
    public int compareTo(Money m) {
        requireSameCurrency(m);
        return Long.compare(minor, m.minor);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency())) {
            throw new IllegalArgumentException(
                    "Devises différentes : " + currency.code() + " et " + other.currency.code());
        }
    }
}
