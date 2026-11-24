package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.file.StoredFile;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.services.DeletableService;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.document.DocumentRules;
import org.gu.fms.domain.document.DocumentStatus;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.DocumentType.DocumentSubject;
import org.gu.fms.domain.document.FleetDocument;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.Driver.DriverStatus;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Module Documents (instructions §8) : conformité de chaque véhicule exploité et de chaque chauffeur, pour chaque type
 * de document (Paramètres › Flotte › Types de documents). Une ligne = un titulaire et un type ; une ligne « Manquant »
 * s'ouvre directement en saisie. Un document se renouvelle (l'ancien reste dans l'historique) ; un document saisi par
 * erreur se supprime. Les fichiers (scan, photo) se joignent au document et s'affichent à la demande.
 *
 * Le filtre par véhicule montre ses documents et ceux des chauffeurs qui le conduisent (permis, certificat médical).
 */
public final class DocumentsScreen extends CrudScreen<ComplianceRow> {

    /** Titulaire proposé à la création d'un document. */
    record Owner(DocumentType.DocumentSubject subject, Long id, String label) {
    }

    /** Choix du filtre véhicule : un véhicule, ou les chauffeurs sans affectation en cours. */
    record VehicleOption(Long id, String label) {
        static final VehicleOption UNASSIGNED = new VehicleOption(null, "");
    }

    private final ComplianceService compliance;
    private final DocumentService documents;
    private final DocumentTypeRepository types;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final DocumentFilesView filesView;
    private final ComboBox<VehicleOption> vehicleFilterBox = new ComboBox<>();
    private final KpiStrip kpis = new KpiStrip();
    private final KpiStrip.Kpi kValid;
    private final KpiStrip.Kpi kExpiring;
    private final KpiStrip.Kpi kExpired;
    private final KpiStrip.Kpi kMissing;
    private volatile List<DocumentType> typeList = List.of();
    private volatile List<Owner> owners = List.of();
    private volatile Map<String, List<FleetDocument>> histories = Map.of();
    private volatile long alertDays;
    private VehicleOption vehicleFilter;
    private StatusFilter statusFilter = StatusFilter.ALL;

    enum StatusFilter { ALL, ATTENTION, EXPIRED, MISSING, EXPIRING, VALID }

    @Inject
    public DocumentsScreen(CrudContext ui, ComplianceService compliance, DocumentService documents,
                           DocumentTypeRepository types, VehicleRepository vehicles, DriverRepository drivers,
                           DocumentFilesView filesView) {
        super(ui, new Adapter(compliance, documents));
        this.filesView = filesView;
        this.compliance = compliance;
        this.documents = documents;
        this.types = types;
        this.vehicles = vehicles;
        this.drivers = drivers;
        kValid = kpis.add(messages.get("documents.kpi.valid"));
        kExpiring = kpis.add(messages.get("documents.kpi.expiring"));
        kExpired = kpis.add(messages.get("documents.kpi.expired"));
        kMissing = kpis.add(messages.get("documents.kpi.missing"));
    }

    /**
     * Lien entre l'écran générique et les services : la ligne porte le document à enregistrer.
     */
        private record Adapter(ComplianceService compliance,
                               DocumentService documents) implements CrudService<ComplianceRow>, DeletableService<ComplianceRow> {

        @Override
            public List<ComplianceRow> findAll() {
                return compliance.rows();
            }

            @Override
            public ComplianceRow save(ComplianceRow row) {
                return row.withDocument(documents.save(row.document()));
            }

            @Override
            public boolean deletable(ComplianceRow row) {
                return row.document() != null && documents.deletable(row.document());
            }

            @Override
            public void delete(ComplianceRow row) {
                documents.delete(row.document());
            }
        }

