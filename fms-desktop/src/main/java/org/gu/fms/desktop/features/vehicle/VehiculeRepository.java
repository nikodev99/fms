package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class VehiculeRepository extends JdbcRepository<Vehicle> {
    private final AppSettings settings;

    @Inject
    public VehiculeRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "vehicle";
    }

    @Override
    protected String defaultOrder() {
        return "registration COLLATE NOCASE";
    }

    @Override
    protected RowMapper<Vehicle> mapper() {
        return row -> new Vehicle(
                row.longValue("id"),
                row.longValue("version"),
                row.string("registration"),
                row.string("make"),
                row.string("model"),
                row.enumValue("category", Vehicle.VehicleCategory.class),
                row.enumValue("status", Vehicle.VehicleStatus.class),
                row.localDate("acquisition_date"),
                row.enumValue("acquisition_method", Vehicle.AcquisitionMethod.class),
                row.money("purchase_price", settings.currency()),
                row.longOrNull("current_mileage"),
                row.money("repair_threshold", settings.currency()),
                row.string("notes"));
    }

    @Override
    protected Long idOf(Vehicle entity) {
        return entity.id();
    }

    @Override
    protected long versionOf(Vehicle entity) {
        return entity.version();
    }

    @Override
    protected Map<String, Object> columnsOf(Vehicle entity) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("registration", entity.registration());
        c.put("make", entity.make());
        c.put("model", entity.model());
        c.put("category", entity.category());
        c.put("status", entity.status());
        c.put("acquisition_date", entity.acquisitionDate());
        c.put("acquisition_method", entity.acquisitionMethod());
        c.put("purchase_price", entity.purchasePrice());
        c.put("current_mileage", entity.currentMileage());
        c.put("repair_threshold", entity.repairThreshold());
        c.put("notes", entity.notes());
        return c;
    }

    /** Comparaison insensible à la casse (colonne COLLATE NOCASE). */
    public boolean registrationTaken(String registration, Long excludingId) {
        return existsWithValue("registration", registration, excludingId);
    }
}
