package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.services.DeletableService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.maintenance.MaintenanceType;
import org.gu.fms.domain.maintenance.MaintenanceTypeRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Types d'intervention (Paramètres › Flotte). Changer la nature (entretien / réparation) d'un type ne modifie
 * pas les interventions passées : chacune garde la nature enregistrée le jour de sa saisie.
 */
@Singleton
public final class MaintenanceTypeService extends AuditedCrudService<MaintenanceType> implements DeletableService<MaintenanceType> {

    private final MaintenanceTypeRepository types;

    @Inject
    public MaintenanceTypeService(MaintenanceTypeRepository repository, TransactionManager tx, AuditLog audit,
                                  Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.types = repository;
    }

    @Override
    protected String auditType() {
        return "maintenance_type";
    }

    @Override
    protected MaintenanceType normalize(MaintenanceType t) {
        return MaintenanceTypeRules.normalize(t);
    }

    @Override
    protected List<FieldError> validate(MaintenanceType t, LocalDate today) {
        return MaintenanceTypeRules.validate(t);
    }

    @Override
    protected void check(MaintenanceType t, MaintenanceType before, List<FieldError> errors) {
        if (t.name() != null && types.existsWithValue("name", t.name(), t.id())) {
            errors.add(FieldError.of("name", "maintenanceType.name.taken", t.name()));
        }
    }

    @Override
    protected FieldError uniqueViolation(MaintenanceType t) {
        return FieldError.of("name", "maintenanceType.name.taken", t.name());
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
    protected MaintenanceType withIdentity(MaintenanceType t, long id, long version) {
        return t.withIdentity(id, version);
    }
}