    @Override
    protected void prepare() {
        typeList = types.findAll();
        List<Owner> list = new ArrayList<>();
        for (Vehicle v : vehicles.findAll()) {
            if (v.status() != VehicleStatus.SOLD && v.status() != VehicleStatus.INACTIVE) {
                list.add(new Owner(DocumentSubject.VEHICLE, v.id(), v.displayName()));
            }
        }
        for (Driver d : drivers.findAll()) {
            if (d.status() != DriverStatus.LEFT) {
                list.add(new Owner(DocumentSubject.DRIVER, d.id(), d.displayName()));
            }
        }
        owners = list;
    }

    @Override
    protected List<ComplianceRow> load() {
        List<ComplianceRow> rows = new ArrayList<>(compliance.rows());
        rows.sort(java.util.Comparator.comparing(ComplianceRow::status).thenComparing(ComplianceRow::ownerLabel)
                .thenComparing(r -> r.type().name()));
        Map<String, List<FleetDocument>> h = new java.util.HashMap<>();
        for (ComplianceRow r : rows) {
            if (r.document() != null && r.document().replacesId() != null) {
                h.put(r.key(), compliance.history(r));
            }
        }
        histories = h;
        alertDays = compliance.alertDays();
        return rows;
    }

    private void refreshVehicleOptions() {
        VehicleOption selected = vehicleFilterBox.getValue();
        List<VehicleOption> options = new ArrayList<>();
        options.add(null);
        owners.stream().filter(o -> o.subject() == DocumentSubject.VEHICLE)
                .forEach(o -> options.add(new VehicleOption(o.id(), o.label())));
        options.add(VehicleOption.UNASSIGNED);
        vehicleFilterBox.getItems().setAll(options);
        vehicleFilterBox.setValue(selected == null ? null : options.stream()
                .filter(o -> o != null && Objects.equals(o.id(), selected.id())).findFirst().orElse(null));
    }

    @Override
    protected void loaded(List<ComplianceRow> items) {
        refreshVehicleOptions();
        Map<DocumentStatus, Integer> counts = new EnumMap<>(DocumentStatus.class);
        items.forEach(r -> counts.merge(r.status(), 1, Integer::sum));
        kValid.set(Integer.toString(counts.getOrDefault(DocumentStatus.VALID, 0)), null, Tone.SUCCESS);
        kExpiring.set(Integer.toString(counts.getOrDefault(DocumentStatus.EXPIRING, 0)),
                messages.get("documents.kpi.expiring.caption", alertDays), Tone.WARNING);
        kExpired.set(Integer.toString(counts.getOrDefault(DocumentStatus.EXPIRED, 0)), null,
                counts.getOrDefault(DocumentStatus.EXPIRED, 0) > 0 ? Tone.DANGER : Tone.NEUTRAL);
        kMissing.set(Integer.toString(counts.getOrDefault(DocumentStatus.MISSING, 0)), null,
                counts.getOrDefault(DocumentStatus.MISSING, 0) > 0 ? Tone.WARNING : Tone.NEUTRAL);
    }

    @Override
    protected Node summary() {
        return kpis;
    }

    @Override
    protected String title() {
        return messages.get("nav.documents");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("documents.count", total)
                : messages.get("documents.countFiltered", visible, total);
    }

