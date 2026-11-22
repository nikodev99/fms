package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.finance.TransactionRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.finance.TransactionSource;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.MaintenanceEventRules;
import org.gu.fms.domain.maintenance.MaintenanceType;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Interventions d'entretien et de réparation.
 * <p>
 * Dans la même transaction que l'intervention :
 *  - la nature (entretien / réparation) est recopiée depuis le type ;
 *  - le kilométrage du véhicule est relevé s'il est supérieur à celui connu (jamais diminué), avec sa trace d'audit.
 * Les plans d'entretien ne sont pas modifiés : leur échéance est recalculée depuis les interventions.
 */
@Singleton
public final class MaintenanceEventService extends AuditedCrudService<MaintenanceEvent> {

    static final String AUDIT_TYPE = "maintenance_event";

    private final VehicleRepository vehicles;
    private final MaintenanceTypeRepository types;
    private final TransactionRepository transactions;

    @Inject
    public MaintenanceEventService(MaintenanceEventRepository repository, VehicleRepository vehicles,
                                   MaintenanceTypeRepository types, TransactionRepository transactions,
                                   TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.vehicles = vehicles;
        this.types = types;
        this.transactions = transactions;
    }

    @Override
    protected String auditType() {
        return AUDIT_TYPE;
    }

    @Override
    protected MaintenanceEvent normalize(MaintenanceEvent e) {
        return MaintenanceEventRules.normalize(e);
    }

    @Override
    protected List<FieldError> validate(MaintenanceEvent e, LocalDate today) {
        return MaintenanceEventRules.validate(e, today);
    }

    @Override
    protected void check(MaintenanceEvent e, MaintenanceEvent before, List<FieldError> errors) {
        if (before != null) {
            errors.addAll(MaintenanceEventRules.validateChange(before, e));
            long paid = transactions.paidFor(TransactionSource.MAINTENANCE, before.id());
            if (paid > 0 && !e.counts()) {
                errors.add(FieldError.of("status", "maintenance.event.cancelPaid"));
            } else if (paid > 0 && e.cost() != null && e.cost().minor() < paid) {
                errors.add(FieldError.of("cost", "maintenance.event.costBelowPaid"));
            }
        }
        if (e.vehicleId() != null && vehicles.findById(e.vehicleId()).isEmpty()) {
            errors.add(FieldError.of("vehicleId", "maintenance.vehicle.unknown"));
        }
        if (e.typeId() != null) {
            Optional<MaintenanceType> type = types.findById(e.typeId());
            boolean typeChanged = before == null || !Objects.equals(before.typeId(), e.typeId());
            if (type.isEmpty()) {
                errors.add(FieldError.of("typeId", "maintenance.type.unknown"));
            } else if (typeChanged && !type.get().active()) {
                errors.add(FieldError.of("typeId", "maintenance.type.inactive", type.get().name()));
            }
        }
    }

    /** Nature recopiée du type à la saisie (ou au changement de type), conservée sinon. */
    @Override
    protected MaintenanceEvent prepare(MaintenanceEvent e, MaintenanceEvent before) {
        if (before != null && Objects.equals(before.typeId(), e.typeId())) {
            return e.withKind(before.kind());
        }
        return e.withKind(types.findById(e.typeId()).orElseThrow().kind());
    }

    @Override
    protected void afterWrite(MaintenanceEvent saved, MaintenanceEvent before) {
        if (!saved.counts() || saved.mileage() == null) {
            return;
        }
        Vehicle vehicle = vehicles.findById(saved.vehicleId()).orElseThrow();
        if (vehicle.currentMileage() == null || saved.mileage() > vehicle.currentMileage()) {
            vehicles.updateMileage(vehicle, saved.mileage());
            audit.updated("vehicle", vehicle.id(), vehicles.snapshot(vehicle),
                    vehicles.snapshot(vehicle.withMileage(saved.mileage())), "maintenance_event#" + saved.id());
        }
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
    protected MaintenanceEvent withIdentity(MaintenanceEvent e, long id, long version) {
        return e.withIdentity(id, version);
    }
}
