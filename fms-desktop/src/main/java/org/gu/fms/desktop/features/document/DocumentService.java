package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.file.StoredFile;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.services.DeletableService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.document.DocumentRules;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.DocumentType.DocumentSubject;
import org.gu.fms.domain.document.FleetDocument;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Documents des véhicules et des chauffeurs (instructions §8).
 *
 * <ul>
 *   <li>un seul document en cours par titulaire et par type ; un nouveau document se fait par
 *       {@link #renew renouvellement}, qui garde l'ancien dans l'historique ;</li>
 *   <li>un document saisi par erreur peut être supprimé s'il est le document en cours : le document qu'il
 *       remplaçait redevient le document en cours ;</li>
 *   <li>un document déjà expiré peut être enregistré (régularisation) : il est signalé, pas refusé ;</li>
 *   <li>pièces jointes (scan PDF, photo) : enregistrées dans la base ; ajoutées ou retirées seulement sur le document
 *       en cours, l'historique garde les siennes.</li>
 * </ul>
 */
@Singleton
public final class DocumentService extends AuditedCrudService<FleetDocument> implements DeletableService<FleetDocument> {

    static final String AUDIT_TYPE = "fleet_document";

    private final FleetDocumentRepository documents;
    private final DocumentTypeRepository types;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final DocumentFileRepository attachments;
    private final FileStore files;

    @Inject
    public DocumentService(FleetDocumentRepository documents, DocumentTypeRepository types, VehicleRepository vehicles,
                           DriverRepository drivers, DocumentFileRepository attachments, FileStore files,
                           TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        super(documents, tx, audit, clock, settings);
        this.documents = documents;
        this.attachments = attachments;
        this.files = files;
        this.types = types;
        this.vehicles = vehicles;
        this.drivers = drivers;
    }

    @Override
    protected String auditType() {
        return AUDIT_TYPE;
    }

    @Override
    protected FleetDocument normalize(FleetDocument d) {
        return DocumentRules.normalize(d);
    }

    @Override
    protected List<FieldError> validate(FleetDocument d, LocalDate today) {
        return DocumentRules.validate(d, today);
    }

    @Override
    protected void check(FleetDocument d, FleetDocument before, List<FieldError> errors) {
        if (d.typeId() == null || d.subject() == null || d.ownerId() == null) {
            return;
        }
        DocumentType type = types.findById(d.typeId()).orElse(null);
        if (type == null || type.subject() != d.subject()) {
            errors.add(FieldError.of("typeId", "document.typeMismatch"));
            return;
        }
        boolean ownerExists = d.subject() == DocumentSubject.VEHICLE
                ? vehicles.findById(d.ownerId()).isPresent() : drivers.findById(d.ownerId()).isPresent();
        if (!ownerExists) {
            errors.add(FieldError.of("ownerId", "document.ownerUnknown"));
        }
        if (before == null) {
            if (!type.active()) {
                errors.add(FieldError.of("typeId", "document.typeInactive"));
            }
            if (d.replacesId() == null
                    && documents.findCurrent(d.typeId(), d.subject(), d.ownerId()).isPresent()) {
                errors.add(FieldError.of(null, "document.currentExists", type.name()));
            }
        } else if (!before.typeId().equals(d.typeId()) || before.subject() != d.subject()
                || !before.ownerId().equals(d.ownerId())) {
            errors.add(FieldError.of(null, "document.ownerFixed"));
        }
    }

    @Override
    protected FleetDocument prepare(FleetDocument d, FleetDocument before) {
        // La chaîne de remplacement est gérée ici, jamais par le formulaire.
        return before == null ? d : new FleetDocument(d.id(), d.version(), d.typeId(), d.subject(), d.vehicleId(),
                d.driverId(), d.number(), d.issuer(), d.issuedOn(), d.expiresOn(), before.replacesId(),
                before.superseded(), d.notes());
    }

    @Override
    protected FieldError uniqueViolation(FleetDocument d) {
        return FieldError.of(null, "document.currentExists", "");
    }

    @Override
    protected Long idOf(FleetDocument d) {
        return d.id();
    }

    @Override
    protected long versionOf(FleetDocument d) {
        return d.version();
    }

    @Override
    protected FleetDocument withIdentity(FleetDocument d, long id, long version) {
        return d.withIdentity(id, version);
    }

    /** Renouvelle le document en cours {@code previous} : le nouveau devient le document en cours. */
    public FleetDocument renew(FleetDocument previous, FleetDocument next) {
        return renew(previous, next, List.of());
    }

    /** Renouvellement avec les fichiers du nouveau document (scan, photo). */
    public FleetDocument renew(FleetDocument previous, FleetDocument next, List<Path> newFiles) {
        FleetDocument input = DocumentRules.normalize(next);
        List<FieldError> errors = new ArrayList<>(DocumentRules.validate(input, today()));
        return tx.inTransaction(c -> {
            FleetDocument stored = documents.findById(previous.id())
                    .orElseThrow(() -> new StaleDataException(AUDIT_TYPE, previous.id(), previous.version()));
            if (stored.version() != previous.version()) {
                throw new StaleDataException(AUDIT_TYPE, previous.id(), previous.version());
            }
            errors.addAll(DocumentRules.validateRenewal(stored, input));
            ValidationException.throwIfAny(errors);
            documents.setSuperseded(stored.id(), true);
            FleetDocument ready = input.replacing(stored.id());
            long id = documents.insert(ready);
            FleetDocument saved = ready.withIdentity(id, 0);
            audit.created(AUDIT_TYPE, id, documents.snapshot(saved));
            audit.event(AUDIT_TYPE, stored.id(), AuditAction.UPDATE, Map.of("supersededBy", id), null);
            attachAll(id, newFiles);
            return saved;
        });
    }

    /** Documents saisis à la création d'un véhicule ou d'un chauffeur (même transaction que la fiche). */
    public void createFor(Long ownerId, List<DocumentDraft> drafts) {
        for (DocumentDraft draft : drafts) {
            FleetDocument saved = save(draft.document().forOwner(ownerId));
            attachAll(saved.id(), draft.files());
        }
    }

    /** Enregistre un document et joint ses fichiers, dans une seule transaction. */
    public FleetDocument saveWithFiles(FleetDocument d, List<Path> newFiles) {
        if (newFiles.isEmpty()) {
            return save(d);
        }
        return tx.inTransaction(c -> {
            FleetDocument saved = save(d);
            attachAll(saved.id(), newFiles);
            return saved;
        });
    }

    /** Joint des fichiers du poste au document en cours {@code documentId}. */
    public List<StoredFile> attach(long documentId, List<Path> newFiles) {
        return tx.inTransaction(c -> attachAll(documentId, newFiles));
    }

    private List<StoredFile> attachAll(long documentId, List<Path> newFiles) {
        if (newFiles.isEmpty()) {
            return List.of();
        }
        FleetDocument doc = documents.findById(documentId)
                .orElseThrow(() -> new StaleDataException(AUDIT_TYPE, documentId, 0));
        if (!doc.isCurrent()) {
            throw ValidationException.of(FieldError.of(null, "document.attachment.history"));
        }
        List<StoredFile> added = new ArrayList<>();
        for (Path p : newFiles) {
            StoredFile f = files.put(p.getFileName().toString(), FileStore.read(p));
            attachments.link(documentId, f.id());
            added.add(f);
        }
        audit.event(AUDIT_TYPE, documentId, AuditAction.UPDATE,
                Map.of("attached", added.stream().map(StoredFile::fileName).toList()), null);
        return added;
    }

    /** Pièces jointes d'un document, dans l'ordre d'ajout. */
    public List<StoredFile> attachments(long documentId) {
        return attachments.forDocument(documentId);
    }

    /** Contenu d'une pièce jointe (aperçu, téléchargement). */
    public byte[] content(StoredFile file) {
        return files.content(file.id());
    }

    /** Retire une pièce jointe ajoutée par erreur (document en cours seulement) : le fichier est supprimé. */
    public void removeAttachment(StoredFile file) {
        tx.runInTransaction(c -> {
            DocumentFileRepository.Attachment a = attachments.find(file.id())
                    .orElseThrow(() -> new StaleDataException("document_file", file.id(), 0));
            FleetDocument doc = documents.findById(a.documentId()).orElseThrow();
            if (!doc.isCurrent()) {
                throw ValidationException.of(FieldError.of(null, "document.attachment.history"));
            }
            attachments.unlink(file.id());
            files.delete(file.id());
            audit.event(AUDIT_TYPE, doc.id(), AuditAction.UPDATE, Map.of("detached", file.fileName()), null);
        });
    }

    @Override
    public boolean deletable(FleetDocument d) {
        return d.id() != null && d.isCurrent();
    }

    /** Suppression d'un document saisi par erreur : seulement le document en cours ; son prédécesseur est rétabli. */
    @Override
    public void delete(FleetDocument d) {
        tx.runInTransaction(c -> {
            FleetDocument stored = documents.findById(d.id())
                    .orElseThrow(() -> new StaleDataException(AUDIT_TYPE, d.id(), d.version()));
            if (!stored.isCurrent()) {
                throw ValidationException.of(FieldError.of(null, "document.deleteHistory"));
            }
            for (StoredFile f : attachments.forDocument(stored.id())) {
                attachments.unlink(f.id());
                files.delete(f.id());
            }
            documents.delete(stored.id(), d.version());
            if (stored.replacesId() != null) {
                documents.setSuperseded(stored.replacesId(), false);
            }
            audit.event(AUDIT_TYPE, stored.id(), AuditAction.DELETE, documents.snapshot(stored), null);
        });
    }
}
