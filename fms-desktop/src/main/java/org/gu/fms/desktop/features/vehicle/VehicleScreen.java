package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import org.gu.fms.desktop.features.document.*;
import org.gu.fms.desktop.features.report.QuickReports;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.document.DocumentStatus;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;
import org.gu.fms.domain.vehicle.VehicleCategory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Écran Véhicules : écran de référence du moteur {@link CrudScreen}.
 * Tout nouvel écran de gestion (chauffeurs, comptes…) suit ce modèle : textes, colonnes, éditeur.
 *
 * La fiche d'un véhicule liste ses documents et ceux de ses chauffeurs (aperçu des fichiers) et télécharge la fiche
 * véhicule en PDF.
 */
public final class VehicleScreen extends CrudScreen<Vehicle> {

    private final Clock clock;
    private final AppSettings settings;
    private final VehicleCategoryService categoryService;
    private final FleetPreferencesService fleetPrefs;
    private volatile List<VehicleCategory> categories = List.of();
    private volatile Map<Long, VehicleCategory> categoryById = Map.of();
    private volatile Money defaultThreshold;
    private volatile List<DocumentType> documentTypes = List.of();
    private volatile Map<Long, DocumentStatus> compliance = Map.of();
    private volatile Map<Long, List<ComplianceRow>> documentsByVehicle = Map.of();
    private final DocumentFilesView filesView;
    private final QuickReports quickReports;
    private final SimpleBooleanProperty downloading = new SimpleBooleanProperty();
    private final VehicleService service;
    private final DocumentService documents;
    private final DocumentTypeRepository types;
    private final ComplianceService complianceService;
    private final TransactionManager tx;

    @Inject
    public VehicleScreen(CrudContext ui, VehicleService service, VehicleCategoryService categoryService,
                          FleetPreferencesService fleetPrefs, Clock clock, AppSettings settings,
                          DocumentService documents, DocumentTypeRepository types, ComplianceService complianceService,
                          TransactionManager tx, DocumentFilesView filesView, QuickReports quickReports) {
        super(ui, service);
        this.filesView = filesView;
        this.quickReports = quickReports;
        this.service = service;
        this.documents = documents;
        this.types = types;
        this.complianceService = complianceService;
        this.tx = tx;
        this.clock = clock;
        this.settings = settings;
        this.categoryService = categoryService;
        this.fleetPrefs = fleetPrefs;
    }

    @Override
    protected void prepare() {
        List<VehicleCategory> all = categoryService.findAll();
        categoryById = all.stream().collect(Collectors.toMap(VehicleCategory::id, Function.identity()));
        categories = all;
        defaultThreshold = fleetPrefs.load().defaultRepairThreshold();
        documentTypes = types.findAll();
        Map<Long, DocumentStatus> worst = new HashMap<>();
        Map<Long, List<ComplianceRow>> byVehicle = new HashMap<>();
        for (ComplianceRow r : complianceService.rows()) {
            for (Long id : r.vehicleIds()) {
                worst.merge(id, r.status(), DocumentStatus::worst);
                byVehicle.computeIfAbsent(id, k -> new ArrayList<>()).add(r);
            }
        }
        compliance = worst;
        documentsByVehicle = byVehicle;
    }

    /** « Fiche PDF » : téléchargement direct de la fiche du véhicule. */
    @Override
    protected List<Button> editorActions(Vehicle existing) {
        Button sheet = new Button(messages.get("vehicle.sheet"), new FmsIcon(AppIcon.DOWNLOAD, 16));
        sheet.getStyleClass().add("secondary");
        sheet.disableProperty().bind(downloading);
        sheet.setOnAction(e -> quickReports.vehicleSheet(existing.id(), downloading));
        return List.of(sheet);
    }

    private Badge documentsBadge(Vehicle v) {
        DocumentStatus s = compliance.get(v.id());
        if (s == null) {
            return new Badge(messages.get("common.none"), Tone.NEUTRAL);
        }
        return new Badge(messages.label(s), switch (s) {
            case EXPIRED -> Tone.DANGER;
            case MISSING, EXPIRING -> Tone.WARNING;
            case VALID -> Tone.SUCCESS;
        });
    }

