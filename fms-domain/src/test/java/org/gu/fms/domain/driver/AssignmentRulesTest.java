package org.gu.fms.domain.driver;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentRulesTest {

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);

    private static DriverAssignment a(Long id, long driver, long vehicle, DriverAssignment.AssignmentRole role, LocalDate start,
                                      LocalDate end) {
        return new DriverAssignment(id, 0, driver, vehicle, role, start, end, null);
    }

    @Test
    void overlapTreatsMissingEndAsOpen() {
        assertTrue(AssignmentRules.overlaps(D1, null, D1.plusYears(5), null));
        assertTrue(AssignmentRules.overlaps(D1, D1.plusDays(10), D1.plusDays(10), null), "le dernier jour compte");
        assertFalse(AssignmentRules.overlaps(D1, D1.plusDays(9), D1.plusDays(10), null));
    }

    @Test
    void onePrimaryPerVehicleButNightAndReplacementAllowed() {
        DriverAssignment primary = a(1L, 10, 100, DriverAssignment.AssignmentRole.PRIMARY, D1, null);
        List<DriverAssignment> existing = List.of(primary);

        DriverAssignment secondPrimary = a(null, 11, 100, DriverAssignment.AssignmentRole.PRIMARY, D1.plusMonths(1), null);
        assertEquals(AssignmentRules.Kind.VEHICLE_HAS_PRIMARY,
                AssignmentRules.conflicts(secondPrimary, existing).get(0).kind());

        assertTrue(AssignmentRules.conflicts(a(null, 11, 100, DriverAssignment.AssignmentRole.NIGHT, D1, null), existing).isEmpty());
        assertTrue(AssignmentRules.conflicts(a(null, 12, 100, DriverAssignment.AssignmentRole.REPLACEMENT, D1, null), existing).isEmpty());

        DriverAssignment afterEnd = a(null, 11, 100, DriverAssignment.AssignmentRole.PRIMARY, D1.plusMonths(1), null);
        List<DriverAssignment> ended = List.of(a(1L, 10, 100, DriverAssignment.AssignmentRole.PRIMARY, D1, D1.plusDays(30)));
        assertTrue(AssignmentRules.conflicts(afterEnd, ended).isEmpty(), "le principal précédent est terminé");
    }

    @Test
    void driverCannotBePrimaryOnTwoVehiclesNorTwiceOnSameVehicle() {
        List<DriverAssignment> existing = List.of(a(1L, 10, 100, DriverAssignment.AssignmentRole.PRIMARY, D1, null));
        assertEquals(AssignmentRules.Kind.DRIVER_ALREADY_PRIMARY,
                AssignmentRules.conflicts(a(null, 10, 200, DriverAssignment.AssignmentRole.PRIMARY, D1, null), existing).get(0).kind());
        assertEquals(AssignmentRules.Kind.DUPLICATE,
                AssignmentRules.conflicts(a(null, 10, 100, DriverAssignment.AssignmentRole.NIGHT, D1, null), existing).get(0).kind());
        assertTrue(AssignmentRules.conflicts(a(1L, 10, 100, DriverAssignment.AssignmentRole.PRIMARY, D1, D1.plusDays(5)), existing)
                .isEmpty(), "une affectation ne se gêne pas elle-même");
    }

    @Test
    void endCannotPrecedeStart() {
        DriverAssignment bad = a(null, 10, 100, DriverAssignment.AssignmentRole.PRIMARY, D1, D1.minusDays(1));
        assertEquals("assignment.endBeforeStart", AssignmentRules.validate(bad).get(0).messageKey());
        assertFalse(bad.withIdentity(1, 0).isActiveOn(D1.minusDays(1)));
    }
}
