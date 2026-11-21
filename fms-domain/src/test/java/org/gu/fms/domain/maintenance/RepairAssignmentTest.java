package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepairAssessmentTest {

    private static final Money ZERO = Money.zero(CurrencyUnit.XAF);
    private static final Percent WARN = Percent.of("80");

    private static MaintenanceEvent event(MaintenanceEvent.MaintenanceKind kind, long cost, MaintenanceEvent.MaintenanceEventStatus status) {
        return new MaintenanceEvent(1L, 0, 1L, 1L, kind, LocalDate.of(2026, 10, 1), null, null, null,
                Money.of(cost, CurrencyUnit.XAF), status, status == MaintenanceEvent.MaintenanceEventStatus.CANCELLED ? "x" : null, null);
    }

    @Test
    void onlyRecordedRepairsCount() {
        List<MaintenanceEvent> events = List.of(
                event(MaintenanceEvent.MaintenanceKind.REPAIR, 600_000, MaintenanceEvent.MaintenanceEventStatus.RECORDED),
                event(MaintenanceEvent.MaintenanceKind.SERVICE, 900_000, MaintenanceEvent.MaintenanceEventStatus.RECORDED),
                event(MaintenanceEvent.MaintenanceKind.REPAIR, 500_000, MaintenanceEvent.MaintenanceEventStatus.CANCELLED),
                event(MaintenanceEvent.MaintenanceKind.REPAIR, 250_000, MaintenanceEvent.MaintenanceEventStatus.RECORDED));
        RepairAssessment r = RepairAssessment.of(events, ZERO, Money.of(1_000_000, CurrencyUnit.XAF), WARN);
        assertEquals(Money.of(850_000, CurrencyUnit.XAF), r.total());
        assertEquals(85, r.usedPercent());
        assertEquals(RepairAssessment.Level.APPROACHING, r.level());
    }

    @Test
    void levels() {
        Money threshold = Money.of(1_000_000, CurrencyUnit.XAF);
        assertEquals(RepairAssessment.Level.OK, RepairAssessment.assess(Money.of(799_999, CurrencyUnit.XAF), threshold, WARN).level());
        assertEquals(RepairAssessment.Level.APPROACHING, RepairAssessment.assess(Money.of(800_000, CurrencyUnit.XAF), threshold, WARN).level());
        assertEquals(RepairAssessment.Level.REACHED, RepairAssessment.assess(threshold, threshold, WARN).level());
        assertEquals(RepairAssessment.Level.NO_THRESHOLD, RepairAssessment.assess(threshold, null, WARN).level());
    }
}
