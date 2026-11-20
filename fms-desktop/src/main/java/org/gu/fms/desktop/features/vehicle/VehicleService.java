package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.DataAccessException;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.VehicleRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Singleton public final class VehicleService implements CrudService<Vehicle> {
    static final String AUDIT_TYPE = "vehicle";

    private final VehicleRepository repository;
    private final VehicleCategoryRepository categories;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;
    private final AppSettings settings;

    @Inject
    public VehicleService(VehicleRepository repository, VehicleCategoryRepository categories, TransactionManager tx,
                          AuditLog audit, Clock clock, AppSettings settings) {
        this.repository = repository;
        this.categories = categories;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public List<Vehicle> findAll() {
        return repository.findAll();
    }

    @Override
    public Vehicle save(Vehicle input) {
        Vehicle vehicle = VehicleRules.normalize(input);
        LocalDate today = LocalDate.ofInstant(clock.instant(), settings.zone());
        List<FieldError> errors = new ArrayList<>(VehicleRules.validate(vehicle, today));

        try {
            return tx.inTransaction(c -> {
                if (vehicle.registration() != null && repository.registrationTaken(vehicle.registration(), vehicle.id())) {
                    errors.add(FieldError.of("registration", "vehicle.registration.taken", vehicle.registration()));
                }
                checkCategory(vehicle, errors);
                ValidationException.throwIfAny(errors);
                return vehicle.isNew() ? create(vehicle) : update(vehicle);
            });
        } catch (DataAccessException e) {
            if (e.isUniqueViolation()) {   // filet de sécurité : la contrainte UNIQUE de la base a parlé
                throw ValidationException.of(FieldError.of("registration", "vehicle.registration.taken",
                        vehicle.registration()));
            }
            throw e;
        }
    }

    /**
     * La catégorie doit exister ; une catégorie désactivée reste acceptée pour un véhicule qui l'avait déjà,
     * mais n'est plus proposée pour un nouveau véhicule ou un changement de catégorie.
     */
    private void checkCategory(Vehicle vehicle, List<FieldError> errors) {
        if (vehicle.categoryId() == null) {
            return;
        }
        var category = categories.findById(vehicle.categoryId());
        if (category.isEmpty()) {
            errors.add(FieldError.of("categoryId", "vehicle.category.unknown"));
            return;
        }
        boolean unchanged = !vehicle.isNew() && repository.findById(vehicle.id())
                .map(v -> vehicle.categoryId().equals(v.categoryId())).orElse(false);
        if (!category.get().active() && !unchanged) {
            errors.add(FieldError.of("categoryId", "vehicle.category.inactive", category.get().name()));
        }
    }

    private Vehicle create(Vehicle vehicle) {
        long id = repository.insert(vehicle);
        Vehicle saved = vehicle.withIdentity(id, 0);
        audit.created(AUDIT_TYPE, id, repository.snapshot(saved));
        return saved;
    }

    private Vehicle update(Vehicle vehicle) {
        Vehicle before = repository.findById(vehicle.id())
                .orElseThrow(() -> new StaleDataException("vehicle", vehicle.id(), vehicle.version()));
        long newVersion = repository.update(vehicle);
        audit.updated(AUDIT_TYPE, vehicle.id(), repository.snapshot(before), repository.snapshot(vehicle), null);
        return vehicle.withIdentity(vehicle.id(), newVersion);
    }
}
