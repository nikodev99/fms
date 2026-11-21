package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
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
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.MaintenanceEvent.MaintenanceEventStatus;
import org.gu.fms.domain.maintenance.MaintenanceEvent.MaintenanceKind;
import org.gu.fms.domain.maintenance.MaintenanceEventRules;
import org.gu.fms.domain.maintenance.MaintenanceType;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Entretien › Interventions. Le coût est la charge de l'intervention ; son paiement sera une sortie de
 * trésorerie liée, enregistrée avec le module Transactions. Une intervention erronée est annulée, jamais supprimée.
 */
public final class MaintenanceEventsScreen extends CrudScreen<MaintenanceEvent> {

    private final Clock clock;
    private final AppSettings settings;
    private final VehicleRepository vehicles;
    private final MaintenanceTypeRepository types;
    private volatile List<Vehicle> vehicleList = List.of();
    private volatile List<MaintenanceType> typeList = List.of();
    private volatile Map<Long, Vehicle> vehicleById = Map.of();
    private volatile Map<Long, MaintenanceType> typeById = Map.of();

    @Inject
    public MaintenanceEventsScreen(CrudContext ui, MaintenanceEventService service, VehicleRepository vehicles, MaintenanceTypeRepository types, Clock clock, AppSettings settings) {
        super(ui, service);
        this.vehicles = vehicles;
        this.types = types;
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
        vehicleById = v.stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
        typeById = t.stream().collect(Collectors.toMap(MaintenanceType::id, Function.identity()));
        vehicleList = v;
        typeList = t;
    }

    private String plate(MaintenanceEvent e) {
        Vehicle v = vehicleById.get(e.vehicleId());
        return v == null ? "" : v.registration();
    }

    private String typeName(MaintenanceEvent e) {
        MaintenanceType t = typeById.get(e.typeId());
        return t == null ? "" : t.name();
    }

