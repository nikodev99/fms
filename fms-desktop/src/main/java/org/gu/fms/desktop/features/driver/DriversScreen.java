package org.gu.fms.desktop.features.driver;

import jakarta.inject.Inject;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.driver.DriverRules;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Chauffeurs › liste et fiche. La colonne « Véhicule » montre les affectations en cours aujourd'hui. */
public final class DriversScreen extends CrudScreen<Driver> {

    private final Clock clock;
    private final AppSettings settings;
    private final AssignmentRepository assignments;
    private final VehicleRepository vehicles;
    private final FleetPreferencesService prefs;
    private volatile Map<Long, String> currentVehicle = Map.of();
    private volatile long alertDays = 30;

    @Inject
    public DriversScreen(CrudContext ui, DriverService service, AssignmentRepository assignments, VehicleRepository vehicles, FleetPreferencesService prefs, Clock clock, AppSettings settings) {
        super(ui, service);
        this.assignments = assignments;
        this.vehicles = vehicles;
        this.prefs = prefs;
        this.clock = clock;
        this.settings = settings;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        LocalDate today = today();
        Map<Long, String> plates = vehicles.findAll().stream()
                .collect(Collectors.toMap(Vehicle::id, Vehicle::registration));
        Map<Long, List<String>> byDriver = new HashMap<>();
        for (DriverAssignment a : assignments.findAll()) {
            if (a.isActiveOn(today)) {
                byDriver.computeIfAbsent(a.driverId(), k -> new ArrayList<>())
                        .add(plates.getOrDefault(a.vehicleId(), "?") + " (" + messages.label(a.role()) + ")");
            }
        }
        Map<Long, String> text = new HashMap<>();
        byDriver.forEach((id, list) -> text.put(id, String.join(", ", list)));
        currentVehicle = text;
        alertDays = prefs.load().documentAlertDays();
    }

    @Override
    protected String title() {
        return messages.get("drivers.tab.list");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("drivers.count", total)
                : messages.get("drivers.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("driver.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.DRIVERS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("drivers.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("drivers.empty.text");
    }

    private String pay(Driver d) {
        if (d.payScheme() == null) {
            return "";
        }
        return switch (d.payScheme()) {
            case FIXED -> messages.get("driver.pay.fixed", ui.formats().money(d.fixedSalary()));
            case PERCENTAGE -> messages.get("driver.pay.percent", ui.formats().percent(d.commissionRate()));
            case FIXED_PLUS_COMMISSION -> messages.get("driver.pay.mixed", ui.formats().money(d.fixedSalary()),
                    ui.formats().percent(d.commissionRate()));
        };
    }

    private Badge licence(Driver d) {
        LocalDate today = today();
        if (d.licenceExpiry() == null) {
            return new Badge(messages.get("driver.licence.unknown"), Tone.NEUTRAL);
        }
        if (DriverRules.licenceExpired(d, today)) {
            return new Badge(messages.get("driver.licence.expired"), Tone.DANGER);
        }
        if (DriverRules.licenceExpiresWithin(d, today, alertDays)) {
            return new Badge(messages.get("driver.licence.soon", ui.formats().date(d.licenceExpiry())), Tone.WARNING);
        }
        return new Badge(messages.get("driver.licence.valid", ui.formats().date(d.licenceExpiry())), Tone.SUCCESS);
    }

    @Override
    protected void configureTable(DataTable<Driver> table) {
        table.textColumn(messages.get("drivers.column.name"), Driver::displayName, 170).setMinWidth(130);
        table.textColumn(messages.get("driver.phone"), Driver::phone, 115).setMinWidth(100);
        table.textColumn(messages.get("drivers.column.vehicle"), d -> currentVehicle.getOrDefault(d.id(), ""), 160)
                .setMinWidth(110);
        table.textColumn(messages.get("drivers.column.pay"), this::pay, 190).setMinWidth(150);
        table.moneyColumn(messages.get("drivers.column.dailyTarget"), Driver::dailyTarget, 120).setMinWidth(100);
        table.badgeColumn(messages.get("drivers.column.licence"), this::licence, 175).setMinWidth(165);
        table.badgeColumn(messages.get("driver.status"), d -> new Badge(messages.label(d.status()), tone(d.status())), 100)
                .setMinWidth(90);
        table.searchBy(d -> String.join(" ", d.displayName(), d.phone(), String.valueOf(d.licenceNumber()),
                currentVehicle.getOrDefault(d.id(), ""), messages.label(d.status())));
    }

    @Override
    protected Node filters() {
        List<Driver.DriverStatus> options = new ArrayList<>();
        options.add(null);
        options.addAll(List.of(Driver.DriverStatus.values()));
        ComboBox<Driver.DriverStatus> combo = new ComboBox<>(FXCollections.observableArrayList(options));
        combo.getStyleClass().add("filter-combo");
        combo.setButtonCell(cell());
        combo.setCellFactory(list -> cell());
        combo.valueProperty().addListener((obs, o, selected) ->
                table().filterProperty().set(d -> selected == null || d.status() == selected));
        return combo;
    }

    private ListCell<Driver.DriverStatus> cell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Driver.DriverStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? messages.get("drivers.filter.all") : messages.label(item));
            }
        };
    }

    @Override
    protected EntityEditor<Driver> createEditor(Driver existing) {
        return new DriverEditor(ui.forms(), messages, this::today, existing);
    }

    @Override
    protected String savedMessage(Driver saved, boolean created) {
        return messages.get(created ? "driver.created" : "driver.updated", saved.displayName());
    }

    @Override
    protected Object identityOf(Driver d) {
        return d.id();
    }

    static Tone tone(Driver.DriverStatus status) {
        return switch (status) {
            case ACTIVE -> Tone.SUCCESS;
            case SUSPENDED -> Tone.WARNING;
            case LEFT -> Tone.NEUTRAL;
        };
    }
}

