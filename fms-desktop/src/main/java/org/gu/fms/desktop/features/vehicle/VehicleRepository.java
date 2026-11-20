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
public class VehicleRepository extends JdbcRepository<Vehicle> {
    private final AppSettings settings;

    @Inject
    public VehicleRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
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
                row.longOrNull("category_id"),
                row.enumValue("status", Vehicle.VehicleStatus.class),
                row.localDate("acquisition_date"),
                row.enumValue("acquisition_method", Vehicle.AcquisitionMethod.class),
                row.money("purchase_price", settings.currency()),
                row.longOrNull("current_mileage"),
                row.money("repair_threshold", settings.currency()),
                row.string("notes"));
    }

    @Override
    protected Long idOf(Vehicle v) {
        return v.id();
    }

    @Override
    protected long versionOf(Vehicle v) {
        return v.version();
    }

    @Override
    protected Map<String, Object> columnsOf(Vehicle v) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("registration", v.registration());
        c.put("make", v.make());
        c.put("model", v.model());
        c.put("category_id", v.categoryId());
        c.put("status", v.status());
        c.put("acquisition_date", v.acquisitionDate());
        c.put("acquisition_method", v.acquisitionMethod());
        c.put("purchase_price", v.purchasePrice());
        c.put("current_mileage", v.currentMileage());
        c.put("repair_threshold", v.repairThreshold());
        c.put("notes", v.notes());
        return c;
    }

    /** Écrit seulement le kilométrage (relevé lors d'une intervention) ; verrou optimiste comme toute mise à jour. */
    public long updateMileage(Vehicle v, long mileage) {
        return update(v.withMileage(mileage));
    }

    /** Comparaison insensible à la casse (colonne COLLATE NOCASE). */
    public boolean registrationTaken(String registration, Long excludingId) {
        return existsWithValue("registration", registration, excludingId);
    }
}
