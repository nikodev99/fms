package org.gu.fms.desktop.app;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.loan.LoanReversalHandler;
import org.gu.fms.desktop.features.payroll.AdvanceService;
import org.gu.fms.desktop.features.payroll.PayrollReversalHandler;
import org.gu.fms.desktop.features.finance.ReversalHandler;
import org.gu.fms.desktop.features.finance.TransferReversalHandler;
import org.gu.fms.desktop.platform.backup.BackupLocation;
import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.db.migration.MigrationEngine;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.settings.SettingsStore;
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
        bind(BackupLocation.class).toInstance(new BackupLocation(paths.backupDirectory()));
        bind(Clock.class).toInstance(Clock.systemUTC());

        // Contre-passation : chaque module déclare le traitement de ses propres écritures.
        Multibinder<ReversalHandler> reversals = Multibinder.newSetBinder(binder(), ReversalHandler.class);
        reversals.addBinding().to(TransferReversalHandler.class);
        reversals.addBinding().to(PayrollReversalHandler.class);
        reversals.addBinding().to(LoanReversalHandler.class);
        reversals.addBinding().to(AdvanceService.class);
    }

    @Provides
    @Singleton
    Database database() {
        return new Database(paths.databaseFile());
    }

    /**
     * Paramètres régionaux lus en base au premier besoin, donc après l'ouverture et la migration de la base
     * (voir {@code FmsApplication.init}). Un changement prend effet au démarrage suivant.
     */
    @Provides
    @Singleton
    AppSettings appSettings(SettingsStore store) {
        return AppSettings.load(store);
    }

    @Provides
    @Singleton
    RouteRegistry routes() {
        return AppRoutes.registry();
    }

    @Provides
    MigrationEngine migrationEngine(Database database, TransactionManager tx, Jdbc jdbc, Clock clock) {
        return new MigrationEngine(database, tx, jdbc, clock, paths.backupDirectory(),
                MigrationEngine.DEFAULT_LOCATION, AppModule.class.getClassLoader());
    }
}
