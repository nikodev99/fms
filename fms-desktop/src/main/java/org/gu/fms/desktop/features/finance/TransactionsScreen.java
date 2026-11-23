package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.gu.fms.desktop.features.driver.DriverRepository;
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
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transactions › Grand livre : toutes les entrées et sorties de trésorerie du mois, quelle que soit leur origine.
 * Saisie directe (on choisit le sens, puis une catégorie de ce sens) ; une transaction enregistrée ne se modifie
 * pas, elle se contre-passe.
 */
public final class TransactionsScreen extends CrudScreen<FinancialTransaction> {

    private final TransactionService transactions;
    private final ReversalService reversals;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final AppSettings settings;
    private final Clock clock;
    private final MonthPicker month;
    private final ComboBox<FinancialAccount> accountFilter = new ComboBox<>();
    private final ComboBox<TransactionDirection> directionFilter = new ComboBox<>();
    private volatile Map<Long, TransactionCategory> categoryById = Map.of();
    private volatile Map<Long, FinancialAccount> accountById = Map.of();
    private volatile Map<Long, Vehicle> vehicleById = Map.of();
    private volatile Map<Long, Driver> driverById = Map.of();
    private volatile YearMonth loadedMonth;
    private KpiStrip.Kpi inflows;
    private KpiStrip.Kpi outflows;
    private KpiStrip.Kpi net;

    @Inject
    public TransactionsScreen(CrudContext ui, TransactionService service, ReversalService reversals,
                              CategoryRepository categories, AccountRepository accounts, VehicleRepository vehicles,
                              DriverRepository drivers, AppSettings settings, Clock clock) {
        super(ui, service);
        this.transactions = service;
        this.reversals = reversals;
        this.categories = categories;
        this.accounts = accounts;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.settings = settings;
        this.clock = clock;
        this.month = new MonthPicker(YearMonth.from(today()), 24, settings.locale());
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        categoryById = categories.findAll().stream().collect(Collectors.toMap(TransactionCategory::id, Function.identity()));
        accountById = accounts.findAll().stream().collect(Collectors.toMap(FinancialAccount::id, Function.identity()));
        vehicleById = vehicles.findAll().stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
        driverById = drivers.findAll().stream().collect(Collectors.toMap(Driver::id, Function.identity()));
    }

    @Override
    protected List<FinancialTransaction> load() {
        YearMonth m = month.getValue() == null ? YearMonth.from(today()) : month.getValue();
        loadedMonth = m;
        return transactions.findBetween(m.atDay(1), m.atEndOfMonth());
    }

    private String category(FinancialTransaction t) {
        TransactionCategory c = categoryById.get(t.categoryId());
        return c == null ? "" : c.name();
    }

    private String account(Long id) {
        FinancialAccount a = id == null ? null : accountById.get(id);
        return a == null ? "" : a.name();
    }

    private String plate(Long id) {
        Vehicle v = id == null ? null : vehicleById.get(id);
        return v == null ? "" : v.registration();
    }

    private String driverName(Long id) {
        Driver d = id == null ? null : driverById.get(id);
        return d == null ? "" : d.displayName();
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        inflows = strip.add(messages.get("transactions.kpi.inflows"));
        outflows = strip.add(messages.get("transactions.kpi.outflows"));
        net = strip.add(messages.get("transactions.kpi.net"));
        return strip;
    }

    @Override
    protected void loaded(List<FinancialTransaction> items) {
        long in = 0;
        long out = 0;
        for (FinancialTransaction t : items) {
            if (t.nature() == TransactionNature.TRANSFER) {
                continue;                                    // un virement n'est ni une recette ni une dépense
            }
            if (t.signedMinor() > 0) {
                in += t.signedMinor();
            } else {
                out -= t.signedMinor();
            }
        }
        var c = settings.currency();
        String period = month.getConverter().toString(loadedMonth);
        inflows.set(ui.formats().money(Money.of(in, c)), period, Tone.SUCCESS);
        outflows.set(ui.formats().money(Money.of(out, c)), period, Tone.WARNING);
        Money flow = Money.of(in - out, c);
        net.set(ui.formats().money(flow), messages.get("transactions.kpi.net.caption"),
                flow.isNegative() ? Tone.DANGER : Tone.NEUTRAL);
        List<FinancialAccount> all = List.copyOf(accountById.values());
        FinancialAccount selected = accountFilter.getValue();
        accountFilter.getItems().setAll(all);
        accountFilter.getItems().add(0, null);
        accountFilter.setValue(selected == null ? null : accountById.get(selected.id()));
    }

