package org.gu.fms.desktop.features.finance;

import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionSource;

import java.util.Set;

/**
 * Contre-passation d'une transaction produite par un module : le module annule aussi ses propres effets
 * (bulletin repassé à « validé », avance retirée du compte chauffeur, virement annulé…).
 * Chaque module déclare son gestionnaire (liaison Guice dans {@code AppModule}) ; la trésorerie ne dépend
 * d'aucun module.
 */
public interface ReversalHandler {

    /** Origines traitées. */
    Set<TransactionSource> sources();

    /** Contre-passe dans la transaction SQL de l'appelant ; renvoie la contre-passation de {@code original}. */
    FinancialTransaction reverse(FinancialTransaction original, String reason);
}
