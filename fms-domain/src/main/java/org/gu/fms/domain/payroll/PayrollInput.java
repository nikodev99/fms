package org.gu.fms.domain.payroll;

import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.money.Money;

import java.time.YearMonth;
import java.util.List;

/**
 * Données d'un calcul de paie mensuel.
 *
 * @param collectionsBase versements reçus du chauffeur sur le mois (versements − avoirs) : base de la commission
 * @param adjustment      ajustement du brut (prime +, absence −), avec motif sur le bulletin
 * @param contributions   toutes les lignes de cotisations (le calcul retient celles en vigueur au 1er du mois)
 * @param itsBands        toutes les tranches du barème ITS (le calcul retient le barème en vigueur au 1er du mois)
 * @param requested       retenues demandées par l'utilisateur ; null = retenues proposées
 */
public record PayrollInput(
        YearMonth period,
        Driver driver,
        Money collectionsBase,
        Money adjustment,
        List<PayrollContribution> contributions,
        ItsSettings its,
        List<ItsBand> itsBands,
        DriverBalance balance,
        RecoveryPolicy policy,
        Recoveries requested) {
}
