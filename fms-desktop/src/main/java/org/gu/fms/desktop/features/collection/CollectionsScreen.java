package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.filter.MonthPicker;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.collection.CollectionEntry;
import org.gu.fms.domain.collection.CollectionKind;
import org.gu.fms.domain.collection.CollectionRules;
import org.gu.fms.domain.collection.CollectionStatus;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.finance.AccountType;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Versements › Versements reçus (instructions §4.3). Saisie rapide pour le caissier : le véhicule et le montant
 * attendu viennent de l'affectation du chauffeur. Versement du jour modifiable (motif) tant que la journée n'est ni
 * pointée ni clôturée ; sinon avoir.
 */
public final class CollectionsScreen extends CrudScreen<CollectionEntry> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CollectionService collections;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final AccountRepository accounts;
    private final AssignmentRepository assignments;
    private final AppSettings settings;
    private final Clock clock;
    private final MonthPicker month;
    private final CheckBox todayOnly = new CheckBox();
    private volatile Map<Long, Driver> driverById = Map.of();
    private volatile Map<Long, Vehicle> vehicleById = Map.of();
    private volatile Map<Long, FinancialAccount> accountById = Map.of();
    private volatile Map<Long, List<DriverAssignment>> activeToday = Map.of();
    private volatile Map<Long, String> lockReasons = Map.of();
    private KpiStrip.Kpi receivedToday;
    private KpiStrip.Kpi receivedMonth;
    private KpiStrip.Kpi expectedToday;

    @Inject
    public CollectionsScreen(CrudContext ui, CollectionService service, DriverRepository drivers,
                             VehicleRepository vehicles, AccountRepository accounts, AssignmentRepository assignments,
                             AppSettings settings, Clock clock) {
        super(ui, service);
        this.collections = service;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.accounts = accounts;
        this.assignments = assignments;
        this.settings = settings;
        this.clock = clock;
        this.month = new MonthPicker(YearMonth.from(today()), 24, settings.locale());
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        driverById = drivers.findAll().stream().collect(Collectors.toMap(Driver::id, Function.identity()));
        vehicleById = vehicles.findAll().stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
        accountById = accounts.findAll().stream().collect(Collectors.toMap(FinancialAccount::id, Function.identity()));
        activeToday = assignments.findActiveOn(today()).stream()
                .collect(Collectors.groupingBy(DriverAssignment::driverId));
    }

    @Override
    protected List<CollectionEntry> load() {
        YearMonth m = month.getValue() == null ? YearMonth.from(today()) : month.getValue();
        List<CollectionEntry> items = collections.findBetween(m.atDay(1), m.atEndOfMonth());
        Map<Long, String> locks = new HashMap<>();
        for (CollectionEntry c : items) {
            if (!c.isCreditNote() && c.status() == CollectionStatus.VALID && c.date().equals(today())) {
                String reason = collections.lockReason(c);
                locks.put(c.id(), reason == null ? "" : reason);
            }
        }
        lockReasons = locks;
        return items;
    }

    private boolean editable(CollectionEntry c) {
        return "".equals(lockReasons.get(c.id()));
    }

    private String driverName(Long id) {
        Driver d = id == null ? null : driverById.get(id);
        return d == null ? "" : d.displayName();
    }

    private String plate(Long id) {
        Vehicle v = id == null ? null : vehicleById.get(id);
        return v == null ? "" : v.registration();
    }

    private String account(Long id) {
        FinancialAccount a = id == null ? null : accountById.get(id);
        return a == null ? "" : a.name();
    }

    private Money expectedToday(long driverId) {
        long total = activeToday.getOrDefault(driverId, List.of()).stream().mapToLong(DriverAssignment::expectedMinor).sum();
        return Money.of(total, settings.currency());
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        receivedToday = strip.add(messages.get("collections.kpi.today"));
        expectedToday = strip.add(messages.get("collections.kpi.expectedToday"));
        receivedMonth = strip.add(messages.get("collections.kpi.month"));
        return strip;
    }

    @Override
    protected void loaded(List<CollectionEntry> items) {
        LocalDate today = today();
        long day = items.stream().filter(c -> c.date().equals(today)).mapToLong(CollectionEntry::signedMinor).sum();
        long count = items.stream().filter(c -> c.date().equals(today) && !c.isCreditNote()).count();
        long all = items.stream().mapToLong(CollectionEntry::signedMinor).sum();
        long expected = activeToday.values().stream().flatMap(List::stream).mapToLong(DriverAssignment::expectedMinor).sum();
        var c = settings.currency();
        receivedToday.set(ui.formats().money(Money.of(day, c)), messages.get("collections.kpi.today.caption", count),
                Tone.SUCCESS);
        Money gap = Money.of(day - expected, c);
        expectedToday.set(ui.formats().money(Money.of(expected, c)),
                messages.get("collections.kpi.expectedToday.caption", ui.formats().money(gap)),
                gap.isNegative() ? Tone.WARNING : Tone.NEUTRAL);
        receivedMonth.set(ui.formats().money(Money.of(all, c)), month.getConverter().toString(month.getValue()),
                Tone.NEUTRAL);
    }

    @Override
    protected Node filters() {
        month.valueProperty().addListener((obs, o, m) -> refresh());
        todayOnly.setText(messages.get("collections.filter.today"));
        todayOnly.selectedProperty().addListener((obs, o, only) ->
                table().filterProperty().set(c -> !only || c.date().equals(today())));
        return new HBox(12, month, todayOnly);
    }

    @Override
    protected String title() {
        return messages.get("collections.tab.list");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("collections.count", total)
                : messages.get("collections.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("collection.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.COLLECTIONS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("collections.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("collections.empty.text");
    }

    @Override
    protected void configureTable(DataTable<CollectionEntry> table) {
        ZoneId zone = settings.zone();
        table.dateColumn(messages.get("collection.date"), CollectionEntry::date, 100).setMinWidth(95);
        table.textColumn(messages.get("collection.time"), c -> TIME.format(c.recordedAt().atZone(zone)), 70);
        table.textColumn(messages.get("collection.driver"), c -> driverName(c.driverId()), 180).setMinWidth(130);
        table.textColumn(messages.get("collection.vehicle"), c -> plate(c.vehicleId()), 100);
        table.textColumn(messages.get("collection.account"), c -> account(c.accountId()), 140).setMinWidth(100);
        table.moneyColumn(messages.get("collection.amount"), c -> Money.of(c.signedMinor(), c.amount().currency()), 120);
        table.badgeColumn(messages.get("collection.kind"), c -> c.isCreditNote()
                ? new Badge(messages.label(c.kind()), Tone.WARNING)
                : c.status() == CollectionStatus.CREDITED
                ? new Badge(messages.label(c.status()), Tone.DANGER)
                : new Badge(messages.label(c.kind()), Tone.SUCCESS), 120);
        table.textColumn(messages.get("collection.cashier"), CollectionEntry::cashier, 130);
        table.searchBy(c -> String.join(" ", driverName(c.driverId()), plate(c.vehicleId()), account(c.accountId()),
                String.valueOf(c.notes()), String.valueOf(c.reason()), c.cashier()));
    }

    @Override
    protected EntityEditor<CollectionEntry> createEditor(CollectionEntry existing) {
        return existing == null || editable(existing) ? new Editor(existing) : new Detail(existing);
    }

    @Override
    protected List<Button> editorActions(CollectionEntry existing) {
        if (existing.isCreditNote() || existing.status() != CollectionStatus.VALID) {
            return List.of();
        }
        Button credit = new Button(messages.get("collection.creditNote"), new FmsIcon(AppIcon.CREDIT_NOTE, 16));
        credit.getStyleClass().add("danger");
        credit.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                ui.dialogs().reason(messages.get("collection.creditNote.title"),
                                messages.get("collection.creditNote.text", ui.formats().money(existing.amount()),
                                        driverName(existing.driverId()), ui.formats().date(existing.date())),
                                messages.get("common.reason"), messages.get("collection.creditNote"), Tone.DANGER)
                        .thenAccept(reason -> reason.ifPresent(r -> ui.run(() -> collections.creditNote(existing.id(), r),
                                c -> messages.get("collection.creditNote.done"), c -> refresh())));
            }
        }));
        return List.of(credit);
    }

    @Override
    protected CompletableFuture<Boolean> beforeSave(CollectionEntry entity, boolean creating) {
        boolean duplicate = creating && table().items().stream().anyMatch(c -> !c.isCreditNote()
                && c.status() == CollectionStatus.VALID && Objects.equals(c.driverId(), entity.driverId())
                && Objects.equals(c.date(), entity.date()) && Objects.equals(c.amount(), entity.amount()));
        if (!duplicate) {
            return CompletableFuture.completedFuture(true);
        }
        return ui.dialogs().confirm(messages.get("collection.duplicate.title"),
                messages.get("collection.duplicate.text", driverName(entity.driverId()),
                        ui.formats().money(entity.amount()), ui.formats().date(entity.date())),
                messages.get("collection.duplicate.confirm"), Tone.WARNING);
    }

    @Override
    protected String savedMessage(CollectionEntry saved, boolean created) {
        return messages.get(created ? "collection.created" : "collection.updated", ui.formats().money(saved.amount()),
                driverName(saved.driverId()));
    }

    @Override
    protected Object identityOf(CollectionEntry c) {
        return c.id();
    }

    private final class Editor implements EntityEditor<CollectionEntry> {
        private final CollectionEntry existing;
        private final Form form = ui.forms().create();
        private final DateInput date;
        private final ChoiceInput<Driver> driver;
        private final ChoiceInput<Vehicle> vehicle;
        private final ChoiceInput<FinancialAccount> account;
        private final MoneyInput amount;
        private final TextAreaInput reason;
        private final TextAreaInput notes;

        Editor(CollectionEntry existing) {
            this.existing = existing;
            LocalDate today = today();
            Long currentDriver = existing == null ? null : existing.driverId();
            Long currentVehicle = existing == null ? null : existing.vehicleId();
            Long currentAccount = existing == null ? null : existing.accountId();
            date = form.date("date", messages.get("collection.date")).required().rule(Rules.notAfter(() -> today));
            driver = form.choice("driverId", messages.get("collection.driver"),
                    Choices.activePlus(List.copyOf(driverById.values()), d -> d.status().canBeAssigned(), Driver::id,
                            currentDriver), Driver::displayName).required();
            vehicle = form.choice("vehicleId", messages.get("collection.vehicle"),
                            Choices.activePlus(List.copyOf(vehicleById.values()), v -> v.status() != VehicleStatus.SOLD
                                    && v.status() != VehicleStatus.INACTIVE, Vehicle::id, currentVehicle),
                            v -> v.registration() + " — " + v.make() + " " + v.model()).required()
                    .help(messages.get("collection.vehicle.help"));
            account = form.choice("accountId", messages.get("collection.account"),
                    Choices.activePlus(List.copyOf(accountById.values()), FinancialAccount::active, FinancialAccount::id,
                            currentAccount), FinancialAccount::name).required();
            amount = form.money("amount", messages.get("collection.amount")).required()
                    .rule(Rules.that(Money::isPositive, "validation.positive"));
            reason = form.textArea("reason", messages.get("collection.editReason"))
                    .maxLength(CollectionRules.REASON_MAX).rows(2).span(2);
            notes = form.textArea("notes", messages.get("collection.notes")).maxLength(CollectionRules.NOTES_MAX)
                    .rows(2).span(2);
            driver.valueProperty().addListener((obs, o, d) -> onDriver(d));
            if (existing == null) {
                reason.shownWhen(javafx.beans.binding.Bindings.createBooleanBinding(() -> false));
                date.setValue(today);
                accountById.values().stream().filter(a -> a.active() && a.type() == AccountType.CASH).findFirst()
                        .or(() -> accountById.values().stream().filter(FinancialAccount::active).findFirst())
                        .ifPresent(account::setValue);
            } else {
                reason.required().help(messages.get("collection.editReason.help"));
                date.setValue(existing.date()).readOnly(true);
                driver.setValue(driverById.get(existing.driverId()));
                vehicle.setValue(vehicleById.get(existing.vehicleId()));
                account.setValue(accountById.get(existing.accountId()));
                amount.setValue(existing.amount());
                notes.setValue(existing.notes());
            }
        }

        /** Le véhicule de l'affectation principale du jour, et l'attendu du chauffeur en aide. */
        private void onDriver(Driver d) {
            if (d == null) {
                amount.help(null);
                return;
            }
            List<DriverAssignment> list = activeToday.getOrDefault(d.id(), List.of());
            list.stream().filter(a -> a.role() == DriverAssignment.AssignmentRole.PRIMARY).findFirst()
                    .or(() -> list.stream().findFirst())
                    .map(a -> vehicleById.get(a.vehicleId())).ifPresent(vehicle::setValue);
            Money expected = expectedToday(d.id());
            amount.help(expected.isZero() ? messages.get("collection.expected.none")
                    : messages.get("collection.expected", ui.formats().money(expected)));
        }

        @Override
        public String title() {
            return existing == null ? messages.get("collection.create") : messages.get("collection.edit");
        }

        @Override
        public String subtitle() {
            return existing == null ? null : messages.get("collection.edit.subtitle");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public CollectionEntry toEntity() {
            return new CollectionEntry(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    date.getValue(), existing == null ? null : existing.recordedAt(),
                    driver.getValue() == null ? null : driver.getValue().id(),
                    vehicle.getValue() == null ? null : vehicle.getValue().id(),
                    account.getValue() == null ? null : account.getValue().id(), amount.getValue(),
                    CollectionKind.COLLECTION, CollectionStatus.VALID, null,
                    existing == null ? null : existing.transactionId(), null,
                    existing == null ? null : reason.getValue(), notes.getValue());
        }
    }

    private final class Detail implements EntityEditor<CollectionEntry> {
        private final CollectionEntry c;

        Detail(CollectionEntry c) {
            this.c = c;
        }

        @Override
        public String title() {
            return messages.get(c.isCreditNote() ? "collection.detail.creditNote" : "collection.detail",
                    ui.formats().date(c.date()));
        }

        @Override
        public String subtitle() {
            String lock = lockReasons.get(c.id());
            if (lock != null && !lock.isEmpty()) {
                return messages.get(lock);
            }
            return c.isCreditNote() || c.status() != CollectionStatus.VALID ? null
                    : messages.get("collection.locked.pastDay");
        }

        @Override
        public Node header() {
            DetailList d = new DetailList();
            d.add(messages.get("collection.driver"), driverName(c.driverId()));
            d.add(messages.get("collection.vehicle"), plate(c.vehicleId()));
            d.add(messages.get("collection.account"), account(c.accountId()));
            d.add(messages.get("collection.amount"), ui.formats().money(Money.of(c.signedMinor(), c.amount().currency())));
            d.add(messages.get("collection.kind"), messages.label(c.kind()));
            d.add(messages.get("common.state"), messages.label(c.status()),
                    c.status() == CollectionStatus.VALID ? Tone.SUCCESS : Tone.DANGER);
            d.add(messages.get("common.reason"), c.reason());
            d.add(messages.get("collection.notes"), c.notes());
            d.add(messages.get("common.recordedBy"), c.cashier() + " — " + ui.formats().dateTime(c.recordedAt()));
            return d;
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public Form form() {
            return null;
        }

        @Override
        public CollectionEntry toEntity() {
            return c;
        }
    }
}
