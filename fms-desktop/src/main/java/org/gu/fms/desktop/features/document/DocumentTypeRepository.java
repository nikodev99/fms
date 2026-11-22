package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.document.DocumentType;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class DocumentTypeRepository extends JdbcRepository<DocumentType> {

    @Inject
    public DocumentTypeRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "document_type";
    }

    @Override
    protected String defaultOrder() {
        return "subject DESC, name COLLATE NOCASE";
    }

    @Override
    protected RowMapper<DocumentType> mapper() {
        return row -> new DocumentType(row.longValue("id"), row.longValue("version"), row.string("name"),
                row.enumValue("subject", DocumentType.DocumentSubject.class), row.bool("mandatory"), row.bool("active"));
    }

    @Override
    protected Long idOf(DocumentType t) {
        return t.id();
    }

    @Override
    protected long versionOf(DocumentType t) {
        return t.version();
    }

    @Override
    protected Map<String, Object> columnsOf(DocumentType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.name());
        m.put("subject", t.subject());
        m.put("mandatory", t.mandatory());
        m.put("active", t.active());
        return m;
    }

    public boolean nameTaken(DocumentType t) {
        return t.id() == null
                ? jdbc.exists("SELECT 1 FROM document_type WHERE subject = ? AND name = ?", t.subject(), t.name())
                : jdbc.exists("SELECT 1 FROM document_type WHERE subject = ? AND name = ? AND id <> ?",
                t.subject(), t.name(), t.id());
    }
}
