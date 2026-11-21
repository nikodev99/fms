package org.gu.fms.desktop.testing;

import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.AssignmentService;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.driver.DriverService;
import org.gu.fms.desktop.features.maintenance.*;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleCategoryRepository;
import org.gu.fms.desktop.features.vehicle.VehicleCategoryService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.features.vehicle.VehicleService;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.PayScheme;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;

import java.nio.file.Path;
import java.time.Clock;

/** Services câblés à la main sur une vraie base SQLite migrée (sans Guice), pour les tests d'intégration. */
public final class Fixture implements AutoCloseable {

    public static final Clock CLOCK = TestDatabase.CLOCK;
    public static final AppSettings SETTINGS = AppSettings.CONGO;

    public final TestDatabase db;
    public final AuditLog audit;
    public final SettingsStore settingsStore;
    public final Formats formats;
    public final VehicleCategoryRepository categoryRepo;
    public final VehicleRepository vehicleRepo;
    public final DriverRepository driverRepo;
    public final AssignmentRepository assignmentRepo;
    public final MaintenanceTypeRepository typeRepo;
    public final MaintenanceEventRepository eventRepo;
    public final MaintenancePlanRepository planRepo;
    public final VehicleCategoryService categories;
    public final VehicleService vehicles;
    public final DriverService drivers;
    public final AssignmentService assignments;
    public final MaintenanceEventService events;
    public final MaintenancePlanService plans;
    public final FleetPreferencesService fleetPrefs;
    public final RepairThresholdService repairs;
    //public final PayrollContributionService contributions;
    //public final ItsBandService itsBands;
    //public final ItsSettingsService itsSettings;
    //public final AllocationRuleService allocations;

    public Fixture(Path dir) throws Exception {
        db = TestDatabase.migrated(dir);
        audit = new AuditLog(db.jdbc, CLOCK, new UserSession());
        settingsStore = new SettingsStore(db.jdbc, db.tx, audit, CLOCK);
        formats = new Formats(SETTINGS);
        categoryRepo = new VehicleCategoryRepository(db.jdbc, CLOCK);
        vehicleRepo = new VehicleRepository(db.jdbc, CLOCK, SETTINGS);
        driverRepo = new DriverRepository(db.jdbc, CLOCK, SETTINGS);
        assignmentRepo = new AssignmentRepository(db.jdbc, CLOCK);
        typeRepo = new MaintenanceTypeRepository(db.jdbc, CLOCK);
        eventRepo = new MaintenanceEventRepository(db.jdbc, CLOCK, SETTINGS);
        planRepo = new MaintenancePlanRepository(db.jdbc, CLOCK);
        categories = new VehicleCategoryService(categoryRepo, db.tx, audit, CLOCK, SETTINGS);
        vehicles = new VehicleService(vehicleRepo, categoryRepo, db.tx, audit, CLOCK, SETTINGS);
        drivers = new DriverService(driverRepo, assignmentRepo, db.tx, audit, CLOCK, SETTINGS);
        assignments = new AssignmentService(assignmentRepo, driverRepo, vehicleRepo, formats, db.tx, audit, CLOCK, SETTINGS);
        events = new MaintenanceEventService(eventRepo, vehicleRepo, typeRepo, db.tx, audit, CLOCK, SETTINGS);
        plans = new MaintenancePlanService(planRepo, vehicleRepo, typeRepo, db.tx, audit, CLOCK, SETTINGS);
        fleetPrefs = new FleetPreferencesService(settingsStore, SETTINGS);
        repairs = new RepairThresholdService(vehicleRepo, eventRepo, fleetPrefs, SETTINGS);
        //contributions = new PayrollContributionService(new PayrollContributionRepository(db.jdbc, CLOCK, SETTINGS), db.tx, audit, CLOCK, SETTINGS);
        //itsBands = new ItsBandService(new ItsBandRepository(db.jdbc, CLOCK, SETTINGS), db.tx, audit, CLOCK, SETTINGS);
        //itsSettings = new ItsSettingsService(settingsStore, SETTINGS);
        //allocations = new AllocationRuleService(new AllocationRuleRepository(db.jdbc, CLOCK, SETTINGS), db.tx, audit, CLOCK, SETTINGS);
    }

    public static Money xaf(long amount) {
        return Money.of(amount, CurrencyUnit.XAF);
    }

    public Vehicle vehicle(String registration) {
        return vehicles.save(new Vehicle(null, 0, registration, "Toyota", "Corolla", 1L, Vehicle.VehicleStatus.ACTIVE, null,
                null, xaf(8_000_000), 10_000L, xaf(1_000_000), null));
    }

    public Driver driver(String lastName, String licence) {
        return drivers.save(new Driver(null, 0, lastName, "Jean", "06 474 50 67", null, null, licence, null, null,
                Driver.DriverStatus.ACTIVE, PayScheme.PERCENTAGE, null, Percent.of("15"),
                xaf(25_000), null, false, null, null, null, null));
    }

    public long auditCount(String entityType) {
        return db.jdbc.queryLong("SELECT COUNT(*) FROM audit_log WHERE entity_type = ?", entityType);
    }

    @Override
    public void close() throws Exception {
        db.close();
    }
}
