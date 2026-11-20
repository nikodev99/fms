package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.VehicleCategory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class VehicleScreen extends CrudScreen<Vehicle> {
    private final Clock clock;
    private final AppSettings settings;
    private final VehicleCategoryService categoryService;
    private final FleetPreferencesService fleetPrefs;
    private volatile List<VehicleCategory> categories = List.of();
    private volatile Map<Long, VehicleCategory> categoryById = Map.of();
    private volatile Money defaultThreshold;

    @Inject
    public VehicleScreen(CrudContext ui, VehicleService service, VehicleCategoryService categoryService,
                          FleetPreferencesService fleetPrefs, Clock clock, AppSettings settings) {
        super(ui, service);
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
        table.numberColumn(messages.get("vehicle.currentMileage"), Vehicle::currentMileage, messages.get("unit.km"), 120)
                .setMinWidth(100);
        table.moneyColumn(messages.get("vehicles.column.purchasePrice"), Vehicle::purchasePrice, 130).setMinWidth(100);
        table.searchBy(v -> String.join(" ", v.registration(), v.make(), v.model(),
                categoryName(v), messages.label(v.status())));
    }

    @Override
    protected Node filters() {
        List<Vehicle.VehicleStatus> options = new ArrayList<>();
        options.add(null);
        options.addAll(List.of(Vehicle.VehicleStatus.values()));
        ComboBox<Vehicle.VehicleStatus> status = new ComboBox<>(FXCollections.observableArrayList(options));
        status.getStyleClass().add("filter-combo");
        status.setButtonCell(statusCell());
        status.setCellFactory(list -> statusCell());
        status.valueProperty().addListener((obs, o, selected) ->
                table().filterProperty().set(v -> selected == null || v.status() == selected));
        return status;
    }

    private ListCell<Vehicle.VehicleStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Vehicle.VehicleStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? messages.get("vehicles.filter.allStatuses") : messages.label(item));
            }
        };
    }

    @Override
    protected EntityEditor<Vehicle> createEditor(Vehicle existing) {
        return new VehicleEditor(ui.forms(), messages, () -> LocalDate.ofInstant(clock.instant(), settings.zone()), categories, defaultThreshold, existing);
    }

    @Override
    protected String savedMessage(Vehicle saved, boolean created) {
        return messages.get(created ? "vehicle.created" : "vehicle.updated", saved.registration());
    }

    @Override
    protected Object identityOf(Vehicle vehicle) {
        return vehicle.id();
    }

    static Tone tone(Vehicle.VehicleStatus status) {
        return switch (status) {
            case ACTIVE -> Tone.SUCCESS;
            case UNDER_MAINTENANCE -> Tone.WARNING;
            case IMMOBILISED -> Tone.DANGER;
            case TEMPORARILY_UNAVAILABLE, SOLD, INACTIVE -> Tone.NEUTRAL;
        };
    }
}
