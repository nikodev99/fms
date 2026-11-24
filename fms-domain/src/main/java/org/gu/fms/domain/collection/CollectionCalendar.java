package org.gu.fms.domain.collection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calendrier mensuel des versements et indicateurs de recouvrement (instructions §4.3 et §13).
 *
 * Par chauffeur et par jour :
 * <ul>
 *   <li><b>attendu</b> : celui du point journalier s'il existe (figé), sinon la somme des montants attendus de ses
 *       affectations ce jour-là ; 0 un jour de repos de la flotte ou une journée exemptée ;</li>
 *   <li><b>reçu</b> : versements − avoirs datés de ce jour (y compris ceux saisis après le point).</li>
 * </ul>
 * Manquant d'un chauffeur sur la période = max(0, Σ attendu − Σ reçu) : un versement fait un jour de repos compense
 * ses propres manquants, jamais ceux d'un autre chauffeur. Le manquant de la flotte est la somme des manquants des
 * chauffeurs.
 */
public final class CollectionCalendar {

    private CollectionCalendar() {
    }

    /**
     * Données d'une journée pour un chauffeur.
     *
     * @param assigned le chauffeur avait au moins une affectation ce jour-là
     * @param exempt   journée exemptée au point journalier
     * @param entries  nombre de versements enregistrés (hors avoirs)
     */
    public record DayInput(LocalDate date, long expected, long received, boolean assigned, boolean exempt, int entries) {
    }

    public record Cell(LocalDate date, long expected, long received, CalendarState state) {
    }

    /**
     * Ligne d'un chauffeur.
     *
     * @param workedDays journées où un versement était attendu
     * @param exemptDays journées exemptées (pannes, repos accordés)
     * @param rateBp     taux de recouvrement en points de base ; null si rien n'était attendu
     */
    public record DriverRow(Long driverId, String label, List<Cell> cells, long expected, long received, long missing,
                            int workedDays, int exemptDays, int entries, Long rateBp, boolean belowObjective) {
    }

    /** Totaux de la période (bandeau d'indicateurs). */
    public record Summary(long expected, int workedDays, long received, int entries, long missing, int exemptDays,
                          Long rateBp, int driversBelowObjective) {
    }

    public record Month(List<DriverRow> rows, Summary summary) {
    }

    /** État d'une case. {@code today} : la journée en cours n'est pas jugée tant que l'attendu n'est pas atteint. */
    public static CalendarState classify(DayInput day, LocalDate today, CollectionPolicy policy) {
        Objects.requireNonNull(day, "day");
        if (day.date().isAfter(today)) {
            return CalendarState.NONE;
        }
        if (day.expected() <= 0) {
            if (day.received() > 0) {
                return CalendarState.BONUS;
            }
            if (day.exempt()) {
                return CalendarState.EXEMPT;
            }
            if (policy.isRestDay(day.date()) && day.assigned()) {
                return CalendarState.REST;
            }
            return CalendarState.NONE;
        }
        if (day.received() >= day.expected()) {
            return CalendarState.FULL;
        }
        if (day.date().equals(today)) {
            return CalendarState.PENDING;
        }
        long redBp = policy.redThreshold().basisPoints();
        // reçu ÷ attendu ≥ seuil, sans division : reçu × 10 000 ≥ seuil × attendu
        return Math.multiplyExact(day.received(), 10_000L) >= Math.multiplyExact(redBp, day.expected())
                ? CalendarState.PARTIAL : CalendarState.MISSED;
    }

    /** Ligne d'un chauffeur à partir de ses journées (déjà limitées à la période). */
    public static DriverRow row(Long driverId, String label, List<DayInput> days, LocalDate today,
                                CollectionPolicy policy) {
        List<Cell> cells = new ArrayList<>(days.size());
        long expected = 0;
        long received = 0;
        int worked = 0;
        int exempt = 0;
        int entries = 0;
        for (DayInput d : days) {
            CalendarState state = classify(d, today, policy);
            cells.add(new Cell(d.date(), d.expected(), d.received(), state));
            if (d.date().isAfter(today)) {
                continue;
            }
            // Journée en cours : l'attendu ne compte qu'une fois atteint, pour ne pas afficher un faux manquant.
            boolean countExpected = state != CalendarState.PENDING;
            if (countExpected) {
                expected += d.expected();
                if (d.expected() > 0) {
                    worked++;
                }
            }
            received += d.received();
            entries += d.entries();
            if (state == CalendarState.EXEMPT) {
                exempt++;
            }
        }
        long missing = Math.max(0, expected - received);
        Long rate = CollectionPolicy.rateBp(received, expected);
        return new DriverRow(driverId, label, List.copyOf(cells), expected, received, missing, worked, exempt, entries,
                rate, !policy.meetsObjective(received, expected));
    }

    public static Summary summarize(List<DriverRow> rows) {
        long expected = 0;
        long received = 0;
        long missing = 0;
        int worked = 0;
        int exempt = 0;
        int entries = 0;
        int below = 0;
        for (DriverRow r : rows) {
            expected += r.expected();
            received += r.received();
            missing += r.missing();
            worked += r.workedDays();
            exempt += r.exemptDays();
            entries += r.entries();
            if (r.belowObjective()) {
                below++;
            }
        }
        return new Summary(expected, worked, received, entries, missing, exempt,
                CollectionPolicy.rateBp(received, expected), below);
    }
}
