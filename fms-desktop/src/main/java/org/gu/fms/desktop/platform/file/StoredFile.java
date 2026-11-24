package org.gu.fms.desktop.platform.file;

import java.time.Instant;

/** Fichier enregistré dans la base (sans son contenu, lu à la demande par {@link FileStore#content}). */
public record StoredFile(long id, String fileName, FileKind kind, long sizeBytes, String sha256, Instant createdAt,
                         String createdBy) {
}
