package org.gu.fms.desktop.features.driver;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.driver.AssignmentRules;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Affectations chauffeur ↔ véhicule. Règles de chevauchement dans le domaine ({@link AssignmentRules}) ;
 * ici : existence et état du chauffeur et du véhicule, parties figées après création, messages lisibles.
 */
@Singleton
public final class AssignmentService extends AuditedCrudService<DriverAssignment> {

    private final AssignmentRepository assignments;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final Formats formats;

    @Inject
    public AssignmentService(AssignmentRepository repository, DriverRepository drivers, VehicleRepository vehicles,
                             Formats formats, TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.assignments = repository;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.formats = formats;
    }

    @Override
    protected String auditType() {
        return "driver_assignment";
    }

    @Override
    protected DriverAssignment normalize(DriverAssignment a) {
        return AssignmentRules.normalize(a);
    }

    @Override
    protected List<FieldError> validate(DriverAssignment a, LocalDate today) {
        return AssignmentRules.validate(a);
    }

    @Override
    protected void check(DriverAssignment a, DriverAssignment before, List<FieldError> errors) {
        if (a.driverId() == null || a.vehicleId() == null || a.role() == null || a.startDate() == null) {
            return;                                  // déjà signalé par la validation
        }
        if (before != null) {
            if (!Objects.equals(before.driverId(), a.driverId()) || !Objects.equals(before.vehicleId(), a.vehicleId())
                    || before.role() != a.role()) {
                errors.add(FieldError.of(null, "assignment.partiesFixed"));
                return;
            }
        }
        Optional<Driver> driver = drivers.findById(a.driverId());
        Optional<Vehicle> vehicle = vehicles.findById(a.vehicleId());
        if (driver.isEmpty()) {
            errors.add(FieldError.of("driverId", "assignment.driver.unknown"));
        } else if (before == null && !driver.get().status().canBeAssigned()) {
            errors.add(FieldError.of("driverId", "assignment.driver.notActive", driver.get().displayName()));
        }
        if (vehicle.isEmpty()) {
            errors.add(FieldError.of("vehicleId", "assignment.vehicle.unknown"));
        } else if (before == null && (vehicle.get().status() == Vehicle.VehicleStatus.SOLD
                || vehicle.get().status() == Vehicle.VehicleStatus.INACTIVE)) {
            errors.add(FieldError.of("vehicleId", "assignment.vehicle.retired", vehicle.get().registration()));
        }
        for (AssignmentRules.Conflict conflict : AssignmentRules.conflicts(a,
                assignments.findForDriverOrVehicle(a.driverId(), a.vehicleId()))) {
            DriverAssignment other = conflict.with();
            String otherDriver = drivers.findById(other.driverId()).map(Driver::displayName).orElse("?");
            String otherVehicle = vehicles.findById(other.vehicleId()).map(Vehicle::registration).orElse("?");
            String field = conflict.kind() == AssignmentRules.Kind.VEHICLE_HAS_PRIMARY ? "vehicleId" : "driverId";
            errors.add(FieldError.of(field, conflict.kind().messageKey(), otherDriver, otherVehicle,
                    formats.date(other.startDate())));
        }
    }

    @Override
    protected FieldError uniqueViolation(DriverAssignment a) {
        return FieldError.of("vehicleId", "assignment.openPrimaryExists");
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
    protected DriverAssignment withIdentity(DriverAssignment a, long id, long version) {
        return a.withIdentity(id, version);
    }
}
