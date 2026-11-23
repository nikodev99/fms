package org.gu.fms.desktop.features.collection;

import org.gu.fms.domain.collection.DailyPoint;
import org.gu.fms.domain.collection.DaySituation;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Ligne du point journalier : un chauffeur, ce qui était prévu, ce qu'il a versé, la décision (situation, motif)
 * avant validation, puis le point enregistré.
 *
 * @param vehicleUnavailable le véhicule est actuellement immobilisé ou en entretien (exemption suggérée)
 * @param point              point enregistré ; null tant que la journée n'est pas validée pour ce chauffeur
 */
public record PointRow(
        LocalDate date,
        long driverId,
        Long vehicleId,
        Money plannedExpected,
        Money received,
        boolean vehicleUnavailable,
        DaySituation situation,
        String reason,
        DailyPoint point) {

    public boolean validated() {
        return point != null;
    }

    public Money expected() {
        if (point != null) {
            return point.expected();
        }
        return situation == DaySituation.EXEMPT ? Money.zero(plannedExpected.currency()) : plannedExpected;
    }

    public Money shownReceived() {
        return point != null ? point.received() : received;
    }

    public Money difference() {
        return point != null ? point.difference() : shownReceived().minus(expected());
    }

    public DaySituation shownSituation() {
        return point != null ? point.situation() : situation;
    }

    public String shownReason() {
        return point != null ? point.reason() : reason;
    }

    public PointRow decided(DaySituation newSituation, String newReason) {
        return new PointRow(date, driverId, vehicleId, plannedExpected, received, vehicleUnavailable, newSituation,
                newReason, point);
    }
}
