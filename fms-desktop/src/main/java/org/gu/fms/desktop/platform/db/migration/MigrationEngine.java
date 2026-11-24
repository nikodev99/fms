package org.gu.fms.desktop.platform.db.migration;

import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.TransactionManager;

import java.nio.file.Path;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Met la base au niveau du code, au démarrage.
 * <p>
 * Garanties :
 *  1. chaque script s'exécute dans sa propre transaction : il s'applique entièrement ou pas du tout ;
 *  2. un script déjà appliqué ne peut plus être modifié (empreinte SHA-256 vérifiée à chaque démarrage) ;
 *  3. avant toute mise à jour d'une base existante, une copie complète est faite dans le dossier des sauvegardes ;
 *  4. une base créée par une version plus récente de l'application est refusée (pas de retour en arrière silencieux).
 * </p>
 * Règle d'équipe : on n'édite jamais un script livré ; on en ajoute un nouveau (V00N+1).
 */
public final class MigrationEngine {

    public static final String DEFAULT_LOCATION = "db/migration";

    /** Directive d'un script qui reconstruit une table référencée par d'autres (voir {@link #setForeignKeys}). */
    static final String FOREIGN_KEYS_OFF = "-- fms:foreign-keys-off";

    private static final System.Logger LOG = System.getLogger(MigrationEngine.class.getName());
    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Database database;
    private final TransactionManager tx;
    private final Jdbc jdbc;
    private final Clock clock;
    private final Path backupDirectory;
    private final String location;
    private final ClassLoader loader;

    public MigrationEngine(Database database, TransactionManager tx, Jdbc jdbc, Clock clock,
                           Path backupDirectory, String location, ClassLoader loader) {
        this.database = database;
        this.tx = tx;
        this.jdbc = jdbc;
        this.clock = clock;
        this.backupDirectory = backupDirectory;
        this.location = location;
        this.loader = loader;
    }

    public MigrationReport migrate() {
        List<Migration> available = loadAvailable();
        createHistoryTable();
        Map<Integer, AppliedMigration> applied = loadApplied();
        verifyApplied(available, applied);

        int currentVersion = applied.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Migration> pending = new ArrayList<>();
        for (Migration m : available) {
            if (!applied.containsKey(m.version())) {
                if (m.version() < currentVersion) {
                    throw new MigrationException("Migration " + m.resource() + " antérieure à la version de la base ("
                            + currentVersion + ") mais jamais appliquée : numérotation incohérente");
                }
                pending.add(m);
            }
        }
        if (pending.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Base à jour (version {0})", currentVersion);
            return new MigrationReport(currentVersion, currentVersion, List.of(), null);
        }

        Path backup = null;
        if (!applied.isEmpty()) {
            backup = backupDirectory.resolve("fms-" + BACKUP_STAMP.format(clock.instant())
                    + "-avant-v" + String.format("%03d", pending.get(0).version()) + ".db");
            database.backupTo(backup);
        }
        for (Migration m : pending) {
            apply(m);
        }
        int newVersion = pending.get(pending.size() - 1).version();
        return new MigrationReport(currentVersion, newVersion, List.copyOf(pending), backup);
    }

    /** Version la plus récente connue de cette application (dernier script embarqué). */
    public int latestAvailableVersion() {
        List<Migration> available = loadAvailable();
        return available.isEmpty() ? 0 : available.get(available.size() - 1).version();
    }

    private List<Migration> loadAvailable() {
        TreeMap<Integer, Migration> byVersion = new TreeMap<>();
        for (String resource : ClasspathResources.list(loader, location, ".sql")) {
            Migration m = Migration.fromResource(resource, ClasspathResources.read(loader, resource));
            Migration duplicate = byVersion.put(m.version(), m);
            if (duplicate != null) {
                throw new MigrationException("Deux migrations portent la version " + m.version() + " : "
                        + duplicate.resource() + " et " + m.resource());
            }
        }
        return new ArrayList<>(byVersion.values());
    }

    private void createHistoryTable() {
        tx.runInTransaction(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version      INTEGER PRIMARY KEY,
                            description  TEXT    NOT NULL,
                            script       TEXT    NOT NULL,
                            checksum     TEXT    NOT NULL,
                            installed_at TEXT    NOT NULL,
                            execution_ms INTEGER NOT NULL
                        )""");
            }
        });
    }

    private Map<Integer, AppliedMigration> loadApplied() {
        Map<Integer, AppliedMigration> result = new HashMap<>();
        for (AppliedMigration a : jdbc.query("SELECT version, script, checksum FROM schema_version",
                row -> new AppliedMigration(row.intValue("version"), row.string("script"), row.string("checksum")))) {
            result.put(a.version(), a);
        }
        return result;
    }

    private static void verifyApplied(List<Migration> available, Map<Integer, AppliedMigration> applied) {
        Map<Integer, Migration> byVersion = new HashMap<>();
        available.forEach(m -> byVersion.put(m.version(), m));
        for (AppliedMigration a : applied.values()) {
            Migration m = byVersion.get(a.version());
            if (m == null) {
                throw new MigrationException("La base contient la migration " + a.script()
                        + " que cette version de l'application ne connaît pas : base créée par une version plus récente ?");
            }
            if (!Objects.equals(m.checksum(), a.checksum())) {
                throw new MigrationException("Le script " + m.resource()
                        + " a été modifié après avoir été appliqué. Ajoutez une nouvelle migration au lieu de modifier celle-ci.");
            }
        }
    }

    private void apply(Migration m) {
        long start = System.nanoTime();
        List<String> statements = SqlScriptSplitter.split(m.sql());
        boolean rebuild = m.sql().contains(FOREIGN_KEYS_OFF);
        try {
            if (rebuild) {
                setForeignKeys(false);
            }
            tx.runInTransaction(c -> {
                try (Statement st = c.createStatement()) {
                    for (String sql : statements) {
                        st.execute(sql);
                    }
                    if (rebuild) {
                        verifyForeignKeys(st, m);
                    }
                }
                long ms = (System.nanoTime() - start) / 1_000_000;
                jdbc.update("INSERT INTO schema_version (version, description, script, checksum, installed_at, execution_ms)"
                                + " VALUES (?, ?, ?, ?, ?, ?)",
                        m.version(), m.description(), m.resource(), m.checksum(), Instant.now(clock), ms);
            });
        } catch (RuntimeException e) {
            throw new MigrationException("Échec de la migration " + m.resource() + " : " + e.getMessage(), e);
        } finally {
            if (rebuild) {
                setForeignKeys(true);
            }
        }
        LOG.log(System.Logger.Level.INFO, "Migration appliquée : {0}", m.resource());
    }

    /**
     * Reconstruction de table (procédure SQLite « 12 étapes ») : les clés étrangères sont suspendues le temps du
     * script — PRAGMA sans effet dans une transaction, donc posé avant —, puis toutes revérifiées avant validation.
     */
    private void setForeignKeys(boolean on) {
        tx.withConnection(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = " + (on ? "ON" : "OFF"));
            }
            return null;
        });
    }

    private static void verifyForeignKeys(Statement st, Migration m) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
            if (rs.next()) {
                throw new MigrationException("Migration " + m.resource() + " : référence rompue dans la table "
                        + rs.getString(1) + " (ligne " + rs.getLong(2) + ")");
            }
        }
    }

    private record AppliedMigration(int version, String script, String checksum) {
    }

    /** Résultat d'une exécution. {@code backup} est nul si aucune sauvegarde n'était nécessaire. */
    public record MigrationReport(int fromVersion, int toVersion, List<Migration> applied, Path backup) {
    }
}