    @Override
    protected java.util.concurrent.Callable<Vehicle> saveWork(EntityEditor<Vehicle> editor, Vehicle entity,
                                                              boolean creating) {
        List<DocumentDraft> drafts = creating && editor instanceof VehicleEditor e ? e.documentDrafts() : List.of();
        if (drafts.isEmpty()) {
            return () -> service.save(entity);
        }
        return () -> tx.inTransaction(c -> {
            Vehicle saved = service.save(entity);
            documents.createFor(saved.id(), drafts);
            return saved;
        });
    }

    private String categoryName(Vehicle v) {
        VehicleCategory c = categoryById.get(v.categoryId());
        return c == null ? "" : c.name();
    }

    @Override
    protected String title() {
        return messages.get("nav.vehicles");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total
                ? messages.get("vehicles.count", total)
                : messages.get("vehicles.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("vehicle.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.VEHICLE;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("vehicles.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("vehicles.empty.text");
    }

    @Override
    protected void configureTable(DataTable<Vehicle> table) {
        table.textColumn(messages.get("vehicle.registration"), Vehicle::registration, 130).setMinWidth(115);
        table.textColumn(messages.get("vehicles.column.vehicle"), v -> v.make() + " " + v.model(), 180).setMinWidth(130);
        table.textColumn(messages.get("vehicle.category"), this::categoryName, 130).setMinWidth(90);
        table.badgeColumn(messages.get("vehicle.status"), v -> new Badge(messages.label(v.status()), tone(v.status())), 140)
                .setMinWidth(125);
        table.badgeColumn(messages.get("vehicles.column.documents"), this::documentsBadge, 130).setMinWidth(115);
        table.numberColumn(messages.get("vehicle.currentMileage"), Vehicle::currentMileage, messages.get("unit.km"), 120)
                .setMinWidth(100);
        table.moneyColumn(messages.get("vehicles.column.purchasePrice"), Vehicle::purchasePrice, 130).setMinWidth(100);
        table.searchBy(v -> String.join(" ", v.registration(), v.make(), v.model(),
                categoryName(v), messages.label(v.status())));
    }

    @Override
    protected Node filters() {
        List<VehicleStatus> options = new ArrayList<>();
        options.add(null);
        options.addAll(List.of(VehicleStatus.values()));
        ComboBox<VehicleStatus> status = new ComboBox<>(FXCollections.observableArrayList(options));
        status.getStyleClass().add("filter-combo");
        status.setButtonCell(statusCell());
        status.setCellFactory(list -> statusCell());
        status.valueProperty().addListener((obs, o, selected) ->
                table().filterProperty().set(v -> selected == null || v.status() == selected));
        return status;
    }

    private ListCell<VehicleStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(VehicleStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? messages.get("vehicles.filter.allStatuses") : messages.label(item));
            }
        };
    }

    @Override
    protected EntityEditor<Vehicle> createEditor(Vehicle existing) {
        Node documentsPanel = existing == null ? null
                : filesView.vehicleDocuments(documentsByVehicle.getOrDefault(existing.id(), List.of()));
        return new VehicleEditor(ui.forms(), messages, () -> LocalDate.ofInstant(clock.instant(), settings.zone()),
                categories, defaultThreshold, existing, documentTypes, documentsPanel);
    }

    @Override
    protected String savedMessage(Vehicle saved, boolean created) {
        return messages.get(created ? "vehicle.created" : "vehicle.updated", saved.registration());
    }

    @Override
    protected Object identityOf(Vehicle vehicle) {
        return vehicle.id();
    }

    static Tone tone(VehicleStatus status) {
        return switch (status) {
            case ACTIVE -> Tone.SUCCESS;
            case UNDER_MAINTENANCE -> Tone.WARNING;
            case IMMOBILISED -> Tone.DANGER;
            case TEMPORARILY_UNAVAILABLE, SOLD, INACTIVE -> Tone.NEUTRAL;
        };
    }
}
