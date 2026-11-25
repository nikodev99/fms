package org.gu.fms.domain.collection;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.gu.fms.domain.common.Percent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectionCalendarTest {

    private static final CollectionPolicy POLICY =
            new CollectionPolicy(EnumSet.of(DayOfWeek.SUNDAY), Percent.of("95"), Percent.of("50"));
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);    // lundi
    private static final LocalDate SUN = LocalDate.of(2026, 11, 8);    // dimanche
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static CollectionCalendar.DayInput day(LocalDate d, long expected, long received, boolean exempt) {
        return new CollectionCalendar.DayInput(d, expected, received, true, exempt, received > 0 ? 1 : 0);
    }

    private static CalendarState state(CollectionCalendar.DayInput d) {
        return CollectionCalendar.classify(d, TODAY, POLICY);
    }

    @Test
    void cellsAreColouredFromExpectedReceivedThresholdAndSituation() {
        assertEquals(CalendarState.FULL, state(day(MON, 25_000, 25_000, false)));
        assertEquals(CalendarState.PARTIAL, state(day(MON, 25_000, 12_500, false)), "50 % = seuil : partiel");
        assertEquals(CalendarState.MISSED, state(day(MON, 25_000, 12_499, false)));
        assertEquals(CalendarState.MISSED, state(day(MON, 25_000, 0, false)));
        assertEquals(CalendarState.EXEMPT, state(day(MON, 0, 0, true)));
        assertEquals(CalendarState.REST, state(day(SUN, 0, 0, false)));
        assertEquals(CalendarState.BONUS, state(day(SUN, 0, 10_000, false)), "versement un jour de repos : compte");
        assertEquals(CalendarState.PENDING, state(day(TODAY, 25_000, 10_000, false)));
        assertEquals(CalendarState.FULL, state(day(TODAY, 25_000, 25_000, false)));
        assertEquals(CalendarState.NONE, state(day(TODAY.plusDays(1), 25_000, 0, false)), "jour à venir");
        assertEquals(CalendarState.NONE, CollectionCalendar.classify(
                new CollectionCalendar.DayInput(MON, 0, 0, false, false, 0), TODAY, POLICY), "pas d'affectation");
    }

    @Test
    void restDayPaymentOffsetsOwnShortagesButNeverAnotherDriver() {
        // Jean : manque 10 000 lundi, verse 10 000 dimanche → aucun manquant. Paul : manque 5 000.
        CollectionCalendar.DriverRow jean = CollectionCalendar.row(1L, "JEAN", List.of(
                day(MON, 25_000, 15_000, false), day(SUN, 0, 10_000, false)), TODAY, POLICY);
        CollectionCalendar.DriverRow paul = CollectionCalendar.row(2L, "PAUL", List.of(
                day(MON, 25_000, 20_000, false), day(MON.plusDays(1), 0, 0, true)), TODAY, POLICY);
        assertEquals(0, jean.missing());
        assertEquals(10_000L, jean.rateBp(), "100 %");
        assertEquals(1, jean.workedDays());
        assertEquals(5_000, paul.missing());
        assertEquals(1, paul.exemptDays());
        assertTrue(paul.belowObjective());
        assertFalse(jean.belowObjective());

        CollectionCalendar.Summary s = CollectionCalendar.summarize(List.of(jean, paul));
        assertEquals(50_000, s.expected());
        assertEquals(45_000, s.received());
        assertEquals(5_000, s.missing(), "le surplus de Jean ne couvre pas Paul");
        assertEquals(9_000L, s.rateBp());
        assertEquals(1, s.driversBelowObjective());
        assertEquals(1, s.exemptDays());
    }

    @Test
    void todayIsNotCountedAsShortageWhileTheDayIsOpen() {
        CollectionCalendar.DriverRow r = CollectionCalendar.row(1L, "JEAN", List.of(day(TODAY, 25_000, 5_000, false)),
                TODAY, POLICY);
        assertEquals(0, r.expected());
        assertEquals(0, r.missing());
        assertNull(r.rateBp());
    }

    @Test
    void policyValidation() {
        assertTrue(POLICY.validate().isEmpty());
        CollectionPolicy redAbove = new CollectionPolicy(EnumSet.of(DayOfWeek.SUNDAY), Percent.of("80"),
                Percent.of("90"));
        assertEquals("collectionPolicy.red.aboveObjective", redAbove.validate().get(0).messageKey());
        CollectionPolicy everyDay = new CollectionPolicy(EnumSet.allOf(DayOfWeek.class), Percent.of("95"),
                Percent.of("50"));
        assertEquals("collectionPolicy.restDays.all", everyDay.validate().get(0).messageKey());
    }
}
