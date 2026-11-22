package org.gu.fms.desktop.platform.backup;

import java.nio.file.Path;
import java.util.Objects;

/** Dossier des sauvegardes (fourni par l'application, voir {@code AppModule}). */
public record BackupLocation(Path directory) {

    public BackupLocation {
        Objects.requireNonNull(directory, "directory");
    }
}
