package org.gu.fms.desktop.features.driver;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.driver.DriverAssignment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class AssignmentRepository extends JdbcRepository<DriverAssignment> {

    @Inject
    public AssignmentRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "driver_assignment";
    }

    @Override
    protected String defaultOrder() {
        return "(end_date IS NULL) DESC, start_date DESC, id DESC";
    }

    @Override
    protected RowMapper<DriverAssignment> mapper() {
        return row -> new DriverAssignment(row.longValue("id"), row.longValue("version"), row.longValue("driver_id"),
                row.longValue("vehicle_id"), row.enumValue("role", DriverAssignment.AssignmentRole.class), row.localDate("start_date"),
                row.localDate("end_date"), row.string("notes"));
    }

    @Override
    protected Long idOf(DriverAssignment a) {
        return a.id();
    }

    @Override
    protected long versionOf(DriverAssignment a) {
        return a.version();
    }

    @Override
    protected Map<String, Object> columnsOf(DriverAssignment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("driver_id", a.driverId());
        m.put("vehicle_id", a.vehicleId());
        m.put("role", a.role());
        m.put("start_date", a.startDate());
        m.put("end_date", a.endDate());
        m.put("notes", a.notes());
        return m;
    }

    /** Affectations susceptibles d'entrer en conflit : même chauffeur ou même véhicule. */
    public List<DriverAssignment> findForDriverOrVehicle(long driverId, long vehicleId) {
        return jdbc.query("SELECT * FROM driver_assignment WHERE driver_id = ? OR vehicle_id = ?", mapper(),
                driverId, vehicleId);
    }

    /** Affectations non terminées à cette date (en cours ou à venir). */
    public long countOpenForDriver(long driverId, LocalDate today) {
        return jdbc.queryLong("SELECT COUNT(*) FROM driver_assignment WHERE driver_id = ?"
                + " AND (end_date IS NULL OR end_date >= ?)", driverId, today);
    }
}
