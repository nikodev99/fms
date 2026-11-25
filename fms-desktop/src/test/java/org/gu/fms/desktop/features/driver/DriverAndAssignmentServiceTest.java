package org.gu.fms.desktop.features.driver;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.Driver.DriverStatus;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.driver.DriverAssignment.AssignmentRole;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DriverAndAssignmentServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @TempDir
    Path dir;
    private Fixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    private DriverAssignment assign(Driver d, Vehicle v, AssignmentRole role, LocalDate start, LocalDate end) {
        return f.assignments.save(new DriverAssignment(null, 0, d.id(), v.id(), role, start, end, null, null));
    }

    @Test
    void driverIsNormalizedAndAudited() {
        Driver d = f.driver(" mbemba ", "cg-1");
        assertEquals("MBEMBA", d.lastName());
        assertNull(d.fixedSalary());
        assertEquals(1, f.auditCount("driver"));
    }

    @Test
    void secondPrimaryIsRefusedWithReadableMessageButNightDriverIsAllowed() {
        Vehicle v = f.vehicle("BZ 1");
        Driver jean = f.driver("Mbemba", "A1");
        Driver paul = f.driver("Okemba", "A2");
        assign(jean, v, AssignmentRole.PRIMARY, START, null);

        ValidationException e = assertThrows(ValidationException.class,
                () -> assign(paul, v, AssignmentRole.PRIMARY, START.plusMonths(2), null));
        FieldError err = e.errors().get(0);
        assertEquals("vehicleId", err.field());
        assertEquals("assignment.vehicleHasPrimary", err.messageKey());
        assertEquals("MBEMBA Jean", err.args().get(0));
        assertEquals("01/01/2026", err.args().get(2));

        assign(paul, v, AssignmentRole.NIGHT, START.plusMonths(2), null);
        assertEquals(2, f.assignmentRepo.count());
    }

    @Test
    void endingPrimaryAllowsSuccessorAndPartiesAreFixed() {
        Vehicle v = f.vehicle("BZ 1");
        Driver jean = f.driver("Mbemba", "A1");
        Driver paul = f.driver("Okemba", "A2");
        DriverAssignment first = assign(jean, v, AssignmentRole.PRIMARY, START, null);
        DriverAssignment ended = f.assignments.save(new DriverAssignment(first.id(), first.version(), jean.id(),
                v.id(), AssignmentRole.PRIMARY, START, START.plusMonths(3).minusDays(1), null, null));
        assign(paul, v, AssignmentRole.PRIMARY, START.plusMonths(3), null);

        ValidationException e = assertThrows(ValidationException.class, () -> f.assignments.save(new DriverAssignment(
                ended.id(), ended.version(), paul.id(), v.id(), AssignmentRole.PRIMARY, START, ended.endDate(), null, null)));
        assertEquals("assignment.partiesFixed", e.errors().get(0).messageKey());
    }

    @Test
    void driverCannotLeaveWithOpenAssignmentAndRetiredVehicleCannotBeAssigned() {
        Vehicle v = f.vehicle("BZ 1");
        Driver jean = f.driver("Mbemba", "A1");
        assign(jean, v, AssignmentRole.PRIMARY, START, null);
        Driver current = f.driverRepo.findById(jean.id()).orElseThrow();
        ValidationException e = assertThrows(ValidationException.class,
                () -> f.drivers.save(current.withStatus(DriverStatus.LEFT)));
        assertEquals("driver.status.openAssignments", e.errors().get(0).messageKey());

        Vehicle sold = f.vehicles.save(f.vehicle("BZ 2").withStatus(VehicleStatus.SOLD));
        Driver paul = f.driver("Okemba", "A2");
        ValidationException retired = assertThrows(ValidationException.class,
                () -> assign(paul, sold, AssignmentRole.REPLACEMENT, START, null));
        assertEquals("assignment.vehicle.retired", retired.errors().get(0).messageKey());
    }
}
