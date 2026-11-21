package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.MaintenanceType;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class MaintenanceTypeRepository extends JdbcRepository<MaintenanceType> {

    @Inject
    public MaintenanceTypeRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "maintenance_type";
    }

    @Override
    protected String defaultOrder() {
        return "kind DESC, name COLLATE NOCASE";
    }

    @Override
    protected RowMapper<MaintenanceType> mapper() {
        return row -> new MaintenanceType(row.longValue("id"), row.longValue("version"), row.string("name"),
                row.enumValue("kind", MaintenanceEvent.MaintenanceKind.class), row.longOrNull("default_interval_days"),
                row.longOrNull("default_interval_km"), row.bool("active"));
    }

    @Override
    protected Long idOf(MaintenanceType t) {
        return t.id();
    }

    @Override
    protected long versionOf(MaintenanceType t) {
        return t.version();
    }

    @Override
    protected Map<String, Object> columnsOf(MaintenanceType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.name());
        m.put("kind", t.kind());
        m.put("default_interval_days", t.defaultIntervalDays());
        m.put("default_interval_km", t.defaultIntervalKm());
        m.put("active", t.active());
        return m;
    }
}