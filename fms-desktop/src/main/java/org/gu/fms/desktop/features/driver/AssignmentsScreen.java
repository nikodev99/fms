package org.gu.fms.desktop.features.driver;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
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
import org.gu.fms.domain.driver.AssignmentRules;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chauffeurs › Affectations. Pour changer de chauffeur principal : terminer l'affectation en cours
 * (date de fin), puis créer la nouvelle. L'historique reste complet.
 */
public final class AssignmentsScreen extends CrudScreen<DriverAssignment> {

    private final Clock clock;
    private final AppSettings settings;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private volatile List<Driver> driverList = List.of();
    private volatile List<Vehicle> vehicleList = List.of();
    private volatile Map<Long, Driver> driverById = Map.of();
    private volatile Map<Long, Vehicle> vehicleById = Map.of();

    @Inject
    public AssignmentsScreen(CrudContext ui, AssignmentService service, DriverRepository drivers, VehicleRepository vehicles, Clock clock, AppSettings settings) {
        super(ui, service);
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.clock = clock;
        this.settings = settings;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        List<Driver> d = drivers.findAll();
        List<Vehicle> v = vehicles.findAll();
        driverById = d.stream().collect(Collectors.toMap(Driver::id, Function.identity()));
        vehicleById = v.stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
        driverList = d;
        vehicleList = v;
    }

    private String driverName(DriverAssignment a) {
        Driver d = driverById.get(a.driverId());
        return d == null ? "" : d.displayName();
    }

    private String plate(DriverAssignment a) {
        Vehicle v = vehicleById.get(a.vehicleId());
        return v == null ? "" : v.registration() + " — " + v.make() + " " + v.model();
    }

    private Badge state(DriverAssignment a) {
        LocalDate today = today();
        if (a.startDate().isAfter(today)) {
            return new Badge(messages.get("assignment.state.upcoming"), Tone.INFO);
        }
        if (a.endDate() != null && a.endDate().isBefore(today)) {
            return new Badge(messages.get("assignment.state.ended"), Tone.NEUTRAL);
        }
        return new Badge(messages.get("assignment.state.current"), Tone.SUCCESS);
    }

    @Override
    protected String title() {
        return messages.get("drivers.tab.assignments");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("assignments.count", total)
                : messages.get("assignments.countFiltered", visible, total);
    }

    @Override
    protected String hint() {
        return messages.get("assignments.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("assignment.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.ASSIGNMENT;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("assignments.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("assignments.empty.text");
    }

    @Override
    protected void configureTable(DataTable<DriverAssignment> table) {
        table.textColumn(messages.get("assignment.driver"), this::driverName, 180).setMinWidth(130);
        table.textColumn(messages.get("assignment.vehicle"), this::plate, 210).setMinWidth(140);
        table.badgeColumn(messages.get("assignment.role"), a -> new Badge(messages.label(a.role()),
                a.role() == DriverAssignment.AssignmentRole.PRIMARY ? Tone.INFO : Tone.NEUTRAL), 130);
        table.dateColumn(messages.get("assignment.startDate"), DriverAssignment::startDate, 110);
        table.textColumn(messages.get("assignment.endDate"), a -> a.endDate() == null
                ? messages.get("assignment.endDate.none") : ui.formats().date(a.endDate()), 110);
        table.badgeColumn(messages.get("common.state"), this::state, 110);
        table.searchBy(a -> driverName(a) + " " + plate(a) + " " + messages.label(a.role()));
    }

    @Override
    protected Node filters() {
        CheckBox currentOnly = new CheckBox(messages.get("assignments.filter.current"));
        currentOnly.selectedProperty().addListener((obs, o, only) -> table().filterProperty().set(
                a -> !only || a.isActiveOn(today()) || a.startDate().isAfter(today())));
        return currentOnly;
    }

    @Override
    protected EntityEditor<DriverAssignment> createEditor(DriverAssignment existing) {
        return new Editor(ui.forms(), messages, existing, driverList, vehicleList, today());
    }

    @Override
    protected String savedMessage(DriverAssignment saved, boolean created) {
        return messages.get(created ? "assignment.created" : "assignment.updated", driverName(saved));
    }

    @Override
    protected Object identityOf(DriverAssignment a) {
        return a.id();
    }

    private static final class Editor implements EntityEditor<DriverAssignment> {
        private final Messages messages;
        private final DriverAssignment existing;
        private final Form form;
        private final ChoiceInput<Driver> driver;
        private final ChoiceInput<Vehicle> vehicle;
        private final ChoiceInput<DriverAssignment.AssignmentRole> role;
        private final DateInput start;
        private final DateInput end;
        private final TextAreaInput notes;

        Editor(Forms forms, Messages messages, DriverAssignment existing, List<Driver> drivers, List<Vehicle> vehicles,
               LocalDate today) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            Long currentDriver = existing == null ? null : existing.driverId();
            Long currentVehicle = existing == null ? null : existing.vehicleId();
            driver = form.choice("driverId", messages.get("assignment.driver"),
                    Choices.activePlus(drivers, d -> d.status().canBeAssigned(), Driver::id, currentDriver),
                    Driver::displayName).required();
            vehicle = form.choice("vehicleId", messages.get("assignment.vehicle"),
                    Choices.activePlus(vehicles, v -> v.status() != Vehicle.VehicleStatus.SOLD
                            && v.status() != Vehicle.VehicleStatus.INACTIVE, Vehicle::id, currentVehicle),
                    v -> v.registration() + " — " + v.make() + " " + v.model()).required();
            role = form.choice("role", messages.get("assignment.role"), DriverAssignment.AssignmentRole.class).required()
                    .help(messages.get("assignment.role.help"));
            start = form.date("startDate", messages.get("assignment.startDate")).required();
            end = form.date("endDate", messages.get("assignment.endDate")).help(messages.get("assignment.endDate.help"));
            notes = form.textArea("notes", messages.get("assignment.notes")).maxLength(AssignmentRules.NOTES_MAX);
            if (existing == null) {
                role.setValue(DriverAssignment.AssignmentRole.PRIMARY);
                start.setValue(today);
            } else {
                driver.setValue(Choices.byId(drivers, Driver::id, existing.driverId())).readOnly(true);
                vehicle.setValue(Choices.byId(vehicles, Vehicle::id, existing.vehicleId())).readOnly(true);
                role.setValue(existing.role()).readOnly(true);
                start.setValue(existing.startDate());
                end.setValue(existing.endDate());
                notes.setValue(existing.notes());
            }
        }

        @Override
        public String title() {
            return messages.get(existing == null ? "assignment.create" : "assignment.edit");
        }

        @Override
        public String subtitle() {
            return existing == null ? null : messages.get("assignment.edit.subtitle");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public DriverAssignment toEntity() {
            return new DriverAssignment(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    driver.getValue() == null ? null : driver.getValue().id(),
                    vehicle.getValue() == null ? null : vehicle.getValue().id(),
                    role.getValue(), start.getValue(), end.getValue(), notes.getValue());
        }
    }
}
