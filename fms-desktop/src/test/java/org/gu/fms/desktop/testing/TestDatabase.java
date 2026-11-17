package org.gu.fms.desktop.testing;

import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.db.migration.MigrationEngine;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TestDatabase implements AutoCloseable {
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-16T09:00:00Z"), ZoneOffset.UTC);

    public final Database database;
    public final TransactionManager tx;
    public final Jdbc jdbc;
    public final Path path;

    private TestDatabase(Path path) {
        this.path = path;

        this.database = new Database(path.resolve("test.db"));
        this.database.open();

        this.tx = new TransactionManager(database);
        this.jdbc = new Jdbc(tx);
    }

    public static TestDatabase migrated(Path directory) throws Exception {
        TestDatabase db = new TestDatabase(directory);
        try {
            db.migrator(MigrationEngine.DEFAULT_LOCATION).migrate();
            return db;
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }

    }

    public static TestDatabase empty(Path directory) {
        return new TestDatabase(directory);
    }

    public MigrationEngine migrator(String location) {
        return new MigrationEngine(database, tx, jdbc, CLOCK, path.resolve("backups"), location, TestDatabase.class.getClassLoader());
    }

    @Override
    public void close() throws Exception {
        database.close();
    }
}
