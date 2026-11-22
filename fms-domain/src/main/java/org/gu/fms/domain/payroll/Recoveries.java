package org.gu.fms.domain.payroll;

import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

/** Retenues sur salaire, par rubrique du compte chauffeur. */
public record Recoveries(Money advance, Money shortage, Money otherDebt) {

    public static Recoveries zero(CurrencyUnit currency) {
        Money z = Money.zero(currency);
        return new Recoveries(z, z, z);
    }

    public Money total() {
        return advance.plus(shortage).plus(otherDebt);
    }
}
