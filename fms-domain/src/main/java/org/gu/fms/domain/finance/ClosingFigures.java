package org.gu.fms.domain.finance;

import org.gu.fms.domain.money.Money;

/**
 * Chiffres d'une journée pour un compte : base de la clôture de caisse (instructions §4.5).
 * Les virements sont séparés des entrées et sorties : ils ne sont ni recettes ni dépenses.
 */
public record ClosingFigures(Money opening, Money inflows, Money outflows, Money transfersIn, Money transfersOut) {

    /** Solde théorique en fin de journée. */
    public Money expected() {
        return opening.plus(inflows).minus(outflows).plus(transfersIn).minus(transfersOut);
    }
}
