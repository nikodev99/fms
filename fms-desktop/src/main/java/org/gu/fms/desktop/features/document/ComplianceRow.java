package org.gu.fms.desktop.features.document;

import org.gu.fms.desktop.platform.file.StoredFile;
import org.gu.fms.domain.document.DocumentStatus;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.DocumentType.DocumentSubject;
import org.gu.fms.domain.document.FleetDocument;

import java.util.List;

/**
 * Ligne du suivi des documents : un titulaire (véhicule ou chauffeur), un type, son document en cours (ou aucun).
 * <p>
 * Un document de chauffeur (permis, certificat médical) reste rattaché au chauffeur — il le suit quand il change de
 * véhicule — et figure aussi dans la liste des documents de chaque véhicule qu'il conduit aujourd'hui.
 *
 * @param ownerInService véhicule en service / chauffeur actif : un document expiré est alors critique
 * @param vehicleIds     véhicules concernés : le véhicule lui-même, ou ceux que le chauffeur conduit aujourd'hui
 * @param vehicleLabel   leurs noms (vide pour un chauffeur sans affectation en cours)
 * @param document       document en cours ; null si manquant
 * @param files          pièces jointes du document en cours
 * @param daysLeft       jours avant expiration (négatif = expiré) ; null sans échéance ou sans document
 */
public record ComplianceRow(DocumentSubject subject, Long ownerId, String ownerLabel, boolean ownerInService,
                            List<Long> vehicleIds, String vehicleLabel, DocumentType type, FleetDocument document,
                            List<StoredFile> files, DocumentStatus status, Long daysLeft) {

    public ComplianceRow {
        vehicleIds = vehicleIds == null ? List.of() : List.copyOf(vehicleIds);
        vehicleLabel = vehicleLabel == null ? "" : vehicleLabel;
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** Identité stable de la ligne (titulaire + type). */
    public String key() {
        return subject + ":" + ownerId + ":" + type.id();
    }

    public boolean needsAttention() {
        return status != DocumentStatus.VALID;
    }

    /** Titulaire, suivi du véhicule conduit pour un chauffeur affecté : « MBEMBA Jean · BZ 300 A ». */
    public String displayLabel() {
        return subject == DocumentSubject.DRIVER && !vehicleLabel.isEmpty() ? ownerLabel + " · " + vehicleLabel
                : ownerLabel;
    }

    public ComplianceRow withDocument(FleetDocument doc) {
        return new ComplianceRow(subject, ownerId, ownerLabel, ownerInService, vehicleIds, vehicleLabel, type, doc,
                files, status, daysLeft);
    }
}
