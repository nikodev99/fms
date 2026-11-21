package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.maintenance.MaintenancePlan;
import org.gu.fms.domain.maintenance.MaintenancePlanRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Plans d'entretien : un seul plan actif par véhicule et par type. */
@Singleton
public final class MaintenancePlanService extends AuditedCrudService<MaintenancePlan> {

    private final MaintenancePlanRepository plans;
    private final VehicleRepository vehicles;
    private final MaintenanceTypeRepository types;

    @Inject
    public MaintenancePlanService(MaintenancePlanRepository repository, VehicleRepository vehicles,
                                  MaintenanceTypeRepository types, TransactionManager tx, AuditLog audit, Clock clock,
                                  AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.plans = repository;
        this.vehicles = vehicles;
        this.types = types;
    }

    @Override
    protected String auditType() {
        return "maintenance_plan";
    }

    @Override
    protected MaintenancePlan normalize(MaintenancePlan p) {
        return MaintenancePlanRules.normalize(p);
    }

    @Override
    protected List<FieldError> validate(MaintenancePlan p, LocalDate today) {
        return MaintenancePlanRules.validate(p, today);
    }

    @Override
    protected void check(MaintenancePlan p, MaintenancePlan before, List<FieldError> errors) {
        if (p.vehicleId() != null && vehicles.findById(p.vehicleId()).isEmpty()) {
            errors.add(FieldError.of("vehicleId", "maintenance.vehicle.unknown"));
        }
        if (p.typeId() != null && types.findById(p.typeId()).isEmpty()) {
            errors.add(FieldError.of("typeId", "maintenance.type.unknown"));
        }
        if (p.active() && p.vehicleId() != null && p.typeId() != null
                && plans.activePlanExists(p.vehicleId(), p.typeId(), p.id())) {
            errors.add(FieldError.of("typeId", "maintenancePlan.duplicate"));
        }
    }

    @Override
    protected FieldError uniqueViolation(MaintenancePlan p) {
        return FieldError.of("typeId", "maintenancePlan.duplicate");
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
    protected MaintenancePlan withIdentity(MaintenancePlan p, long id, long version) {
        return p.withIdentity(id, version);
    }
}
