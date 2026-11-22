package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Tranche du barème annuel de l'ITS (par part). La tranche commence juste après la limite de la précédente
 * (ou à 0 pour la première) et s'arrête à {@code upTo} inclus ; {@code upTo} nul = dernière tranche, sans limite.
 *
 * Exactement un des deux : {@code rate} (taux sur la fraction de la tranche) ou {@code fixedAmount}
 * (montant forfaitaire dû dès que la fraction est positive — cas de la 1re tranche : 1 200 F CFA).
 */
public record ItsBand(
        Long id,
        long version,
        LocalDate effectiveFrom,
        Money upTo,
        Percent rate,
        Money fixedAmount) {

    public boolean isNew() {
        return id == null;
    }

    public ItsBand withIdentity(long newId, long newVersion) {
        return new ItsBand(newId, newVersion, effectiveFrom, upTo, rate, fixedAmount);
    }
}
