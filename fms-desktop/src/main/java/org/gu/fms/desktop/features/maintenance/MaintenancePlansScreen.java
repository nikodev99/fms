package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.fleet.FleetPreferences;
import org.gu.fms.domain.maintenance.*;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Entretien › Plans. Dernière réalisation et échéance tiennent compte des interventions enregistrées ;
 * les délais d'alerte viennent de Paramètres › Flotte.
 */
public final class MaintenancePlansScreen extends CrudScreen<MaintenancePlan> {

    private final Clock clock;
    private final AppSettings settings;
    private final MaintenancePlanService service;
    private final VehicleRepository vehicles;
    private final MaintenanceTypeRepository types;
    private final MaintenanceEventRepository events;
    private final FleetPreferencesService prefs;
    private volatile List<Vehicle> vehicleList = List.of();
    private volatile List<MaintenanceType> typeList = List.of();
    private volatile Map<Long, Vehicle> vehicleById = Map.of();
    private volatile Map<Long, MaintenanceType> typeById = Map.of();
    private volatile Map<Long, MaintenancePlan> effective = Map.of();
    private volatile Map<Long, DueStatus> statusById = Map.of();

    @Inject
    public MaintenancePlansScreen(CrudContext ui, MaintenancePlanService service, VehicleRepository vehicles,
                                  MaintenanceTypeRepository types, MaintenanceEventRepository events,
                                  FleetPreferencesService prefs, Clock clock, AppSettings settings) {
        super(ui, service);
        this.service = service;
        this.vehicles = vehicles;
        this.types = types;
        this.events = events;
        this.prefs = prefs;
        this.clock = clock;
        this.settings = settings;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        List<Vehicle> v = vehicles.findAll();
        List<MaintenanceType> t = types.findAll();
        List<MaintenanceEvent> history = events.findAll();
        FleetPreferences p = prefs.load();
        Map<Long, Vehicle> vById = v.stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
        Map<Long, MaintenancePlan> eff = new HashMap<>();
        Map<Long, DueStatus> status = new HashMap<>();
        for (MaintenancePlan plan : service.findAll()) {
            MaintenancePlan e = MaintenancePlanRules.withHistory(plan, history);
            Vehicle vehicle = vById.get(plan.vehicleId());
            eff.put(plan.id(), e);
            status.put(plan.id(), MaintenancePlanRules.status(e, today(),
                    vehicle == null ? null : vehicle.currentMileage(), p.maintenanceAlertDays(), p.maintenanceAlertKm()));
        }
        vehicleById = vById;
        typeById = t.stream().collect(Collectors.toMap(MaintenanceType::id, Function.identity()));
        vehicleList = v;
        typeList = t;
        effective = eff;
        statusById = status;
    }

    private String plate(MaintenancePlan p) {
        Vehicle v = vehicleById.get(p.vehicleId());
        return v == null ? "" : v.registration();
    }

    private String typeName(MaintenancePlan p) {
        MaintenanceType t = typeById.get(p.typeId());
        return t == null ? "" : t.name();
    }

    private String interval(MaintenancePlan p) {
        String days = p.intervalDays() == null ? null : messages.get("plan.every.days", p.intervalDays());
        String km = p.intervalKm() == null ? null : messages.get("plan.every.km", ui.formats().number(p.intervalKm()));
        return days == null ? km : km == null ? days : days + " / " + km;
    }

    private String lastDone(MaintenancePlan p) {
        MaintenancePlan e = effective.getOrDefault(p.id(), p);
        if (e.lastDoneDate() == null) {
            return messages.get("plan.never");
        }
        return ui.formats().date(e.lastDoneDate())
                + (e.lastDoneKm() == null ? "" : " — " + ui.formats().number(e.lastDoneKm()) + " " + messages.get("unit.km"));
    }

    private String nextDue(MaintenancePlan p) {
        MaintenancePlan e = effective.getOrDefault(p.id(), p);
        String date = e.nextDueDate() == null ? null : ui.formats().date(e.nextDueDate());
        String km = e.nextDueKm() == null ? null : ui.formats().number(e.nextDueKm()) + " " + messages.get("unit.km");
        if (date == null && km == null) {
            return "—";
        }
        return date == null ? km : km == null ? date : date + " / " + km;
    }

    private Badge status(MaintenancePlan p) {
        DueStatus s = statusById.getOrDefault(p.id(), DueStatus.NOT_STARTED);
        return new Badge(messages.label(s), switch (s) {
            case OVERDUE -> Tone.DANGER;
            case DUE_SOON -> Tone.WARNING;
            case OK -> Tone.SUCCESS;
            case NOT_STARTED -> Tone.INFO;
            case INACTIVE -> Tone.NEUTRAL;
        });
    }

    @Override
    protected String title() {
        return messages.get("maintenance.tab.plans");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("plans.count", total)
                : messages.get("plans.countFiltered", visible, total);
    }

