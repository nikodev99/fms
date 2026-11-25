package org.gu.fms.desktop.features.collection;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.collection.*;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.vehicle.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.gu.fms.desktop.testing.Fixture.xaf;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Calendrier des versements sur une vraie base : repos du dimanche (rien d'attendu, versement compté), exemption au
 * point journalier (panne), seuil rouge, journée en cours.
 */
class CollectionCalendarServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    @TempDir
    Path dir;
    private Fixture f;
    private Driver jean;
    private Vehicle car;
    private long cash;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        cash = f.account("Caisse");
        car = f.vehicle("BZ 1");
        jean = f.driver("MBEMBA", "P-1");
        f.assignments.save(new DriverAssignment(null, 0, jean.id(), car.id(), DriverAssignment.AssignmentRole.PRIMARY, MON, null,
                xaf(25_000), null));
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    private void pay(LocalDate day, long amount) {
        f.clock.setDay(day);
        f.collections.save(new CollectionEntry(null, 0, day, null, jean.id(), car.id(), cash, xaf(amount),
                CollectionKind.COLLECTION, CollectionStatus.VALID, null, null, null, null, null));
    }

    @Test
    void restDayExemptionThresholdAndRunningDay() {
        pay(MON, 25_000);                                  // lundi : complet
        pay(MON.plusDays(1), 15_000);                      // mardi : partiel (60 %)
        pay(MON.plusDays(2), 5_000);                       // mercredi : sous le seuil de 50 %
        f.clock.setDay(MON.plusDays(4));                   // vendredi : panne, point exempté
        f.dailyPoints.validate(MON.plusDays(4), Map.of(jean.id(), new PointRow(MON.plusDays(4), jean.id(), car.id(),
                xaf(25_000), xaf(0), true, DaySituation.EXEMPT, "Panne d'embrayage", null)));
        pay(MON.plusDays(6), 10_000);                      // dimanche (repos) : versement compté
        f.clock.setDay(MON.plusDays(7));                   // lundi suivant = aujourd'hui, 0 versé

        assertEquals(0, f.dailyPoints.preview(MON.plusDays(6)).get(0).plannedExpected().minor(),
                "jour de repos : rien d'attendu au point");

        CollectionCalendar.Month month = f.calendar.period(MON, MON.plusDays(8));
        CollectionCalendar.DriverRow row = month.rows().get(0);
        List<CalendarState> states = row.cells().stream().map(CollectionCalendar.Cell::state).toList();
        assertEquals(List.of(CalendarState.FULL, CalendarState.PARTIAL, CalendarState.MISSED, CalendarState.MISSED,
                CalendarState.EXEMPT, CalendarState.MISSED, CalendarState.BONUS, CalendarState.PENDING,
                CalendarState.NONE), states);
        // Attendu : lun–sam sauf vendredi exempté = 5 × 25 000 ; aujourd'hui pas encore compté.
        assertEquals(125_000, row.expected());
        assertEquals(55_000, row.received());
        assertEquals(70_000, row.missing());
        assertEquals(5, row.workedDays());
        assertEquals(1, row.exemptDays());
        assertEquals(4, row.entries());
        assertEquals(4_400L, row.rateBp());
    }
}
