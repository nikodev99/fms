package org.gu.fms.desktop.features.finance;

import org.gu.fms.domain.finance.CashClosing;
import org.gu.fms.domain.finance.ClosingFigures;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Ligne de l'écran Clôture : un compte pour une journée, ses chiffres et sa clôture éventuelle.
 *
 * @param closing      clôture en vigueur pour ce jour ; null si le jour n'est pas clôturé
 * @param lockedAfter  vrai si une clôture plus récente existe (jour verrouillé sans clôture propre)
 * @param latest       vrai si {@code closing} est la dernière clôture du compte (seule rouvrable)
 * @param actual       solde compté saisi (formulaire de clôture)
 * @param explanation  explication de l'écart saisie (formulaire de clôture)
 */
public record ClosingRow(
        FinancialAccount account,
        LocalDate date,
        ClosingFigures figures,
        CashClosing closing,
        boolean lockedAfter,
        boolean latest,
        Money actual,
        String explanation) {

    public boolean closed() {
        return closing != null;
    }

    public ClosingRow withInput(Money newActual, String newExplanation) {
        return new ClosingRow(account, date, figures, closing, lockedAfter, latest, newActual, newExplanation);
    }
}
