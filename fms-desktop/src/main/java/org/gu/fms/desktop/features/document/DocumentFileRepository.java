package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.file.StoredFile;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Liens entre les documents et leurs pièces jointes (métadonnées seulement ; le contenu se lit à la demande). */
@Singleton
public final class DocumentFileRepository {

    /** Pièce jointe : document et fichier. */
    public record Attachment(long documentId, StoredFile file) {
    }

    private static final String SELECT = "SELECT l.document_id, " + FileStore.columns("f", "f_")
            + " FROM document_file l JOIN stored_file f ON f.id = l.file_id ";

    private final Jdbc jdbc;
    private final Clock clock;

    @Inject
    public DocumentFileRepository(Jdbc jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void link(long documentId, long fileId) {
        jdbc.insert("INSERT INTO document_file (document_id, file_id, created_at) VALUES (?, ?, ?)", documentId, fileId,
                clock.instant());
    }

    public void unlink(long fileId) {
        jdbc.update("DELETE FROM document_file WHERE file_id = ?", fileId);
    }

    public List<StoredFile> forDocument(long documentId) {
        return jdbc.query(SELECT + "WHERE l.document_id = ? ORDER BY l.id", FileStore.mapper("f_"), documentId);
    }

    public Optional<Attachment> find(long fileId) {
        return jdbc.queryOne(SELECT + "WHERE l.file_id = ?",
                row -> new Attachment(row.longValue("document_id"), FileStore.mapper("f_").map(row)), fileId);
    }

    /** Pièces jointes de tous les documents : identifiant du document → fichiers, dans l'ordre d'ajout. */
    public Map<Long, List<StoredFile>> allByDocument() {
        Map<Long, List<StoredFile>> result = new HashMap<>();
        for (Attachment a : jdbc.query(SELECT + "ORDER BY l.id",
                row -> new Attachment(row.longValue("document_id"), FileStore.mapper("f_").map(row)))) {
            result.computeIfAbsent(a.documentId(), k -> new ArrayList<>()).add(a.file());
        }
        return result;
    }
}
