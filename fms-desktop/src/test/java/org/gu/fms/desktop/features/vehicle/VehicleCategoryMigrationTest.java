package org.gu.fms.desktop.features.vehicle;

import org.gu.fms.desktop.platform.db.migration.MigrationEngine;
import org.gu.fms.desktop.testing.TestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V003 sur une base réelle en V002 contenant des véhicules : chaque véhicule garde son identifiant et
 * reçoit la catégorie correspondant à son ancienne valeur d'énumération. Les scripts de production sont
 * recopiés à l'exécution (aucune copie figée dans les tests).
 */
class VehicleCategoryMigrationTest {

    @TempDir
    Path dir;

    @Test
    void existingVehiclesKeepTheirCategory() throws IOException {
        Path v2 = copyScripts(List.of("V001__platform.sql", "V002__vehicles.sql"), dir.resolve("v2"));
        Path all = dir.resolve("all");
        try (TestDatabase db = TestDatabase.empty(dir.resolve("db"));
             URLClassLoader upToV2 = loader(v2);
             URLClassLoader latest = loader(copyAllScripts(all))) {
            new MigrationEngine(db.database, db.tx, db.jdbc,
                    TestDatabase.CLOCK, dir.resolve("backups"), "db/migration", upToV2).migrate();
            db.jdbc.update("INSERT INTO vehicle (registration, make, model, category, status, created_at, updated_at)"
                    + " VALUES ('BZ 7', 'Toyota', 'Hiace', 'DELIVERY', 'ACTIVE', 'n', 'n')");
            db.jdbc.update("INSERT INTO vehicle (registration, make, model, category, status, created_at, updated_at)"
                    + " VALUES ('BZ 9', 'Kia', 'Rio', 'PRIVATE_HIRE', 'SOLD', 'n', 'n')");

            var report = new MigrationEngine(db.database,
                    db.tx, db.jdbc, TestDatabase.CLOCK, dir.resolve("backups"), "db/migration", latest).migrate();

            assertEquals(2, report.fromVersion());
            assertTrue(Files.exists(report.backup()), "sauvegarde faite avant la reconstruction de la table");
            List<String> rows = db.jdbc.query("SELECT v.id, v.registration, c.name FROM vehicle v"
                            + " JOIN vehicle_category c ON c.id = v.category_id ORDER BY v.id",
                    r -> r.longValue("id") + " " + r.string("registration") + " " + r.string("name"));
            assertEquals(List.of("1 BZ 7 Livraison", "2 BZ 9 VTC"), rows);
            assertEquals(0, db.jdbc.query("PRAGMA foreign_key_check", r -> 1).size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static URLClassLoader loader(Path root) throws IOException {
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, null);
    }

    /** Tous les scripts de production (dossier du classpath de test). */
    private Path copyAllScripts(Path target) throws IOException {
        Path source;
        try {
            source = Path.of(java.util.Objects.requireNonNull(
                    getClass().getClassLoader().getResource("db/migration")).toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
        List<String> names;
        try (var files = Files.list(source)) {
            names = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".sql")).sorted().toList();
        }
        return copyScripts(names, target);
    }

    private Path copyScripts(List<String> names, Path target) throws IOException {
        Path dirPath = target.resolve("db/migration");
        Files.createDirectories(dirPath);
        for (String name : names) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/migration/" + name)) {
                Files.copy(java.util.Objects.requireNonNull(in, name), dirPath.resolve(name));
            }
        }
        return target;
    }
}