    @Override
    protected Node filters() {
        month.valueProperty().addListener((obs, o, m) -> refresh());
        accountFilter.setPromptText(messages.get("transactions.filter.allAccounts"));
        ListCell<FinancialAccount> accountCell = accountCell();
        accountFilter.setButtonCell(accountCell);
        accountFilter.setCellFactory(l -> accountCell());
        directionFilter.getItems().add(null);
        directionFilter.getItems().addAll(TransactionDirection.values());
        directionFilter.setPromptText(messages.get("transactions.filter.allDirections"));
        directionFilter.setButtonCell(Tones.cell(messages, messages.get("transactions.filter.allDirections")));
        directionFilter.setCellFactory(l -> Tones.cell(messages, messages.get("transactions.filter.allDirections")));
        accountFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        directionFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        return new HBox(8, month, accountFilter, directionFilter);
    }

    private ListCell<FinancialAccount> accountCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(FinancialAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? messages.get("transactions.filter.allAccounts") : item.name());
            }
        };
    }

    private void applyFilter() {
        FinancialAccount a = accountFilter.getValue();
        TransactionDirection d = directionFilter.getValue();
        table().filterProperty().set(t -> (a == null || a.id().equals(t.accountId()))
                && (d == null || t.direction() == d));
    }

    @Override
    protected String title() {
        return messages.get("transactions.tab.ledger");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("transactions.count", total)
                : messages.get("transactions.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("transaction.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.TRANSACTIONS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("transactions.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("transactions.empty.text");
    }

    @Override
    protected void configureTable(DataTable<FinancialTransaction> table) {
        table.dateColumn(messages.get("transaction.date"), FinancialTransaction::date, 100).setMinWidth(95);
        table.badgeColumn(messages.get("transaction.direction"), t -> Tones.direction(messages, t.direction()), 100);
        table.textColumn(messages.get("transaction.category"), this::category, 200).setMinWidth(140);
        table.textColumn(messages.get("transaction.account"), t -> account(t.accountId()), 150).setMinWidth(110);
        table.moneyColumn(messages.get("transaction.amount"), FinancialTransaction::amount, 130);
        table.textColumn(messages.get("transaction.label"), FinancialTransaction::label, 240).setMinWidth(140);
        table.textColumn(messages.get("transaction.vehicle.short"), t -> plate(t.vehicleId()), 100);
        table.textColumn(messages.get("transaction.source"), t -> messages.label(t.source()), 120);
        table.badgeColumn(messages.get("common.state"), t -> Tones.state(messages, t), 130);
        table.searchBy(t -> String.join(" ", category(t), account(t.accountId()), String.valueOf(t.label()),
                String.valueOf(t.reference()), plate(t.vehicleId()), driverName(t.driverId()), messages.label(t.source())));
    }

    @Override
    protected EntityEditor<FinancialTransaction> createEditor(FinancialTransaction existing) {
        return existing == null ? new Editor() : new Detail(existing);
    }

    @Override
    protected List<Button> editorActions(FinancialTransaction existing) {
        if (reversals.refusal(existing) != null) {
            return List.of();
        }
        Button reverse = new Button(messages.get("transaction.reverse"), new FmsIcon(AppIcon.REVERSE, 16));
        reverse.getStyleClass().add("danger");
        reverse.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                ui.dialogs().reason(messages.get("transaction.reverse.title"),
                                messages.get(existing.source() == TransactionSource.MANUAL || existing.source() == TransactionSource.MAINTENANCE
                                                ? "transaction.reverse.text" : "transaction.reverse.textModule",
                                        messages.label(existing.source())),
                                messages.get("common.reason"), messages.get("transaction.reverse"), Tone.DANGER)
                        .thenAccept(reason -> reason.ifPresent(r -> ui.run(() -> reversals.reverse(existing.id(), r),
                                t -> messages.get("transaction.reversed"), t -> refresh())));
            }
        }));
        return List.of(reverse);
    }

    @Override
    protected String savedMessage(FinancialTransaction saved, boolean created) {
        return messages.get("transaction.created", ui.formats().money(saved.amount()), category(saved));
    }

    @Override
    protected Object identityOf(FinancialTransaction t) {
        return t.id();
    }

    private final class Editor implements EntityEditor<FinancialTransaction> {
        private final Form form = ui.forms().create();
        private final ChoiceInput<TransactionDirection> direction;
        private final ChoiceInput<TransactionCategory> category;
        private final DateInput date;
        private final ChoiceInput<FinancialAccount> account;
        private final MoneyInput amount;
        private final ChoiceInput<Vehicle> vehicle;
        private final ChoiceInput<Driver> driver;
        private final TextInput label;
        private final TextInput reference;
        private final List<TransactionCategory> direct;

        Editor() {
            LocalDate today = today();
            direct = categoryById.values().stream().filter(c -> c.active() && c.directEntry())
                    .sorted(java.util.Comparator.comparing(TransactionCategory::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            form.section(messages.get("transaction.section.what"));
            direction = form.choice("direction", messages.get("transaction.direction"), TransactionDirection.class)
                    .required();
            category = form.choice("categoryId", messages.get("transaction.category"), List.of(),
                    TransactionCategory::name).required();
            direction.valueProperty().addListener((obs, o, d) -> category.setOptions(
                    direct.stream().filter(c -> c.direction() == d).toList()));
            date = form.date("date", messages.get("transaction.date")).required().rule(Rules.notAfter(() -> today));
            account = form.choice("accountId", messages.get("transaction.account"),
                            accountById.values().stream().filter(FinancialAccount::active).toList(), FinancialAccount::name)
                    .required();
            amount = form.money("amount", messages.get("transaction.amount")).required()
                    .rule(Rules.that(Money::isPositive, "validation.positive"));
            form.section(messages.get("transaction.section.links"));
            vehicle = form.choice("vehicleId", messages.get("transaction.vehicle"),
                    vehicleById.values().stream().filter(v -> v.status() != Vehicle.VehicleStatus.SOLD
                            && v.status() != Vehicle.VehicleStatus.INACTIVE).toList(),
                    v -> v.registration() + " — " + v.make() + " " + v.model()).allowEmpty();
            vehicle.help(messages.get("transaction.vehicle.help"));
            driver = form.choice("driverId", messages.get("transaction.driver"),
                            driverById.values().stream().filter(d -> d.status().canBeAssigned()).toList(), Driver::displayName)
                    .allowEmpty();
            label = form.text("label", messages.get("transaction.label")).maxLength(TransactionRules.LABEL_MAX).span(2);
            reference = form.text("reference", messages.get("transaction.reference"))
                    .maxLength(TransactionRules.REFERENCE_MAX).help(messages.get("transaction.reference.help"));
            category.valueProperty().addListener((obs, o, c) -> vehicle.help(messages.get(
                    c != null && c.requiresVehicle() ? "transaction.vehicle.required.help" : "transaction.vehicle.help")));
            direction.setValue(TransactionDirection.OUTFLOW);
            date.setValue(today);
            accountById.values().stream().filter(a -> a.active()).findFirst().ifPresent(account::setValue);
        }

        @Override
        public String title() {
            return messages.get("transaction.create");
        }

        @Override
        public String subtitle() {
            return messages.get("transaction.create.subtitle");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public FinancialTransaction toEntity() {
            return new FinancialTransaction(null, 0, date.getValue(), null, direction.getValue(),
                    category.getValue() == null ? null : category.getValue().id(), null,
                    account.getValue() == null ? null : account.getValue().id(), amount.getValue(),
                    vehicle.getValue() == null ? null : vehicle.getValue().id(),
                    driver.getValue() == null ? null : driver.getValue().id(), label.getValue(), reference.getValue(),
                    TransactionSource.MANUAL, null, null, null, null);
        }
    }

    private final class Detail implements EntityEditor<FinancialTransaction> {
        private final FinancialTransaction t;

        Detail(FinancialTransaction t) {
            this.t = t;
        }

        @Override
        public String title() {
            return messages.get("transaction.detail", ui.formats().date(t.date()));
        }

        @Override
        public String subtitle() {
            return category(t);
        }

        @Override
        public Node header() {
            DetailList d = new DetailList();
            d.add(messages.get("transaction.direction"), messages.label(t.direction()),
                    t.direction() == TransactionDirection.INFLOW ? Tone.SUCCESS : Tone.WARNING);
            d.add(messages.get("transaction.amount"), ui.formats().money(t.amount()));
            d.add(messages.get("transaction.account"), account(t.accountId()));
            d.add(messages.get("transaction.category"), category(t));
            d.add(messages.get("transaction.nature"), messages.label(t.nature()));
            d.add(messages.get("transaction.vehicle"), plate(t.vehicleId()));
            d.add(messages.get("transaction.driver"), driverName(t.driverId()));
            d.add(messages.get("transaction.label"), t.label());
            d.add(messages.get("transaction.reference"), t.reference());
            d.add(messages.get("transaction.source"), messages.label(t.source()));
            d.add(messages.get("common.recordedBy"), t.userName() + " — " + ui.formats().dateTime(t.recordedAt()));
            d.add(messages.get("common.state"), Tones.state(messages, t).text(), Tones.state(messages, t).tone());
            String refusal = reversals.refusal(t);
            if (refusal != null && !t.isReversal() && !t.isReversed()) {
                d.add(messages.get("transaction.reverse"), messages.get(refusal));
            }
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
        public FinancialTransaction toEntity() {
            return t;
        }
    }
}
