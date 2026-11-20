package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.vehicle.VehicleCategory;
import org.gu.fms.domain.vehicle.VehicleCategoryRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Catégories de véhicules (Paramètres › Flotte). Jamais supprimées : désactivées. */
@Singleton
public final class VehicleCategoryService extends AuditedCrudService<VehicleCategory> {

    private final VehicleCategoryRepository categories;

    @Inject
    public VehicleCategoryService(VehicleCategoryRepository repository, TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.categories = repository;
    }

    @Override
    protected String auditType() {
        return "vehicle_category";
    }

    @Override
    protected VehicleCategory normalize(VehicleCategory c) {
        return VehicleCategoryRules.normalize(c);
    }

    @Override
    protected List<FieldError> validate(VehicleCategory c, LocalDate today) {
        return VehicleCategoryRules.validate(c);
    }

    @Override
    protected void check(VehicleCategory c, VehicleCategory before, List<FieldError> errors) {
        if (c.name() != null && categories.existsWithValue("name", c.name(), c.id())) {
            errors.add(FieldError.of("name", "category.name.taken", c.name()));
        }
    }

    @Override
    protected FieldError uniqueViolation(VehicleCategory c) {
        return FieldError.of("name", "category.name.taken", c.name());
    }

    @Override
    protected Long idOf(VehicleCategory c) {
        return c.id();
    }

    @Override
    protected long versionOf(VehicleCategory c) {
        return c.version();
    }

    @Override
    protected VehicleCategory withIdentity(VehicleCategory c, long id, long version) {
        return c.withIdentity(id, version);
    }
}
