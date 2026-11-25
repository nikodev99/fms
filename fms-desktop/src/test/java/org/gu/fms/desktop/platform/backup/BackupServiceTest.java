package org.gu.fms.desktop.platform.backup;

import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.db.migration.MigrationEngine;
import org.gu.fms.desktop.testing.Fixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    @TempDir
    Path dir;
    private Fixture f;
    private BackupService backups;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir.resolve("data"));
        backups = service(Fixture.CLOCK);
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    private BackupService service(Clock clock) {
        return new BackupService(f.db.database, new BackupLocation(dir.resolve("backups")), clock, f.settingsStore,
                () -> f.db.migrator(MigrationEngine.DEFAULT_LOCATION), f.session);
    }

    @Test
    void automaticBackupOncePerDayAndOnlyAutomaticOnesArePruned() throws Exception {
        assertNotNull(backups.createAutomaticIfDue());
        assertNull(backups.createAutomaticIfDue(), "pas deux sauvegardes automatiques dans les 24 h");
        backups.createManual();
        backups.configureAuto(true, 3);
        for (int day = 1; day <= 4; day++) {
            Clock later = Clock.fixed(Fixture.CLOCK.instant().plus(Duration.ofDays(day)), ZoneOffset.UTC);
            assertNotNull(service(later).createAutomaticIfDue());
        }
        List<BackupFile> all = backups.list();
        assertEquals(3, all.stream().filter(b -> b.kind() == BackupKind.AUTOMATIC).count());
        assertEquals(1, all.stream().filter(b -> b.kind() == BackupKind.MANUAL).count());
        assertEquals(Fixture.CLOCK.instant().plus(Duration.ofDays(4)).getEpochSecond(),
                all.get(0).createdAt().getEpochSecond(), "date lue dans le nom du fichier, la plus récente d'abord");
    }

    @Test
    void verifyRefusesForeignOrNewerDatabases() throws Exception {
        Path foreign = dir.resolve("autre.db");
        try (Database other = new Database(foreign)) {
            other.open();
            new Jdbc(new TransactionManager(other)).update("CREATE TABLE x (a INTEGER)");
        }
        assertEquals("backup.invalid.notFms",
                assertThrows(InvalidBackupException.class, () -> backups.verify(foreign)).messageKey());

        BackupFile manual = backups.createManual();
        try (Database copy = new Database(manual.path())) {
            copy.open();
            new Jdbc(new TransactionManager(copy)).update("INSERT INTO schema_version VALUES (999, 'futur', 'V999__futur.sql',"
                    + " 'x', '2030-01-01T00:00:00Z', 1)");
        }
        assertEquals("backup.invalid.newer",
                assertThrows(InvalidBackupException.class, () -> backups.verify(manual.path())).messageKey());
        assertEquals("backup.invalid.missing",
                assertThrows(InvalidBackupException.class, () -> backups.verify(dir.resolve("absent.db"))).messageKey());
    }

    @Test
    void restoreReplacesDatabaseAfterSafetyCopy() throws Exception {
        BackupFile before = backups.createManual();          // aucun véhicule
        f.vehicle("BZ 1");
        BackupFile safety = backups.restore(before.path());
        assertEquals(BackupKind.BEFORE_RESTORE, safety.kind());
        try (Database reopened = new Database(f.db.database.file())) {
            reopened.open();
            Jdbc jdbc = new Jdbc(new TransactionManager(reopened));
            assertEquals(0, jdbc.queryLong("SELECT COUNT(*) FROM vehicle"));
        }
        try (Database saved = new Database(safety.path())) {
            saved.open();
            assertEquals(1, new Jdbc(new TransactionManager(saved)).queryLong("SELECT COUNT(*) FROM vehicle"));
        }
        assertEquals(Instant.class, safety.createdAt().getClass());
    }
}