    @Override
    protected String hint() {
        return messages.get("documents.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("document.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.DOCUMENT;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("documents.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("documents.empty.text");
    }

    static Tone tone(DocumentStatus s) {
        return switch (s) {
            case EXPIRED -> Tone.DANGER;
            case MISSING, EXPIRING -> Tone.WARNING;
            case VALID -> Tone.SUCCESS;
        };
    }

    private Badge status(ComplianceRow r) {
        return new Badge(messages.label(r.status()), tone(r.status()));
    }

    private String expiry(ComplianceRow r) {
        if (r.document() == null) {
            return "—";
        }
        if (r.document().expiresOn() == null) {
            return messages.get("document.noExpiry");
        }
        String date = ui.formats().date(r.document().expiresOn());
        Long days = r.daysLeft();
        if (days == null) {
            return date;
        }
        return days < 0 ? messages.get("document.expiredSince", date, -days)
                : messages.get("document.expiresIn", date, days);
    }

    @Override
    protected void configureTable(DataTable<ComplianceRow> table) {
        table.textColumn(messages.get("document.vehicle"), r -> r.vehicleLabel().isEmpty() ? "—" : r.vehicleLabel(), 190)
                .setMinWidth(140);
        table.textColumn(messages.get("document.subject"), r -> r.subject() == DocumentSubject.VEHICLE
                ? messages.get("vehicle.documents.vehicle") : r.ownerLabel(), 160).setMinWidth(120);
        table.textColumn(messages.get("document.type"), r -> r.type().name(), 170).setMinWidth(130);
        table.textColumn(messages.get("document.number"),
                        r -> r.document() == null || r.document().number() == null ? "—" : r.document().number(), 130)
                .setMinWidth(110);
        table.textColumn(messages.get("document.expiresOn"), this::expiry, 230).setMinWidth(180);
        table.textColumn(messages.get("document.files"), r -> r.files().isEmpty() ? "—"
                : messages.get("document.files.count", r.files().size()), 90).setMinWidth(80);
        table.badgeColumn(messages.get("document.status"), this::status, 120).setMinWidth(110);
        table.searchBy(r -> r.ownerLabel() + " " + r.vehicleLabel() + " " + r.type().name() + " "
                + messages.label(r.status()) + " "
                + (r.document() == null ? "" : String.valueOf(r.document().number())));
    }

    @Override
    protected Node filters() {
        vehicleFilterBox.setButtonCell(cell(this::vehicleOptionLabel));
        vehicleFilterBox.setCellFactory(l -> cell(this::vehicleOptionLabel));
        vehicleFilterBox.valueProperty().addListener((obs, o, v) -> {
            vehicleFilter = v;
            applyFilter();
        });
        ComboBox<StatusFilter> status = new ComboBox<>();
        status.getItems().addAll(StatusFilter.values());
        status.setValue(StatusFilter.ALL);
        status.setButtonCell(cell(f -> messages.get("documents.filter." + f.name().toLowerCase(java.util.Locale.ROOT))));
        status.setCellFactory(l -> cell(f -> messages.get("documents.filter." + f.name().toLowerCase(java.util.Locale.ROOT))));
        status.valueProperty().addListener((obs, o, f) -> {
            statusFilter = f == null ? StatusFilter.ALL : f;
            applyFilter();
        });
        return new HBox(8, vehicleFilterBox, status);
    }

    private String vehicleOptionLabel(VehicleOption o) {
        return o == null ? messages.get("documents.filter.allVehicles")
                : o == VehicleOption.UNASSIGNED ? messages.get("documents.filter.unassigned") : o.label();
    }

    private static <X> ListCell<X> cell(java.util.function.Function<X, String> text) {
        return new ListCell<>() {
            @Override
            protected void updateItem(X item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : text.apply(item));
            }
        };
    }

    private void applyFilter() {
        Predicate<ComplianceRow> p = r -> matchesVehicle(r) && switch (statusFilter) {
            case ALL -> true;
            case ATTENTION -> r.needsAttention();
            case EXPIRED -> r.status() == DocumentStatus.EXPIRED;
            case MISSING -> r.status() == DocumentStatus.MISSING;
            case EXPIRING -> r.status() == DocumentStatus.EXPIRING;
            case VALID -> r.status() == DocumentStatus.VALID;
        };
        table().filterProperty().set(p);
    }

    private boolean matchesVehicle(ComplianceRow r) {
        if (vehicleFilter == null) {
            return true;
        }
        if (vehicleFilter == VehicleOption.UNASSIGNED) {
            return r.subject() == DocumentSubject.DRIVER && r.vehicleIds().isEmpty();
        }
        return r.vehicleIds().contains(vehicleFilter.id());
    }

    /** Filtre « à traiter » (appelé depuis une alerte). */
    public void showAttention() {
        statusFilter = StatusFilter.ATTENTION;
        if (table() != null) {
            applyFilter();
        }
    }

    @Override
    protected List<Button> editorActions(ComplianceRow existing) {
        if (existing.document() == null) {
            return List.of();
        }
        Button renew = new Button(messages.get("document.renew"), new FmsIcon(AppIcon.REFRESH, 16));
        renew.getStyleClass().add("secondary");
        renew.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                openRenewal(existing);
            }
        }));
        return List.of(renew);
    }

    private void openRenewal(ComplianceRow row) {
        FleetDocument previous = row.document();
        Fields f = new Fields(ui.forms().create(), messages, this::today);
        f.issuedOn.setValue(today());
        if (previous.expiresOn() != null) {
            f.expiresOn.setValue(previous.expiresOn().plusYears(1));
        }
        f.issuer.setValue(previous.issuer());
        ui.<FleetDocument>formDrawer(messages.get("document.renew.title", row.type().name()))
                .subtitle(row.ownerLabel())
                .form(f.form)
                .saveLabel(messages.get("document.renew"), AppIcon.REFRESH)
                .onSave(() -> {
                    FleetDocument next = f.toDocument(null, 0, row.type().id(), row.subject(), row.ownerId(), null);
                    List<Path> chosen = f.files.getValue();
                    return () -> documents.renew(previous, next, chosen);
                })
                .onSuccess(saved -> {
                    ui.toasts().success(messages.get("document.renewed", row.type().name()));
                    reload(r -> r.key().equals(row.key()));
                })
                .open();
    }

    private LocalDate today() {
        return compliance.today();
    }

    @Override
    protected Callable<ComplianceRow> saveWork(EntityEditor<ComplianceRow> editor, ComplianceRow entity,
                                               boolean creating) {
        List<Path> chosen = editor instanceof Editor e ? e.fields.files.getValue() : List.of();
        return () -> entity.withDocument(documents.saveWithFiles(entity.document(), chosen));
    }

    /** Retire une pièce jointe (après confirmation) ; la ligne du fichier disparaît du tiroir. */
    private void removeAttachment(StoredFile file, Runnable removeLine) {
        ui.dialogs().confirm(messages.get("document.files.remove.title"),
                messages.get("document.files.remove.message", file.fileName()), messages.get("document.files.remove"),
                Tone.DANGER).thenAccept(ok -> {
            if (ok) {
                ui.run(() -> {
                    documents.removeAttachment(file);
                    return file;
                }, x -> messages.get("document.files.removed", file.fileName()), x -> {
                    removeLine.run();
                    refresh();
                });
            }
        });
    }

    @Override
    protected EntityEditor<ComplianceRow> createEditor(ComplianceRow existing) {
        return new Editor(existing);
    }

    @Override
    protected String savedMessage(ComplianceRow saved, boolean created) {
        return messages.get("document.saved", saved.type() == null ? "" : saved.type().name());
    }

    @Override
    protected Object identityOf(ComplianceRow r) {
        return r.key();
    }

    /** Champs communs : saisie, modification, renouvellement. */
    private static final class Fields {
        final Form form;
        final TextInput number;
        final TextInput issuer;
        final DateInput issuedOn;
        final DateInput expiresOn;
        final TextAreaInput notes;
        final FilesInput files;

        Fields(Form form, Messages messages, Supplier<LocalDate> today) {
            this.form = form;
            number = form.text("number", messages.get("document.number")).maxLength(DocumentRules.NUMBER_MAX);
            issuer = form.text("issuer", messages.get("document.issuer")).maxLength(DocumentRules.ISSUER_MAX);
            issuedOn = form.date("issuedOn", messages.get("document.issuedOn"))
                    .rule(Rules.notAfter(today));
            expiresOn = form.date("expiresOn", messages.get("document.expiresOn"))
                    .help(messages.get("document.expiresOn.help"));
            notes = form.textArea("notes", messages.get("document.notes")).maxLength(DocumentRules.NOTES_MAX).span(2);
            files = form.files("files", messages.get("document.files.add")).span(2)
                    .help(messages.get("document.files.help"));
        }

        FleetDocument toDocument(Long id, long version, Long typeId, DocumentSubject subject, Long ownerId,
                                 FleetDocument existing) {
            FleetDocument d = new FleetDocument(id, version, typeId, subject, null, null, number.getValue(),
                    issuer.getValue(), issuedOn.getValue(), expiresOn.getValue(),
                    existing == null ? null : existing.replacesId(), existing != null && existing.superseded(),
                    notes.getValue());
            return ownerId == null ? d : d.forOwner(ownerId);
        }

        void fill(FleetDocument d) {
            number.setValue(d.number());
            issuer.setValue(d.issuer());
            issuedOn.setValue(d.issuedOn());
            expiresOn.setValue(d.expiresOn());
            notes.setValue(d.notes());
        }
    }

    private final class Editor implements EntityEditor<ComplianceRow> {
        private final ComplianceRow row;
        private final Fields fields;
        private final ChoiceInput<DocumentSubject> subject;
        private final ChoiceInput<Owner> owner;
        private final ChoiceInput<DocumentType> type;

        Editor(ComplianceRow row) {
            this.row = row;
            Form form = ui.forms().create();
            if (row == null) {
                subject = form.choice("subject", messages.get("document.subject"), DocumentSubject.class).required();
                owner = form.choice("ownerId", messages.get("document.owner"), List.<Owner>of(), Owner::label)
                        .required().span(2);
                type = form.choice("typeId", messages.get("document.type"), List.<DocumentType>of(), DocumentType::name)
                        .required();
                subject.valueProperty().addListener((obs, o, s) -> {
                    owner.setOptions(owners.stream().filter(x -> x.subject() == s).toList());
                    type.setOptions(typeList.stream().filter(t -> t.subject() == s && t.active()).toList());
                });
                subject.setValue(DocumentSubject.VEHICLE);
            } else {
                subject = null;
                owner = null;
                type = null;
            }
            fields = new Fields(form, messages, DocumentsScreen.this::today);
            if (row != null && row.document() != null) {
                fields.fill(row.document());
            }
        }

        @Override
        public String title() {
            if (row == null) {
                return messages.get("document.create");
            }
            return row.document() == null ? messages.get("document.add", row.type().name())
                    : messages.get("document.edit", row.type().name());
        }

        @Override
        public String subtitle() {
            return row == null ? null : row.ownerLabel();
        }

        @Override
        public Node header() {
            if (row == null) {
                return null;
            }
            DetailList details = new DetailList();
            details.add(messages.get("document.status"), messages.label(row.status()), tone(row.status()));
            details.add(messages.get("document.mandatory"),
                    messages.get(row.type().mandatory() ? "common.yes" : "common.no"));
            List<FleetDocument> history = histories.getOrDefault(row.key(), List.of());
            for (FleetDocument old : history) {
                if (row.document() != null && old.id().equals(row.document().id())) {
                    continue;
                }
                details.add(messages.get("document.previous"),
                        (old.number() == null ? "" : old.number() + " — ")
                                + (old.expiresOn() == null ? messages.get("document.noExpiry")
                                : messages.get("document.expiredOn", ui.formats().date(old.expiresOn()))));
            }
            if (row.document() == null) {
                return details;
            }
            String title = row.type().name() + " — " + row.ownerLabel();
            return new VBox(14, details, filesView.attachments(title, row.files(), DocumentsScreen.this::removeAttachment));
        }

        @Override
        public Form form() {
            return fields.form;
        }

        @Override
        public ComplianceRow toEntity() {
            if (row == null) {
                Owner o = owner.getValue();
                DocumentType t = type.getValue();
                FleetDocument d = fields.toDocument(null, 0, t == null ? null : t.id(), subject.getValue(),
                        o == null ? null : o.id(), null);
                return new ComplianceRow(subject.getValue(), o == null ? null : o.id(), o == null ? "" : o.label(),
                        false, List.of(), "", t, d, List.of(), DocumentStatus.VALID, null);
            }
            FleetDocument existing = row.document();
            FleetDocument d = fields.toDocument(existing == null ? null : existing.id(),
                    existing == null ? 0 : existing.version(), row.type().id(), row.subject(), row.ownerId(), existing);
            return row.withDocument(d);
        }
    }
}
