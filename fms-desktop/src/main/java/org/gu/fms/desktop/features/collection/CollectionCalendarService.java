package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.CollectionCalendar;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.collection.DailyPoint;
import org.gu.fms.domain.collection.DaySituation;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calendrier des versements et indicateurs de recouvrement d'une période (mois du calendrier, période du tableau de
 * bord). Calcul : {@link CollectionCalendar}.
 */
@Singleton
public final class CollectionCalendarService {

    private final CollectionRepository collections;
    private final DailyPointRepository points;
    private final AssignmentRepository assignments;
    private final DriverRepository drivers;
    private final CollectionPolicyService policies;
    private final Clock clock;
    private final AppSettings settings;

    @Inject
    public CollectionCalendarService(CollectionRepository collections, DailyPointRepository points,
                                     AssignmentRepository assignments, DriverRepository drivers,
                                     CollectionPolicyService policies, Clock clock, AppSettings settings) {
        this.collections = collections;
        this.points = points;
        this.assignments = assignments;
        this.drivers = drivers;
        this.policies = policies;
        this.clock = clock;
        this.settings = settings;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    public CollectionPolicy policy() {
        return policies.load();
    }

    /** Première date de l'historique (premier versement ou première affectation) ; aujourd'hui s'il n'y a rien. */
    public LocalDate firstActivity() {
        LocalDate first = today();
        for (DriverAssignment a : assignments.findAll()) {
            if (a.startDate() != null && a.startDate().isBefore(first)) {
                first = a.startDate();
            }
        }
        return first;
    }

    /** Calendrier de la période [from, to] (les jours à venir sont vides). */
    public CollectionCalendar.Month period(LocalDate from, LocalDate to) {
        CollectionPolicy policy = policies.load();
        LocalDate today = today();
        Map<Long, Driver> driverById = drivers.findAll().stream()
                .collect(Collectors.toMap(Driver::id, Function.identity()));
        List<DriverAssignment> overlapping = assignments.findAll().stream()
                .filter(a -> a.startDate() != null && !a.startDate().isAfter(to)
                        && (a.endDate() == null || !a.endDate().isBefore(from)))
                .toList();
        Map<Long, List<DriverAssignment>> byDriver = overlapping.stream()
                .collect(Collectors.groupingBy(DriverAssignment::driverId));
        Map<String, CollectionRepository.DayTotal> received = new HashMap<>();
        Set<Long> driverIds = new LinkedHashSet<>(byDriver.keySet());
        for (CollectionRepository.DayTotal t : collections.dailyTotals(from, to)) {
            received.put(t.driverId() + ":" + t.date(), t);
            driverIds.add(t.driverId());
        }
        Map<String, DailyPoint> pointed = new HashMap<>();
        for (DailyPoint p : points.findBetween(from, to)) {
            pointed.put(p.driverId() + ":" + p.date(), p);
            driverIds.add(p.driverId());
        }

        List<CollectionCalendar.DriverRow> rows = new ArrayList<>();
        for (Long driverId : driverIds) {
            List<DriverAssignment> own = byDriver.getOrDefault(driverId, List.of());
            List<CollectionCalendar.DayInput> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                String key = driverId + ":" + d;
                boolean assigned = false;
                long planned = 0;
                for (DriverAssignment a : own) {
                    if (a.isActiveOn(d)) {
                        assigned = true;
                        planned += a.expectedMinor();
                    }
                }
                if (policy.isRestDay(d)) {
                    planned = 0;
                }
                DailyPoint p = pointed.get(key);
                long expected = p != null ? p.expected().minor() : planned;
                boolean exempt = p != null && p.situation() == DaySituation.EXEMPT;
                CollectionRepository.DayTotal t = received.get(key);
                days.add(new CollectionCalendar.DayInput(d, expected, t == null ? 0 : t.received(), assigned, exempt,
                        t == null ? 0 : t.entries()));
            }
            Driver driver = driverById.get(driverId);
            rows.add(CollectionCalendar.row(driverId, driver == null ? "#" + driverId : driver.displayName(), days,
                    today, policy));
        }
        rows.sort(Comparator.comparing(CollectionCalendar.DriverRow::label, String.CASE_INSENSITIVE_ORDER));
        return new CollectionCalendar.Month(List.copyOf(rows), CollectionCalendar.summarize(rows));
    }
}
