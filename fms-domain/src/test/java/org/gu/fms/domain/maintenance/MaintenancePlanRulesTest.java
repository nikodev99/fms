package org.gu.fms.domain.maintenance;

import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MaintenancePlanRulesTest {

    private static final LocalDate DONE = LocalDate.of(2026, 9, 1);

    private static MaintenancePlan plan(Long days, Long km, LocalDate lastDate, Long lastKm, boolean active) {
        return new MaintenancePlan(1L, 0, 1L, 1L, days, km, lastDate, lastKm, active, null);
    }

    @Test
    void dueByDate() {
        MaintenancePlan p = plan(90L, null, DONE, null, true);          // échéance 30/11/2026
        assertEquals(LocalDate.of(2026, 11, 30), p.nextDueDate());
        assertEquals(DueStatus.OK, MaintenancePlanRules.status(p, LocalDate.of(2026, 11, 14), null, 15, 500));
        assertEquals(DueStatus.DUE_SOON, MaintenancePlanRules.status(p, LocalDate.of(2026, 11, 15), null, 15, 500));
        assertEquals(DueStatus.DUE_SOON, MaintenancePlanRules.status(p, LocalDate.of(2026, 11, 30), null, 15, 500));
        assertEquals(DueStatus.OVERDUE, MaintenancePlanRules.status(p, LocalDate.of(2026, 12, 1), null, 15, 500));
    }

    @Test
    void dueByMileageAndFirstCriterionWins() {
        MaintenancePlan p = plan(180L, 5_000L, DONE, 40_000L, true);    // 45 000 km ou 28/02/2027
        LocalDate early = LocalDate.of(2026, 10, 1);
        assertEquals(DueStatus.OK, MaintenancePlanRules.status(p, early, 44_499L, 15, 500));
        assertEquals(DueStatus.DUE_SOON, MaintenancePlanRules.status(p, early, 44_500L, 15, 500));
        assertEquals(DueStatus.OVERDUE, MaintenancePlanRules.status(p, early, 45_000L, 15, 500));
    }

    @Test
    void neverDoneOrInactive() {
        assertEquals(DueStatus.NOT_STARTED,
                MaintenancePlanRules.status(plan(90L, 5000L, null, null, true), DONE, 10_000L, 15, 500));
        assertEquals(DueStatus.INACTIVE, MaintenancePlanRules.status(plan(90L, null, DONE, null, false), DONE, null, 15, 500));
    }

    @Test
    void validationAndAdvance() {
        assertEquals("maintenancePlan.intervalRequired",
                MaintenancePlanRules.validate(plan(null, null, null, null, true), DONE).get(0).messageKey());
    }

    private static MaintenanceEvent event(long typeId, LocalDate date, Long km, MaintenanceEvent.MaintenanceEventStatus status) {
        return new MaintenanceEvent(9L, 0, 1L, typeId, MaintenanceEvent.MaintenanceKind.SERVICE, date, km, null, null,
                Money.zero(CurrencyUnit.XAF),
                status, status == MaintenanceEvent.MaintenanceEventStatus.CANCELLED ? "erreur" : null, null);
    }

    @Test
    void lastDoneComesFromLatestRecordedEventOrReference() {
        MaintenancePlan p = plan(90L, 5_000L, DONE, 40_000L, true);
        LocalDate later = DONE.plusDays(20);
        MaintenancePlan withEvent = MaintenancePlanRules.withHistory(p, java.util.List.of(
                event(1, later, 42_000L, MaintenanceEvent.MaintenanceEventStatus.RECORDED),
                event(1, later.plusDays(5), 43_000L, MaintenanceEvent.MaintenanceEventStatus.CANCELLED),
                event(2, later.plusDays(9), 44_000L, MaintenanceEvent.MaintenanceEventStatus.RECORDED)));
        assertEquals(later, withEvent.lastDoneDate(), "annulée et autre type ignorées");
        assertEquals(47_000L, withEvent.nextDueKm());
        MaintenancePlan older = MaintenancePlanRules.withHistory(p, java.util.List.of(
                event(1, DONE.minusDays(1), 39_000L, MaintenanceEvent.MaintenanceEventStatus.RECORDED)));
        assertEquals(DONE, older.lastDoneDate(), "une saisie ancienne ne recule pas l'échéance");
        assertTrue(MaintenancePlanRules.withHistory(p, java.util.List.of()) == p);
        assertFalse(older.nextDueDate() == null);
    }
}