    @Override
    protected String title() {
        return messages.get("maintenance.tab.events");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("events.count", total)
                : messages.get("events.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("event.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.MAINTENANCE;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("events.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("events.empty.text");
    }

    @Override
    protected boolean canOpen(MaintenanceEvent e) {
        return e.status() == MaintenanceEventStatus.RECORDED;
    }

    @Override
    protected void configureTable(DataTable<MaintenanceEvent> table) {
        table.dateColumn(messages.get("event.date"), MaintenanceEvent::date, 100).setMinWidth(95);
        table.textColumn(messages.get("event.vehicle"), this::plate, 100).setMinWidth(90);
        table.textColumn(messages.get("event.type"), this::typeName, 180).setMinWidth(150);
        table.badgeColumn(messages.get("event.kind"), e -> new Badge(messages.label(e.kind()),
                e.kind() == MaintenanceKind.REPAIR ? Tone.WARNING : Tone.INFO), 110);
        table.textColumn(messages.get("event.supplier.short"), MaintenanceEvent::supplier, 160).setMinWidth(130);
        table.numberColumn(messages.get("event.mileage.short"), MaintenanceEvent::mileage, messages.get("unit.km"), 100);
        table.moneyColumn(messages.get("event.cost"), MaintenanceEvent::cost, 120);
        table.badgeColumn(messages.get("common.state"), e -> e.status() == MaintenanceEventStatus.CANCELLED
                ? new Badge(messages.label(e.status()), Tone.DANGER)
                : new Badge(messages.label(e.status()), Tone.SUCCESS), 110);
        table.searchBy(e -> String.join(" ", plate(e), typeName(e), messages.label(e.kind()),
                String.valueOf(e.supplier()), String.valueOf(e.description()), String.valueOf(e.cancelReason())));
    }

    @Override
    protected EntityEditor<MaintenanceEvent> createEditor(MaintenanceEvent existing) {
        return new Editor(ui.forms(), messages, this::today, existing, vehicleList, typeList);
    }

    @Override
    protected String savedMessage(MaintenanceEvent saved, boolean created) {
        if (saved.status() == MaintenanceEventStatus.CANCELLED) {
            return messages.get("event.cancelled");
        }
        return messages.get(created ? "event.created" : "event.updated", plate(saved));
    }

    @Override
    protected Object identityOf(MaintenanceEvent e) {
        return e.id();
    }

    private static final class Editor implements EntityEditor<MaintenanceEvent> {
        private final Messages messages;
        private final MaintenanceEvent existing;
        private final Form form;
        private final ChoiceInput<Vehicle> vehicle;
        private final ChoiceInput<MaintenanceType> type;
        private final DateInput date;
        private final NumberInput mileage;
        private final MoneyInput cost;
        private final TextInput supplier;
        private final TextAreaInput description;
        private final ChoiceInput<MaintenanceEventStatus> status;
        private final TextAreaInput reason;
        private final TextAreaInput notes;

        Editor(Forms forms, Messages messages, Supplier<LocalDate> today, MaintenanceEvent existing,
               List<Vehicle> vehicles, List<MaintenanceType> types) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            Long currentVehicle = existing == null ? null : existing.vehicleId();
            Long currentType = existing == null ? null : existing.typeId();
            form.section(messages.get("event.section.what"));
            vehicle = form.choice("vehicleId", messages.get("event.vehicle"),
                    Choices.activePlus(vehicles, v -> v.status() != VehicleStatus.SOLD
                            && v.status() != VehicleStatus.INACTIVE, Vehicle::id, currentVehicle),
                    v -> v.registration() + " — " + v.make() + " " + v.model()).required();
            type = form.choice("typeId", messages.get("event.type"),
                            Choices.activePlus(types, MaintenanceType::active, MaintenanceType::id, currentType),
                            t -> t.name() + " (" + messages.label(t.kind()) + ")").required()
                    .help(messages.get("event.type.help"));
            date = form.date("date", messages.get("event.date")).required().rule(Rules.notAfter(today));
            mileage = form.number("mileage", messages.get("event.mileage")).unit(messages.get("unit.km"))
                    .help(messages.get("event.mileage.help"));
            form.section(messages.get("event.section.cost"));
            cost = form.money("cost", messages.get("event.cost")).required().help(messages.get("event.cost.help"));
            supplier = form.text("supplier", messages.get("event.supplier")).maxLength(MaintenanceEventRules.SUPPLIER_MAX);
            description = form.textArea("description", messages.get("event.description"))
                    .maxLength(MaintenanceEventRules.DESCRIPTION_MAX).rows(2);
            status = form.choice("status", messages.get("event.status"), MaintenanceEventStatus.class).required()
                    .shownWhen(Bindings.createBooleanBinding(() -> existing != null));
            reason = form.textArea("cancelReason", messages.get("event.cancelReason"))
                    .maxLength(MaintenanceEventRules.REASON_MAX).rows(2).help(messages.get("event.cancelReason.help"));
            reason.shownWhen(Bindings.createBooleanBinding(
                    () -> status.getValue() == MaintenanceEventStatus.CANCELLED, status.valueProperty()));
            notes = form.textArea("notes", messages.get("event.notes")).maxLength(MaintenanceEventRules.NOTES_MAX);
            if (existing == null) {
                date.setValue(today.get());
                status.setValue(MaintenanceEventStatus.RECORDED);
            } else {
                vehicle.setValue(Choices.byId(vehicles, Vehicle::id, existing.vehicleId())).readOnly(true);
                type.setValue(Choices.byId(types, MaintenanceType::id, existing.typeId()));
                date.setValue(existing.date());
                mileage.setValue(existing.mileage());
                cost.setValue(existing.cost());
                supplier.setValue(existing.supplier());
                description.setValue(existing.description());
                status.setValue(existing.status());
                notes.setValue(existing.notes());
            }
        }

        @Override
        public String title() {
            return messages.get(existing == null ? "event.create" : "event.edit");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public MaintenanceEvent toEntity() {
            MaintenanceType t = type.getValue();
            return new MaintenanceEvent(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    vehicle.getValue() == null ? null : vehicle.getValue().id(), t == null ? null : t.id(),
                    t == null ? null : t.kind(), date.getValue(), mileage.getValue(), description.getValue(),
                    supplier.getValue(), cost.getValue(), status.getValue(), reason.getValue(), notes.getValue());
        }
    }
}
