package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.DailyPoint;
import org.gu.fms.domain.collection.DaySituation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class DailyPointRepository extends JdbcRepository<DailyPoint> {

    private final AppSettings settings;

    @Inject
    public DailyPointRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "daily_point";
    }

    @Override
    protected String defaultOrder() {
        return "point_date DESC, id";
    }

    @Override
    protected RowMapper<DailyPoint> mapper() {
        var c = settings.currency();
        return row -> new DailyPoint(row.longValue("id"), row.longValue("version"), row.localDate("point_date"),
                row.longValue("driver_id"), row.longOrNull("vehicle_id"), row.money("expected", c),
                row.money("received", c), row.money("difference", c), row.enumValue("situation", DaySituation.class),
                row.string("reason"), row.string("validated_by"), row.instant("validated_at"));
    }

    @Override
    protected Long idOf(DailyPoint p) {
        return p.id();
    }

    @Override
    protected long versionOf(DailyPoint p) {
        return p.version();
    }

    @Override
    protected Map<String, Object> columnsOf(DailyPoint p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("point_date", p.date());
        m.put("driver_id", p.driverId());
        m.put("vehicle_id", p.vehicleId());
        m.put("expected", p.expected());
        m.put("received", p.received());
        m.put("difference", p.difference());
        m.put("situation", p.situation());
        m.put("reason", p.reason());
        m.put("validated_by", p.validatedBy());
        m.put("validated_at", p.validatedAt());
        return m;
    }

    public List<DailyPoint> findForDate(LocalDate day) {
        return jdbc.query("SELECT * FROM daily_point WHERE point_date = ?", mapper(), day);
    }

    public List<DailyPoint> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM daily_point WHERE point_date BETWEEN ? AND ?", mapper(), from, to);
    }

    public boolean isPointed(long driverId, LocalDate day) {
        return jdbc.exists("SELECT 1 FROM daily_point WHERE driver_id = ? AND point_date = ?", driverId, day);
    }
}
