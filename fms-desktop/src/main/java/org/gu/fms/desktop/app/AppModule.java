package org.gu.fms.desktop.app;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.db.migration.MigrationEngine;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.nav.RouteRegistry;

import java.time.Clock;

/**
 * Câblage de l'application (Guice).
 * <p>
 * Les classes annotées {@code @Singleton} + {@code @Inject} n'ont pas besoin d'être déclarées ici :
 * Guice les construit à la demande. Ne figurent ici que les objets construits à partir de la configuration.
 */
public final class AppModule extends AbstractModule {
    private final AppPaths paths;

    public AppModule(AppPaths paths) {
        this.paths = paths;
    }

    @Override
    protected void configure() {
        bind(AppPaths.class).toInstance(paths);
        bind(AppSettings.class).toInstance(AppSettings.CONGO);
        bind(Clock.class).toInstance(Clock.systemDefaultZone());
    }

    @Provides
    @Singleton
    Database database() {
        return new Database(paths.databaseFile());
    }

    @Provides
    @Singleton
    RouteRegistry routes() {
        return AppRoutes.registry();
    }

    @Provides
    MigrationEngine migrationEngine(Database database, TransactionManager tx, Jdbc jdbc, Clock clock) {
        return new MigrationEngine(
                database, tx, jdbc, clock, paths.backupDirectory(),
                MigrationEngine.DEFAULT_LOCATION, AppModule.class.getClassLoader()
        );
    }
}
