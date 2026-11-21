package org.gu.fms.desktop.features.maintenance;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.maintenance.*;
import org.gu.fms.domain.maintenance.MaintenanceEvent.MaintenanceKind;
import org.gu.fms.domain.maintenance.MaintenanceEvent.MaintenanceEventStatus;
import org.gu.fms.domain.vehicle.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.gu.fms.desktop.testing.Fixture.xaf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaintenanceServiceTest {

    private static final long OIL = 1;        // « Vidange » (SERVICE), seed V004
    private static final long REPAIR = 5;     // « Réparation mécanique » (REPAIR), seed V004
    private static final LocalDate DAY = LocalDate.of(2025, 12, 21);

    @TempDir
    Path dir;
    private Fixture f;
    private Vehicle v;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        v = f.vehicle("BZ 1");      // 10 000 km, seuil 1 000 000
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    private MaintenanceEvent record(long type, LocalDate day, Long km, long cost) {
        return f.events.save(new MaintenanceEvent(null, 0, v.id(), type, MaintenanceKind.SERVICE, day, km, "travaux",
                " Garage  Moungali ", xaf(cost), MaintenanceEventStatus.RECORDED, null, null));
    }

    @Test
    void kindComesFromTypeAndMileageOnlyGoesUp() {
        MaintenanceEvent e = record(REPAIR, DAY, 12_000L, 450_000);
        assertEquals(MaintenanceKind.REPAIR, e.kind(), "la nature saisie est remplacée par celle du type");
        assertEquals("Garage Moungali", e.supplier());
        assertEquals(12_000L, f.vehicleRepo.findById(v.id()).orElseThrow().currentMileage());
        record(OIL, DAY.minusDays(30), 11_000L, 25_000);
        assertEquals(12_000L, f.vehicleRepo.findById(v.id()).orElseThrow().currentMileage(), "jamais diminué");
        assertEquals(1, f.db.jdbc.queryLong("SELECT COUNT(*) FROM audit_log WHERE entity_type = 'vehicle'"
                + " AND action = 'UPDATE' AND reason LIKE 'maintenance_event#%'"));
    }

    @Test
    void cancellingNeedsReasonIsFinalAndLeavesRepairTotal() {
        MaintenanceEvent e = record(REPAIR, DAY, null, 900_000);
        assertEquals(RepairAssessment.Level.APPROACHING, f.repairs.findAll().get(0).assessment().level());

        MaintenanceEvent cancelNoReason = new MaintenanceEvent(e.id(), e.version(), e.vehicleId(), e.typeId(), e.kind(),
                e.date(), e.mileage(), e.description(), e.supplier(), e.cost(), MaintenanceEventStatus.CANCELLED,
                " ", null);
        assertEquals("cancelReason", assertThrows(ValidationException.class,
                () -> f.events.save(cancelNoReason)).errors().get(0).field());

        MaintenanceEvent cancelled = f.events.save(new MaintenanceEvent(e.id(), e.version(), e.vehicleId(),
                e.typeId(), e.kind(), e.date(), e.mileage(), e.description(), e.supplier(), e.cost(),
                MaintenanceEventStatus.CANCELLED, "Saisie en double", null));
        assertEquals(RepairAssessment.Level.OK, f.repairs.findAll().get(0).assessment().level());
        assertEquals(xaf(0), f.repairs.findAll().get(0).assessment().total());

        ValidationException finalErr = assertThrows(ValidationException.class, () -> f.events.save(cancelled));
        assertEquals("maintenanceEvent.cancelledIsFinal", finalErr.errors().get(0).messageKey());
    }

    @Test
    void planDueDateFollowsLatestRecordedEventAndOnePlanPerType() {
        MaintenancePlan plan = f.plans.save(new MaintenancePlan(null, 0, v.id(), OIL, 90L, 5_000L, null, null, true, null));
        assertEquals(DueStatus.NOT_STARTED, MaintenancePlanRules.status(
                MaintenancePlanRules.withHistory(plan, f.eventRepo.findAll()), DAY, 10_000L, 15, 500));

        record(OIL, DAY, 10_000L, 25_000);
        MaintenancePlan effective = MaintenancePlanRules.withHistory(plan, f.eventRepo.findAll());
        assertEquals(DAY.plusDays(90), effective.nextDueDate());
        assertEquals(15_000L, effective.nextDueKm());

        ValidationException dup = assertThrows(ValidationException.class, () -> f.plans.save(
                new MaintenancePlan(null, 0, v.id(), OIL, 30L, null, null, null, true, null)));
        assertEquals("maintenancePlan.duplicate", dup.errors().get(0).messageKey());
    }
}
