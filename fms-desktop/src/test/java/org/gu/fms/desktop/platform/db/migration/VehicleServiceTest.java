package org.gu.fms.desktop.platform.db.migration;

import org.gu.fms.desktop.features.vehicle.VehicleCategoryRepository;
import org.gu.fms.desktop.features.vehicle.VehicleService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.DataAccessException;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.testing.TestDatabase;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class VehicleServiceTest {

    @TempDir
    Path dir;

    private static final Long TAXI = 1L;          // catégorie système créée par V003

    private TestDatabase db;
    private VehicleService service;
    private VehicleRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        db = TestDatabase.migrated(dir);
        repository = new VehicleRepository(db.jdbc, TestDatabase.CLOCK, AppSettings.CONGO);
        AuditLog audit = new AuditLog(db.jdbc, TestDatabase.CLOCK, new UserSession());
        service = new VehicleService(repository, new VehicleCategoryRepository(db.jdbc, TestDatabase.CLOCK), db.tx,
                audit, TestDatabase.CLOCK, AppSettings.CONGO);
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    private static Vehicle newVehicle(String registration) {
        return new Vehicle(null, 0, registration, "Toyota", "Corolla", TAXI, Vehicle.VehicleStatus.ACTIVE,
                LocalDate.of(2026, 1, 15), Vehicle.AcquisitionMethod.LOAN, Money.of(8_000_000, CurrencyUnit.XAF), 12_500L,
                Money.of(2_500_000, CurrencyUnit.XAF), null);
    }

    @Test
    void createsNormalizedVehicleAndAuditsIt() {
        Vehicle saved = service.save(newVehicle("  bz 1234 a "));
        assertNotNull(saved.id());
        assertEquals("BZ 1234 A", saved.registration());
        assertEquals(0, saved.version());

        Vehicle loaded = repository.findById(saved.id()).orElseThrow();
        assertEquals(saved, loaded);
        assertEquals(Money.of(8_000_000, CurrencyUnit.XAF), loaded.purchasePrice());
        assertEquals(1, db.jdbc.queryLong(
                "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'vehicle' AND action = 'CREATE' AND entity_id = ?",
                saved.id()));
    }

    @Test
    void registrationMustBeUniqueIgnoringCase() {
        service.save(newVehicle("BZ 1234 A"));
        ValidationException e = assertThrows(ValidationException.class, () -> service.save(newVehicle("bz 1234 a")));
        assertEquals("registration", e.errors().get(0).field());
        assertEquals("vehicle.registration.taken", e.errors().get(0).messageKey());
        assertEquals(1, repository.count());
    }

    @Test
    void updateIncrementsVersionAndAuditsOnlyChanges() {
        Vehicle saved = service.save(newVehicle("BZ 1"));
        Vehicle updated = service.save(saved.withStatus(Vehicle.VehicleStatus.UNDER_MAINTENANCE));
        assertEquals(1, updated.version());
        assertEquals(Vehicle.VehicleStatus.UNDER_MAINTENANCE, repository.findById(saved.id()).orElseThrow().status());

        String changes = db.jdbc.queryOne("SELECT changes FROM audit_log WHERE action = 'UPDATE'",
                row -> row.string("changes")).orElseThrow();
        assertEquals("{\"status\":{\"from\":\"ACTIVE\",\"to\":\"UNDER_MAINTENANCE\"}}", changes);
    }

    @Test
    void staleUpdateIsRefused() {
        Vehicle saved = service.save(newVehicle("BZ 1"));
        service.save(saved.withStatus(Vehicle.VehicleStatus.IMMOBILISED));          // version 0 → 1
        assertThrows(StaleDataException.class, () -> service.save(saved.withStatus(Vehicle.VehicleStatus.SOLD)));
        assertEquals(Vehicle.VehicleStatus.IMMOBILISED, repository.findById(saved.id()).orElseThrow().status());
    }

    @Test
    void invalidVehicleWritesNothing() {
        Vehicle invalid = new Vehicle(null, 0, " ", "Toyota", "", TAXI, Vehicle.VehicleStatus.ACTIVE,
                null, null, null, null, null, null);
        ValidationException e = assertThrows(ValidationException.class, () -> service.save(invalid));
        assertEquals(2, e.errors().size());
        assertEquals(0, repository.count());
        assertEquals(0, db.jdbc.queryLong("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void auditLogCannotBeAlteredOrDeleted() {
        service.save(newVehicle("BZ 1"));
        DataAccessException update = assertThrows(DataAccessException.class,
                () -> db.jdbc.update("UPDATE audit_log SET user_name = 'x'"));
        assertTrue(update.getMessage().contains("ne peut pas être modifié"));
        assertThrows(DataAccessException.class, () -> db.jdbc.update("DELETE FROM audit_log"));
        assertEquals(1, db.jdbc.queryLong("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void databaseRefusesInconsistentValuesEvenIfCodeIsBypassed() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> db.jdbc.update(
                "INSERT INTO vehicle (registration, make, model, category_id, status, purchase_price, created_at, updated_at)"
                        + " VALUES ('X', 'a', 'b', 1, 'ACTIVE', -5, 'now', 'now')"));
        assertTrue(e.isIntegrityViolation());
    }
}
