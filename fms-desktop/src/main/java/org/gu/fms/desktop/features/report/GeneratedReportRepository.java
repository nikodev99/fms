package org.gu.fms.desktop.features.report;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.file.StoredFile;
import org.intellij.lang.annotations.Language;

import java.util.List;

/** Historique des rapports générés (ajout seulement : la base refuse modification et suppression). */
@Singleton
public final class GeneratedReportRepository {

    @Language("SQL")
    private static final String SELECT = "SELECT r.id, r.kind, r.reference, r.subject, r.generated_at, r.generated_by, "
            + FileStore.columns("f", "f_") + " FROM generated_report r JOIN stored_file f ON f.id = r.file_id ";

    private final Jdbc jdbc;

    @Inject
    public GeneratedReportRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public GeneratedReport insert(ReportKind kind, String reference, String subject, StoredFile file) {
        long id = jdbc.insert("INSERT INTO generated_report (kind, reference, subject, file_id, generated_at,"
                        + " generated_by) VALUES (?, ?, ?, ?, ?, ?)", kind, reference, subject, file.id(), file.createdAt(),
                file.createdBy());
        return new GeneratedReport(id, kind, reference, subject, file, file.createdAt(), file.createdBy());
    }

    public boolean exists(String reference) {
        return jdbc.exists("SELECT 1 FROM generated_report WHERE reference = ?", reference);
    }

    /** Du plus récent au plus ancien. */
    public List<GeneratedReport> findAll() {
        return jdbc.query(SELECT + "ORDER BY r.generated_at DESC, r.id DESC", mapper());
    }

    private static RowMapper<GeneratedReport> mapper() {
        RowMapper<StoredFile> file = FileStore.mapper("f_");
        return row -> new GeneratedReport(row.longValue("id"), row.enumValue("kind", ReportKind.class),
                row.string("reference"), row.string("subject"), file.map(row), row.instant("generated_at"),
                row.string("generated_by"));
    }
}
