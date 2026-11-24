package org.gu.fms.desktop.platform.backup;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.DataAccessException;
import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.db.migration.MigrationEngine;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.SettingsStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Sauvegardes et restauration de la base locale (instructions §2.2 : « Backups and restoration »).
 * <p>
 *  - Manuelle : copie cohérente immédiate (VACUUM INTO), même pendant l'utilisation.
 *  - Automatique : au démarrage, si la dernière sauvegarde automatique a plus de 24 h ; on garde les N plus récentes.
 *  - Restauration : le fichier est d'abord contrôlé (base SQLite intègre, créée par FMS, pas plus récente que
 *    cette version) ; la base actuelle est sauvegardée, puis remplacée. L'application doit ensuite redémarrer.
 * <p>
 * Les sauvegardes « avant mise à jour » créées par le moteur de migration apparaissent dans la même liste.
 */
@Singleton
public final class BackupService {

    public static final String KEY_AUTO_ENABLED = "backup.auto.enabled";
    public static final String KEY_AUTO_KEEP = "backup.auto.keep";
    public static final int KEEP_MIN = 3;
    public static final int KEEP_MAX = 60;

    private static final System.Logger LOG = System.getLogger(BackupService.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final Duration AUTO_INTERVAL = Duration.ofHours(24);

    private final Database database;
    private final UserSession session;
    private final BackupLocation location;
    private final Clock clock;
    private final SettingsStore settings;
    private final Provider<MigrationEngine> migrations;

    @Inject
    public BackupService(Database database, BackupLocation location, Clock clock, SettingsStore settings,
                         Provider<MigrationEngine> migrations, UserSession session) {
        this.session = session;
        this.database = database;
        this.location = location;
        this.clock = clock;
        this.settings = settings;
        this.migrations = migrations;
    }

    public Path directory() {
        return location.directory();
    }

    public Path databaseFile() {
        return database.file();
    }

    public long databaseSize() {
        try {
            return Files.size(database.file());
        } catch (IOException e) {
            return -1;
        }
    }

    public boolean autoEnabled() {
        return settings.bool(KEY_AUTO_ENABLED, true);
    }

    public int autoKeep() {
        return (int) Math.max(KEEP_MIN, Math.min(KEEP_MAX, settings.longValue(KEY_AUTO_KEEP, 10)));
    }

    public void configureAuto(boolean enabled, int keep) {
        if (keep < KEEP_MIN || keep > KEEP_MAX) {
            throw new IllegalArgumentException("Nombre de sauvegardes à garder hors limites : " + keep);
        }
        settings.putAll(Map.of(KEY_AUTO_ENABLED, Boolean.toString(enabled), KEY_AUTO_KEEP, Integer.toString(keep)),
                null);
    }

    public BackupFile createManual() {
        return create("manuelle", BackupKind.MANUAL);
    }

    /** Au démarrage : sauvegarde automatique si activée et si la précédente a plus de 24 h. */
    public BackupFile createAutomaticIfDue() {
        if (!autoEnabled()) {
            return null;
        }
        Instant now = clock.instant();
        boolean recent = list().stream().anyMatch(b -> b.kind() == BackupKind.AUTOMATIC
                && Duration.between(b.createdAt(), now).compareTo(AUTO_INTERVAL) < 0);
        if (recent) {
            return null;
        }
        BackupFile created = create("auto", BackupKind.AUTOMATIC);
        prune();
        return created;
    }

    /** Sauvegardes du dossier, plus récentes d'abord. */
    public List<BackupFile> list() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupFile> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith(".db"))::iterator) {
                String name = p.getFileName().toString();
                result.add(new BackupFile(p, kindOf(name), stampOf(name).orElse(Files.getLastModifiedTime(p).toInstant()),
                        Files.size(p)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier des sauvegardes impossible", e);
        }
        result.sort(Comparator.comparing(BackupFile::createdAt).reversed());
        return result;
    }

    /**
     * Contrôle un fichier avant restauration, sans toucher à la base en cours.
     *
     * @return version du schéma du fichier
     * @throws InvalidBackupException avec la raison lisible (clé de message)
     */
    public int verify(Path candidate) {
        if (!Files.isRegularFile(candidate)) {
            throw new InvalidBackupException("backup.invalid.missing");
        }
        if (candidate.toAbsolutePath().normalize().equals(database.file().toAbsolutePath().normalize())) {
            throw new InvalidBackupException("backup.invalid.current");
        }
        Properties readOnly = new Properties();
        readOnly.setProperty("open_mode", "1");            // SQLITE_OPEN_READONLY : le fichier n'est jamais modifié
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + candidate.toAbsolutePath(), readOnly);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new InvalidBackupException("backup.invalid.corrupt");
                }
            }
            int version;
            try (ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_version")) {
                version = rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new InvalidBackupException("backup.invalid.notFms");
            }
            if (version <= 0) {
                throw new InvalidBackupException("backup.invalid.notFms");
            }
            if (version > migrations.get().latestAvailableVersion()) {
                throw new InvalidBackupException("backup.invalid.newer");
            }
            return version;
        } catch (SQLException e) {
            throw new InvalidBackupException("backup.invalid.corrupt");
        }
    }

    /**
     * Remplace la base par {@code candidate}. La base en cours est d'abord sauvegardée, puis FERMÉE :
     * l'application doit redémarrer (au démarrage, les migrations mettent la base restaurée à niveau).
     *
     * @return la sauvegarde de sécurité faite avant le remplacement
     */
    public BackupFile restore(Path candidate) throws Exception {
        session.requireOwner();
        verify(candidate);
        BackupFile safety = create("avant-restauration", BackupKind.BEFORE_RESTORE);
        database.close();
        Path target = database.file();
        try {
            Path temp = target.resolveSibling(target.getFileName() + ".restauration");
            Files.copy(candidate, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(target.resolveSibling(target.getFileName() + "-wal"));
            Files.deleteIfExists(target.resolveSibling(target.getFileName() + "-shm"));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new DataAccessException("Restauration impossible. La base actuelle est intacte dans "
                    + safety.path(), e);
        }
        LOG.log(System.Logger.Level.INFO, "Base restaurée depuis {0} (sauvegarde de sécurité : {1})",
                candidate, safety.path());
        return safety;
    }

    private BackupFile create(String label, BackupKind kind) {
        Path target = directory().resolve("fms-" + STAMP.format(clock.instant()) + "-" + label + ".db");
        if (Files.exists(target)) {        // deux sauvegardes dans la même seconde
            target = directory().resolve("fms-" + STAMP.format(clock.instant()) + "-" + label + "-"
                    + System.nanoTime() + ".db");
        }
        database.backupTo(target);
        try {
            return new BackupFile(target, kind, clock.instant(), Files.size(target));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Garde les N sauvegardes automatiques les plus récentes ; ne touche jamais aux autres. */
    void prune() {
        List<BackupFile> autos = list().stream().filter(b -> b.kind() == BackupKind.AUTOMATIC).toList();
        for (BackupFile old : autos.subList(Math.min(autoKeep(), autos.size()), autos.size())) {
            try {
                Files.deleteIfExists(old.path());
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Suppression impossible : " + old.path(), e);
            }
        }
    }

    /**
     * Date de création lue dans le nom (« fms-20261119-101500-… », UTC) : fiable même si le fichier a été copié
     * ou déplacé (ce qui change sa date de modification).
     */
    static java.util.Optional<Instant> stampOf(String fileName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^fms-(\\d{8}-\\d{6})-").matcher(fileName);
        if (!m.find()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Instant.from(STAMP.parse(m.group(1))));
        } catch (java.time.format.DateTimeParseException e) {
            return java.util.Optional.empty();
        }
    }

    static BackupKind kindOf(String fileName) {
        if (fileName.contains("-auto")) {
            return BackupKind.AUTOMATIC;
        }
        if (fileName.contains("-manuelle")) {
            return BackupKind.MANUAL;
        }
        if (fileName.contains("-avant-restauration")) {
            return BackupKind.BEFORE_RESTORE;
        }
        if (fileName.contains("-avant-v")) {
            return BackupKind.BEFORE_UPGRADE;
        }
        return BackupKind.OTHER;
    }
}