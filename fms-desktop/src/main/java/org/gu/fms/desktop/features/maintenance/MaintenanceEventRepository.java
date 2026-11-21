package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.MaintenanceEvent.MaintenanceEventStatus;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class MaintenanceEventRepository extends JdbcRepository<MaintenanceEvent> {

    private final AppSettings settings;

    @Inject
    public MaintenanceEventRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "maintenance_event";
    }

    @Override
    protected String defaultOrder() {
        return "event_date DESC, id DESC";
    }

    @Override
    protected RowMapper<MaintenanceEvent> mapper() {
        return row -> new MaintenanceEvent(row.longValue("id"), row.longValue("version"), row.longValue("vehicle_id"),
                row.longValue("type_id"), row.enumValue("kind", MaintenanceEvent.MaintenanceKind.class), row.localDate("event_date"),
                row.longOrNull("mileage"), row.string("description"), row.string("supplier"),
                row.money("cost", settings.currency()), row.enumValue("status", MaintenanceEventStatus.class),
                row.string("cancel_reason"), row.string("notes"));
    }

    @Override
    protected Long idOf(MaintenanceEvent e) {
        return e.id();
    }

    @Override
    protected long versionOf(MaintenanceEvent e) {
        return e.version();
    }

    @Override
    protected Map<String, Object> columnsOf(MaintenanceEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vehicle_id", e.vehicleId());
        m.put("type_id", e.typeId());
        m.put("kind", e.kind());
        m.put("event_date", e.date());
        m.put("mileage", e.mileage());
        m.put("description", e.description());
        m.put("supplier", e.supplier());
        m.put("cost", e.cost());
        m.put("status", e.status());
        m.put("cancel_reason", e.cancelReason());
        m.put("notes", e.notes());
        return m;
    }
}
