package org.gu.fms.desktop.features.driver;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Chauffeurs. Un chauffeur n'est jamais supprimé ; il ne peut passer au statut « Parti » qu'une fois
 * ses affectations terminées (pour qu'aucun versement futur ne lui soit attribué par erreur).
 */
@Singleton
public final class DriverService extends AuditedCrudService<Driver> {

    private final DriverRepository drivers;
    private final AssignmentRepository assignments;

    @Inject
    public DriverService(DriverRepository repository, AssignmentRepository assignments, TransactionManager tx,
                         AuditLog audit, Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.drivers = repository;
        this.assignments = assignments;
    }

    @Override
    protected String auditType() {
        return "driver";
    }

    @Override
    protected Driver normalize(Driver d) {
        return DriverRules.normalize(d);
    }

    @Override
    protected List<FieldError> validate(Driver d, LocalDate today) {
        return DriverRules.validate(d, today);
    }

    @Override
    protected void check(Driver d, Driver before, List<FieldError> errors) {
        if (before != null && d.status() == Driver.DriverStatus.LEFT && before.status() != Driver.DriverStatus.LEFT) {
            long open = assignments.countOpenForDriver(d.id(), today());
            if (open > 0) {
                errors.add(FieldError.of("status", "driver.status.openAssignments", open));
            }
        }
    }

    @Override
    protected Long idOf(Driver d) {
        return d.id();
    }

    @Override
    protected long versionOf(Driver d) {
        return d.version();
    }

    @Override
    protected Driver withIdentity(Driver d, long id, long version) {
        return d.withIdentity(id, version);
    }
}