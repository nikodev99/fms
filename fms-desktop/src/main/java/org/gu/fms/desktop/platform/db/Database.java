package org.gu.fms.desktop.platform.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Fichier SQLite de l'application.
 *
 * Une seule connexion, partagée et protégée par {@link TransactionManager} : SQLite n'admet
 * qu'un écrivain à la fois, et une connexion unique évite les verrous « database is locked ».
 * Réglages appliqués à l'ouverture
 * – foreign_keys = ON : les clés étrangères sont réellement contrôlées (désactivé par défaut dans SQLite)
 * – journal_mode = WAL : écritures plus sûres en cas de coupure de courant
 * – synchronous = FULL : aucune perte de transaction validée, au prix d'écritures un peu plus lentes
 * – busy_timeout = 5000 : attente au lieu d'échec immédiat si un autre outil lit le fichier.
 */
public final class Database implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(Database.class.getName());

    private final Path file;
    private Connection connection;
    private boolean createdOnOpen;

    public Database(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
    }

    public synchronized void open() {
        if (connection != null) return;
        try {
            Files.createDirectories(file.getParent());
            createdOnOpen = Files.notExists(file);
            connection = DriverManager.getConnection("jdbc:sqlite:" + file);
            try(Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=FULL");
                st.execute("PRAGMA busy_timeout=5000");
            }
            LOG.log(System.Logger.Level.INFO, "Base ouverte : {0}{1}", file, createdOnOpen ? " (nouvelle)" : "");
        }catch (IOException e) {
            throw new DataAccessException("Impossible de créer le dossier de données " + file.getParent(), e);
        }catch (SQLException ex) {
            throw new DataAccessException("Impossible d'ouvrir la base " + file, ex);
        }
    }

    /** Réservé à {@link TransactionManager} et au moteur de migration. */
    synchronized Connection connection() {
        if (connection == null) {
            throw new IllegalStateException("La base n'est pas ouverte");
        }
        return connection;
    }

    public Path file() {
        return file;
    }

    /** Vrai si le fichier n'existait pas avant {@link #open()}. */
    public boolean wasCreatedOnOpen() {
        return createdOnOpen;
    }

    /**
     * Copie cohérente de la base (VACUUM INTO), utilisable même pendant que l'application tourne.
     * Ne doit pas être appelée à l'intérieur d'une transaction.
     */
    public synchronized void backupTo(Path target) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            if (Files.exists(target)) {
                throw new DataAccessException("La sauvegarde existe déjà : " + target);
            }
            try(Statement st = connection.createStatement()) {
                st.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            LOG.log(System.Logger.Level.INFO, "Sauvegarde créée : {0}", target);
        }catch (IOException e) {
            throw new DataAccessException("Impossible de créer le dossier de sauvegarde", e);
        }catch (SQLException e) {
            throw new DataAccessException("Sauvegarde impossible vers " + target, e);
        }
    }

    @Override
    public void close() throws Exception {

    }
}
