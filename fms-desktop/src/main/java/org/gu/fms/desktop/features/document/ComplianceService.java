package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.file.StoredFile;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.document.DocumentRules;
import org.gu.fms.domain.document.DocumentStatus;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.DocumentType.DocumentSubject;
import org.gu.fms.domain.document.FleetDocument;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.Driver.DriverStatus;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conformité documentaire de la flotte : pour chaque véhicule exploité (hors vendus et inactifs) et chaque chauffeur
 * présent (hors partis), chaque type de document obligatoire, plus les documents en cours des types facultatifs.
 *
 * Les documents d'un véhicule comprennent ceux des chauffeurs qui le conduisent aujourd'hui (affectations en cours,
 * titulaire, remplaçant ou nuit) : un permis expiré rend le véhicule non conforme.
 */
@Singleton
public final class ComplianceService {

    private final FleetDocumentRepository documents;
    private final DocumentTypeRepository types;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final AssignmentRepository assignments;
    private final DocumentFileRepository files;
    private final FleetPreferencesService prefs;
    private final Clock clock;
    private final AppSettings settings;

    @Inject
    public ComplianceService(FleetDocumentRepository documents, DocumentTypeRepository types,
                             VehicleRepository vehicles, DriverRepository drivers, AssignmentRepository assignments,
                             DocumentFileRepository files, FleetPreferencesService prefs, Clock clock,
                             AppSettings settings) {
        this.documents = documents;
        this.assignments = assignments;
        this.files = files;
        this.types = types;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.prefs = prefs;
        this.clock = clock;
        this.settings = settings;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    public long alertDays() {
        return prefs.load().documentAlertDays();
    }

    public List<ComplianceRow> rows() {
        LocalDate today = today();
        long alertDays = prefs.load().documentAlertDays();
        Map<String, FleetDocument> current = new HashMap<>();
        for (FleetDocument d : documents.findCurrent()) {
            current.put(d.subject() + ":" + d.ownerId() + ":" + d.typeId(), d);
        }
        Map<Long, List<StoredFile>> attachments = files.allByDocument();
        List<DocumentType> allTypes = types.findAll();
        List<ComplianceRow> rows = new ArrayList<>();
        Map<Long, Vehicle> exploited = new LinkedHashMap<>();
        for (Vehicle v : vehicles.findAll()) {
            if (v.status() == VehicleStatus.SOLD || v.status() == VehicleStatus.INACTIVE) {
                continue;
            }
            exploited.put(v.id(), v);
            Owner owner = new Owner(DocumentSubject.VEHICLE, v.id(), v.displayName(),
                    v.status() == VehicleStatus.ACTIVE, List.of(v.id()), v.displayName());
            addRows(rows, owner, allTypes, current, attachments, today, alertDays);
        }
        Map<Long, List<Long>> driven = new HashMap<>();
        for (DriverAssignment a : assignments.findActiveOn(today)) {
            if (exploited.containsKey(a.vehicleId())) {
                List<Long> ids = driven.computeIfAbsent(a.driverId(), k -> new ArrayList<>());
                if (!ids.contains(a.vehicleId())) {
                    ids.add(a.vehicleId());
                }
            }
        }
        for (Driver d : drivers.findAll()) {
            if (d.status() == DriverStatus.LEFT) {
                continue;
            }
            List<Long> ids = driven.getOrDefault(d.id(), List.of());
            String label = ids.stream().map(id -> exploited.get(id).displayName()).collect(Collectors.joining(", "));
            Owner owner = new Owner(DocumentSubject.DRIVER, d.id(), d.displayName(),
                    d.status() == DriverStatus.ACTIVE, ids, label);
            addRows(rows, owner, allTypes, current, attachments, today, alertDays);
        }
        return rows;
    }

    /** Documents d'un véhicule : les siens et ceux des chauffeurs qui le conduisent aujourd'hui. */
    public List<ComplianceRow> forVehicle(long vehicleId) {
        return rows().stream().filter(r -> r.vehicleIds().contains(vehicleId)).toList();
    }

    private record Owner(DocumentSubject subject, Long id, String label, boolean inService, List<Long> vehicleIds,
                         String vehicleLabel) {
    }

    private static void addRows(List<ComplianceRow> rows, Owner owner, List<DocumentType> allTypes,
                                Map<String, FleetDocument> current, Map<Long, List<StoredFile>> attachments,
                                LocalDate today, long alertDays) {
        for (DocumentType type : allTypes) {
            if (type.subject() != owner.subject()) {
                continue;
            }
            FleetDocument doc = current.get(owner.subject() + ":" + owner.id() + ":" + type.id());
            boolean mandatory = type.mandatory() && type.active();
            DocumentStatus status = DocumentRules.status(doc, mandatory, today, alertDays);
            if (status == null) {
                continue;               // type facultatif, aucun document : rien à suivre
            }
            rows.add(new ComplianceRow(owner.subject(), owner.id(), owner.label(), owner.inService(),
                    owner.vehicleIds(), owner.vehicleLabel(), type, doc,
                    doc == null ? List.of() : attachments.getOrDefault(doc.id(), List.of()), status,
                    DocumentRules.daysLeft(doc, today)));
        }
    }

    /**
     * État global (le plus grave) : identifiant → état ; absent = rien à suivre. Pour les véhicules, les documents des
     * chauffeurs qui les conduisent comptent.
     */
    public Map<Long, DocumentStatus> worstBy(DocumentSubject subject) {
        Map<Long, DocumentStatus> worst = new HashMap<>();
        for (ComplianceRow r : rows()) {
            if (subject == DocumentSubject.VEHICLE) {
                r.vehicleIds().forEach(id -> worst.merge(id, r.status(), DocumentStatus::worst));
            } else if (r.subject() == subject) {
                worst.merge(r.ownerId(), r.status(), DocumentStatus::worst);
            }
        }
        return worst;
    }

    /** Historique (documents remplacés compris) d'un titulaire pour un type, du plus récent au plus ancien. */
    public List<FleetDocument> history(ComplianceRow row) {
        return documents.history(row.type().id(), row.subject(), row.ownerId());
    }
}