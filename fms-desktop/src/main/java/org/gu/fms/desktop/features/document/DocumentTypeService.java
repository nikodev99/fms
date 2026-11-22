package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.document.DocumentType;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Singleton
public final class DocumentTypeService extends AuditedCrudService<DocumentType> {

    private final DocumentTypeRepository types;

    @Inject
    public DocumentTypeService(DocumentTypeRepository repository, TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.types = repository;
    }

    @Override
    protected String auditType() {
        return "document_type";
    }

    @Override
    protected DocumentType normalize(DocumentType t) {
        return DocumentType.normalize(t);
    }

    @Override
    protected List<FieldError> validate(DocumentType t, LocalDate today) {
        return DocumentType.validate(t);
    }

    @Override
    protected void check(DocumentType t, DocumentType before, List<FieldError> errors) {
        if (t.name() != null && t.subject() != null && types.nameTaken(t)) {
            errors.add(FieldError.of("name", "documentType.name.taken", t.name()));
        }
    }

    @Override
    protected FieldError uniqueViolation(DocumentType t) {
        return FieldError.of("name", "documentType.name.taken", t.name());
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
    protected DocumentType withIdentity(DocumentType t, long id, long version) {
        return t.withIdentity(id, version);
    }
}
