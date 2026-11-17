package org.gu.fms.desktop.platform.db.migration;

import org.gu.fms.desktop.testing.TestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MigrationEngineTest {
    @TempDir
    Path dir;

    @Test
    void productionScriptsApplyOnEmptyDatabaseWithoutBackup() {
        try (TestDatabase db = TestDatabase.empty(dir)){
            MigrationReport report = db.migrator(MigrationEngine.DEFAULT_LOCATION).migrate();
            assertEquals(0, report.fromVersion());
            assertTrue(report.toVersion() >= 2);
            assertNull(report.backup());
            assertEquals(0, db.jdbc.queryLong("SELECT COUNT(*) FROM vehicle"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void secondRunDoesNothing() throws Exception {
        try (TestDatabase db = TestDatabase.migrated(dir)) {
            MigrationReport report = db.migrator(MigrationEngine.DEFAULT_LOCATION).migrate();
            assertTrue(report.applied().isEmpty());
            assertEquals(report.fromVersion(), report.toVersion());
        }
    }

    @Test
    void upgradeOfExistingDatabaseMakesBackupFirst() {
        try (TestDatabase db = TestDatabase.empty(dir)) {
            db.migrator("db/test-migration/v1").migrate();
            db.jdbc.update("INSERT INTO sample (name) VALUES ('kept')");

            MigrationReport report = db.migrator("db/test-migration/v2").migrate();

            assertEquals(1, report.fromVersion());
            assertEquals(2, report.toVersion());
            assertNotNull(report.backup());
            assertTrue(Files.exists(report.backup()));
            assertEquals(1, db.jdbc.queryLong("SELECT COUNT(*) FROM sample WHERE note IS NULL"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void modifiedScriptIsRefused() {
        try (TestDatabase db = TestDatabase.empty(dir)) {
            db.migrator("db/test-migration/v1").migrate();
            db.jdbc.update("UPDATE schema_version SET checksum = 'altered' WHERE version = 1");
            MigrationException e = assertThrows(MigrationException.class,
                    () -> db.migrator("db/test-migration/v1").migrate());
            assertTrue(e.getMessage().contains("modifié"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void databaseFromNewerApplicationIsRefused() {
        try (TestDatabase db = TestDatabase.empty(dir)) {
            db.migrator("db/test-migration/v2").migrate();
            assertThrows(MigrationException.class, () -> db.migrator("db/test-migration/v1").migrate());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void failingScriptLeavesNoTrace() {
        try (TestDatabase db = TestDatabase.empty(dir)) {
            assertThrows(MigrationException.class, () -> db.migrator("db/test-migration/broken").migrate());
            assertEquals(0, db.jdbc.queryLong("SELECT COUNT(*) FROM schema_version"));
            assertEquals(0, db.jdbc.queryLong(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'half_done'"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
