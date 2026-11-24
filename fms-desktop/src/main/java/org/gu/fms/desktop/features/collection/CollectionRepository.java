package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.CollectionEntry;
import org.gu.fms.domain.collection.CollectionKind;
import org.gu.fms.domain.collection.CollectionStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class CollectionRepository extends JdbcRepository<CollectionEntry> {

    private final AppSettings settings;

    @Inject
    public CollectionRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "collection_entry";
    }

    @Override
    protected String defaultOrder() {
        return "entry_date DESC, recorded_at DESC, id DESC";
    }

    @Override
    protected RowMapper<CollectionEntry> mapper() {
        return row -> new CollectionEntry(row.longValue("id"), row.longValue("version"), row.localDate("entry_date"),
                row.instant("recorded_at"), row.longValue("driver_id"), row.longValue("vehicle_id"),
                row.longValue("account_id"), row.money("amount", settings.currency()),
                row.enumValue("kind", CollectionKind.class), row.enumValue("status", CollectionStatus.class),
                row.longOrNull("origin_id"), row.longOrNull("transaction_id"), row.string("cashier"),
                row.string("reason"), row.string("notes"));
    }

    @Override
    protected Long idOf(CollectionEntry c) {
        return c.id();
    }

    @Override
    protected long versionOf(CollectionEntry c) {
        return c.version();
    }

    @Override
    protected Map<String, Object> columnsOf(CollectionEntry c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry_date", c.date());
        m.put("recorded_at", c.recordedAt());
        m.put("driver_id", c.driverId());
        m.put("vehicle_id", c.vehicleId());
        m.put("account_id", c.accountId());
        m.put("amount", c.amount());
        m.put("kind", c.kind());
        m.put("status", c.status());
        m.put("origin_id", c.originId());
        m.put("transaction_id", c.transactionId());
        m.put("cashier", c.cashier());
        m.put("reason", c.reason());
        m.put("notes", c.notes());
        return m;
    }

    public List<CollectionEntry> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM collection_entry WHERE entry_date BETWEEN ? AND ? ORDER BY " + defaultOrder(),
                mapper(), from, to);
    }

    /** Sommes reçues (versements − avoirs) par chauffeur pour une journée. */
    public Map<Long, Long> receivedByDriver(LocalDate day) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT driver_id, SUM(CASE kind WHEN 'CREDIT_NOTE' THEN -amount ELSE amount END) AS total"
                        + " FROM collection_entry WHERE entry_date = ? GROUP BY driver_id",
                row -> result.put(row.longValue("driver_id"), row.longValue("total")), day);
        return result;
    }

    /** Véhicule du premier versement du jour par chauffeur (pour un chauffeur sans affectation). */
    public Map<Long, Long> firstVehicleByDriver(LocalDate day) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT driver_id, vehicle_id FROM collection_entry WHERE entry_date = ? ORDER BY recorded_at DESC",
                row -> result.put(row.longValue("driver_id"), row.longValue("vehicle_id")), day);
        return result;
    }

    /** Sommes reçues d'un chauffeur sur une période (base de la commission). */
    public long receivedBetween(long driverId, LocalDate from, LocalDate to) {
        return jdbc.queryLong("SELECT COALESCE(SUM(CASE kind WHEN 'CREDIT_NOTE' THEN -amount ELSE amount END), 0)"
                + " FROM collection_entry WHERE driver_id = ? AND entry_date BETWEEN ? AND ?", driverId, from, to);
    }

    /** Chauffeurs ayant versé sur la période. */
    public List<Long> driversBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT DISTINCT driver_id FROM collection_entry WHERE entry_date BETWEEN ? AND ?",
                row -> row.longValue("driver_id"), from, to);
    }

    /** Par chauffeur et par jour : reçu net (versements − avoirs) et nombre de versements, sur une période. */
    public List<DayTotal> dailyTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT driver_id, entry_date, SUM(CASE kind WHEN 'CREDIT_NOTE' THEN -amount ELSE amount END)"
                        + " AS total, SUM(CASE kind WHEN 'COLLECTION' THEN 1 ELSE 0 END) AS entries"
                        + " FROM collection_entry WHERE entry_date BETWEEN ? AND ? GROUP BY driver_id, entry_date",
                row -> new DayTotal(row.longValue("driver_id"), row.localDate("entry_date"), row.longValue("total"),
                        row.intValue("entries")), from, to);
    }

    public record DayTotal(long driverId, LocalDate date, long received, int entries) {
    }
}