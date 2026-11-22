package org.gu.fms.desktop.platform.backup;

import java.nio.file.Path;
import java.time.Instant;

/** Fichier de sauvegarde présent dans le dossier des sauvegardes. */
public record BackupFile(Path path, BackupKind kind, Instant createdAt, long sizeBytes) {

    public String fileName() {
        return path.getFileName().toString();
    }
}
