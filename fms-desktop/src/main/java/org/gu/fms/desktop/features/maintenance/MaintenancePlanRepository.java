package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.maintenance.MaintenancePlan;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class MaintenancePlanRepository extends JdbcRepository<MaintenancePlan> {

    @Inject
    public MaintenancePlanRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "maintenance_plan";
    }

    @Override
    protected String defaultOrder() {
        return "active DESC, vehicle_id, type_id";
    }

    @Override
    protected RowMapper<MaintenancePlan> mapper() {
        return row -> new MaintenancePlan(row.longValue("id"), row.longValue("version"), row.longValue("vehicle_id"),
                row.longValue("type_id"), row.longOrNull("interval_days"), row.longOrNull("interval_km"),
                row.localDate("last_done_date"), row.longOrNull("last_done_km"), row.bool("active"), row.string("notes"));
    }

    @Override
    protected Long idOf(MaintenancePlan p) {
        return p.id();
    }

    @Override
    protected long versionOf(MaintenancePlan p) {
        return p.version();
    }

    @Override
    protected Map<String, Object> columnsOf(MaintenancePlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vehicle_id", p.vehicleId());
        m.put("type_id", p.typeId());
        m.put("interval_days", p.intervalDays());
        m.put("interval_km", p.intervalKm());
        m.put("last_done_date", p.lastDoneDate());
        m.put("last_done_km", p.lastDoneKm());
        m.put("active", p.active());
        m.put("notes", p.notes());
        return m;
    }

    public boolean activePlanExists(long vehicleId, long typeId, Long excludingId) {
        return excludingId == null
                ? jdbc.exists("SELECT 1 FROM maintenance_plan WHERE vehicle_id = ? AND type_id = ? AND active = 1",
                vehicleId, typeId)
                : jdbc.exists("SELECT 1 FROM maintenance_plan WHERE vehicle_id = ? AND type_id = ? AND active = 1"
                + " AND id <> ?", vehicleId, typeId, excludingId);
    }
}
