package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.vehicle.VehicleCategory;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class VehicleCategoryRepository extends JdbcRepository<VehicleCategory> {
    @Inject
    public VehicleCategoryRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "vehicle_category";
    }

    @Override
    protected String defaultOrder() {
        return "sort_order, name COLLATE NOCASE";
    }

    @Override
    protected RowMapper<VehicleCategory> mapper() {
        return row -> new VehicleCategory(row.longValue("id"), row.longValue("version"), row.string("name"),
                row.intValue("sort_order"), row.bool("active"), row.bool("is_system"));
    }

    @Override
    protected Long idOf(VehicleCategory c) {
        return c.id();
    }

    @Override
    protected long versionOf(VehicleCategory c) {
        return c.version();
    }

    /** {@code is_system} n'est jamais écrit par l'application : seules les migrations créent des catégories système. */
    @Override
    protected Map<String, Object> columnsOf(VehicleCategory c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.name());
        m.put("sort_order", c.sortOrder());
        m.put("active", c.active());
        return m;
    }

    public long vehiclesUsing(long categoryId) {
        return jdbc.queryLong("SELECT COUNT(*) FROM vehicle WHERE category_id = ?", categoryId);
    }
}