    @Override
    protected String hint() {
        return messages.get("plans.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("plan.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.PLAN;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("plans.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("plans.empty.text");
    }

    @Override
    protected void configureTable(DataTable<MaintenancePlan> table) {
        table.textColumn(messages.get("plan.vehicle"), this::plate, 100).setMinWidth(90);
        table.textColumn(messages.get("plan.type"), this::typeName, 140).setMinWidth(110);
        table.textColumn(messages.get("plan.interval"), this::interval, 130).setMinWidth(120);
        table.textColumn(messages.get("plan.lastDone"), this::lastDone, 190).setMinWidth(175);
        table.textColumn(messages.get("plan.nextDue"), this::nextDue, 190).setMinWidth(175);
        table.badgeColumn(messages.get("plan.status"), this::status, 130).setMinWidth(120);
        table.searchBy(p -> plate(p) + " " + typeName(p) + " " + messages.label(statusById.get(p.id())));
    }

    @Override
    protected Node filters() {
        CheckBox attention = new CheckBox(messages.get("plans.filter.attention"));
        attention.selectedProperty().addListener((obs, o, only) -> table().filterProperty().set(p -> {
            DueStatus s = statusById.get(p.id());
            return !only || s == DueStatus.OVERDUE || s == DueStatus.DUE_SOON;
        }));
        return attention;
    }

    @Override
    protected EntityEditor<MaintenancePlan> createEditor(MaintenancePlan existing) {
        return new Editor(ui.forms(), messages, this::today, existing, vehicleList, typeList);
    }

    @Override
    protected String savedMessage(MaintenancePlan saved, boolean created) {
        return messages.get(created ? "plan.created" : "plan.updated", plate(saved));
    }

    @Override
    protected Object identityOf(MaintenancePlan p) {
        return p.id();
    }

    private static final class Editor implements EntityEditor<MaintenancePlan> {
        private final Messages messages;
        private final MaintenancePlan existing;
        private final Form form;
        private final ChoiceInput<Vehicle> vehicle;
        private final ChoiceInput<MaintenanceType> type;
        private final NumberInput days;
        private final NumberInput km;
        private final DateInput lastDate;
        private final NumberInput lastKm;
        private final CheckInput active;
        private final TextAreaInput notes;

        Editor(Forms forms, Messages messages, Supplier<LocalDate> today, MaintenancePlan existing,
               List<Vehicle> vehicles, List<MaintenanceType> types) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            Long currentVehicle = existing == null ? null : existing.vehicleId();
            Long currentType = existing == null ? null : existing.typeId();
            vehicle = form.choice("vehicleId", messages.get("plan.vehicle"),
                    Choices.activePlus(vehicles, v -> v.status() != Vehicle.VehicleStatus.SOLD
                            && v.status() != Vehicle.VehicleStatus.INACTIVE, Vehicle::id, currentVehicle),
                    v -> v.registration() + " — " + v.make() + " " + v.model()).required();
            type = form.choice("typeId", messages.get("plan.type"),
                    Choices.activePlus(types, MaintenanceType::active, MaintenanceType::id, currentType),
                    MaintenanceType::name).required();
            form.section(messages.get("plan.section.interval"));
            days = form.number("intervalDays", messages.get("plan.intervalDays")).unit(messages.get("unit.days"))
                    .rule(Rules.positive());
            km = form.number("intervalKm", messages.get("plan.intervalKm")).unit(messages.get("unit.km"))
                    .rule(Rules.positive()).help(messages.get("plan.interval.help"));
            form.section(messages.get("plan.section.reference"));
            lastDate = form.date("lastDoneDate", messages.get("plan.lastDoneDate")).rule(Rules.notAfter(today));
            lastKm = form.number("lastDoneKm", messages.get("plan.lastDoneKm")).unit(messages.get("unit.km"))
                    .help(messages.get("plan.reference.help"));
            active = form.check("active", messages.get("plan.active"));
            notes = form.textArea("notes", messages.get("plan.notes")).maxLength(MaintenancePlanRules.NOTES_MAX);
            if (existing == null) {
                active.setValue(true);
                type.valueProperty().addListener((obs, o, t) -> {     // périodicité proposée par le type
                    if (t != null && days.getValue() == null && km.getValue() == null) {
                        days.setValue(t.defaultIntervalDays());
                        km.setValue(t.defaultIntervalKm());
                    }
                });
            } else {
                vehicle.setValue(Choices.byId(vehicles, Vehicle::id, existing.vehicleId()));
                type.setValue(Choices.byId(types, MaintenanceType::id, existing.typeId()));
                days.setValue(existing.intervalDays());
                km.setValue(existing.intervalKm());
                lastDate.setValue(existing.lastDoneDate());
                lastKm.setValue(existing.lastDoneKm());
                active.setValue(existing.active());
                notes.setValue(existing.notes());
            }
        }

        @Override
        public String title() {
            return messages.get(existing == null ? "plan.create" : "plan.edit");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public MaintenancePlan toEntity() {
            return new MaintenancePlan(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    vehicle.getValue() == null ? null : vehicle.getValue().id(),
                    type.getValue() == null ? null : type.getValue().id(), days.getValue(), km.getValue(),
                    lastDate.getValue(), lastKm.getValue(), active.getValue(), notes.getValue());
        }
    }
}
