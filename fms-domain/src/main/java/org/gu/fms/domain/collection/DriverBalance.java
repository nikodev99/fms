package org.gu.fms.domain.collection;

import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

/**
 * Soldes du compte d'un chauffeur, par rubrique (somme des mouvements).
 * <p>
 * Un solde négatif de la rubrique Manquants signifie que le chauffeur a versé plus que l'attendu : ce crédit
 * compense ses manquants futurs. Le total dû additionne les rubriques plafonnées à zéro (règle de l'édition Excel).
 */
public record DriverBalance(Long driverId, Money shortage, Money advance, Money otherDebt) {

    public static DriverBalance zero(Long driverId, CurrencyUnit currency) {
        Money z = Money.zero(currency);
        return new DriverBalance(driverId, z, z, z);
    }

    public Money shortageDue() {
        return positive(shortage);
    }

    public Money advanceDue() {
        return positive(advance);
    }

    public Money otherDebtDue() {
        return positive(otherDebt);
    }

    public Money totalDue() {
        return shortageDue().plus(advanceDue()).plus(otherDebtDue());
    }

    /** Crédit du chauffeur (excédents non encore compensés). */
    public Money credit() {
        return shortage.isNegative() ? shortage.negate() : Money.zero(shortage.currency());
    }

    public Money of(LedgerRubric rubric) {
        return switch (rubric) {
            case SHORTAGE -> shortage;
            case ADVANCE -> advance;
            case OTHER_DEBT -> otherDebt;
        };
    }

    private static Money positive(Money m) {
        return m.isPositive() ? m : Money.zero(m.currency());
    }
}
