package org.gu.fms.desktop.app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Emplacements des données de l'application.
 * <p>
 * Windows : %LOCALAPPDATA%\FMS (données propres à l'utilisateur, hors du dossier d'installation,
 * donc conservées lors d'une mise à jour). Ailleurs : ~/.fms.
 * La propriété système {@code fms.dataDir} permet de choisir un autre dossier (développement, tests).
 */
public record AppPaths(Path dataDirectory) {

    public static AppPaths resolve() {
        String override = System.getProperty("fms.dataDir");
        if (override != null && !override.isBlank()) {
            return new AppPaths(Paths.get(override));
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String localAppData = System.getenv("LOCALAPPDATA");
        if (os.contains("win") && localAppData != null) {
            return new AppPaths(Paths.get(localAppData, "FMS"));
        }
        return new AppPaths(Paths.get(System.getProperty("user.home"), ".fms"));
    }

    public Path databaseFile() {
        return dataDirectory.resolve("fms.db");
    }

    public Path backupDirectory() {
        return dataDirectory.resolve("backups");
    }

    /**
     * Dossier où l'application dépose les fichiers téléchargés (rapports, pièces jointes) : « Téléchargements » de
     * l'utilisateur. La propriété système {@code fms.downloadsDir} permet d'en choisir un autre (tests).
     */
    public Path downloadsDirectory() {
        String override = System.getProperty("fms.downloadsDir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), "Downloads");
    }
}
