package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Cumul des réparations d'un véhicule comparé à son seuil (instructions §4.1).
 * <p>
 * Indicateur de gestion uniquement : atteindre le seuil signale le véhicule pour une évaluation
 * (réparer, garder ou vendre), jamais une instruction automatique de vente.
 *
 * @param usedPercent part du seuil consommée, arrondie à l'unité (null sans seuil)
 */
public record RepairAssessment(Money total, Money threshold, Integer usedPercent, Level level) {

    public enum Level {
        NO_THRESHOLD,
        OK,
        APPROACHING,
        REACHED
    }

    /**
     * @param events      interventions du véhicule ; seules les réparations non annulées comptent
     * @param threshold   seuil du véhicule (null = pas de seuil)
     * @param warnAt      part du seuil à partir de laquelle le véhicule passe « proche du seuil » (paramètre)
     */
    public static RepairAssessment of(Collection<MaintenanceEvent> events, Money zero, Money threshold, Percent warnAt) {
        Objects.requireNonNull(zero, "zero");
        Money total = zero;
        for (MaintenanceEvent e : events) {
            if (e.counts() && e.kind() == MaintenanceEvent.MaintenanceKind.REPAIR && e.cost() != null) {
                total = total.plus(e.cost());
            }
        }
        return assess(total, threshold, warnAt);
    }

    public static RepairAssessment assess(Money total, Money threshold, Percent warnAt) {
        if (threshold == null || !threshold.isPositive()) {
            return new RepairAssessment(total, threshold, null, Level.NO_THRESHOLD);
        }
        int used = BigDecimal.valueOf(total.minor()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(threshold.minor()), 0, RoundingMode.DOWN).intValueExact();
        Level level;
        if (total.compareTo(threshold) >= 0) {
            level = Level.REACHED;
        } else if (BigDecimal.valueOf(total.minor()).compareTo(
                BigDecimal.valueOf(threshold.minor()).multiply(warnAt.asFraction())) >= 0) {
            level = Level.APPROACHING;
        } else {
            level = Level.OK;
        }
        return new RepairAssessment(total, threshold, used, level);
    }
}
