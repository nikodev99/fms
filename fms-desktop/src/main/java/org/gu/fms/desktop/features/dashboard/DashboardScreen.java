package org.gu.fms.desktop.features.dashboard;

import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Tones;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.filter.PeriodNavigator;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.Screen;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.table.Tables;
import org.gu.fms.desktop.platform.ui.task.UiTasks;
import org.gu.fms.domain.collection.CollectionCalendar;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionCategory;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.report.OperatingResult;
import org.gu.fms.domain.vehicle.Vehicle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tableau de bord du propriétaire (instructions §13) : chiffres de la période choisie (mois, ou depuis le début),
 * deux graphiques sur douze mois, et les transactions de la période. Recalculé à chaque affichage et à chaque
 * changement de période : aucun chiffre figé.
 */
public final class DashboardScreen implements Screen {

    private final DashboardService service;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final Navigator navigator;
    private final UiTasks tasks;
    private final Tables tables;
    private final Messages messages;
    private final Formats formats;
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final Label dateLabel = new Label();
    private final KpiStrip money = new KpiStrip();
    private final KpiStrip fleet = new KpiStrip();
    private final KpiStrip.Kpi kCollected;
    private final KpiStrip.Kpi kCharges;
    private final KpiStrip.Kpi kResult;
    private final KpiStrip.Kpi kCash;
    private final KpiStrip.Kpi kVehicles;
    private final KpiStrip.Kpi kDrivers;
    private final KpiStrip.Kpi kRate;
    private final KpiStrip.Kpi kMissing;
    private final Label tableTitle = new Label();
    private BarChart<String, Number> collectionsChart;
    private BarChart<String, Number> resultChart;
    private DataTable<FinancialTransaction> table;
    private PeriodNavigator period;
    private Node view;
    private volatile Lookup lookup = new Lookup(Map.of(), Map.of(), Map.of(), Map.of());

    private record Lookup(Map<Long, TransactionCategory> categories, Map<Long, FinancialAccount> accounts,
                          Map<Long, Vehicle> vehicles, Map<Long, Driver> drivers) {
    }

    private record Loaded(DashboardService.Data data, Lookup lookup) {
    }

    @Inject
    public DashboardScreen(DashboardService service, CategoryRepository categories, AccountRepository accounts,
                           VehicleRepository vehicles, DriverRepository drivers, Navigator navigator, UiTasks tasks,
                           Tables tables, Messages messages, Formats formats) {
        this.service = service;
        this.categories = categories;
        this.accounts = accounts;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.navigator = navigator;
        this.tasks = tasks;
        this.tables = tables;
        this.messages = messages;
        this.formats = formats;
        kCollected = money.add(messages.get("dashboard.kpi.collected")).onClick(() -> navigator.navigate(AppRoutes.COLLECTIONS));
        kCharges = money.add(messages.get("dashboard.kpi.charges")).onClick(() -> navigator.navigate(AppRoutes.TRANSACTIONS));
        kResult = money.add(messages.get("dashboard.kpi.result"));
        kCash = money.add(messages.get("dashboard.kpi.cash")).onClick(() -> navigator.navigate(AppRoutes.ACCOUNTS));
        kVehicles = fleet.add(messages.get("dashboard.kpi.vehicles")).onClick(() -> navigator.navigate(AppRoutes.VEHICLES));
        kDrivers = fleet.add(messages.get("dashboard.kpi.drivers")).onClick(() -> navigator.navigate(AppRoutes.DRIVERS));
        kRate = fleet.add(messages.get("dashboard.kpi.rate")).onClick(() -> navigator.navigate(AppRoutes.COLLECTIONS));
        kMissing = fleet.add(messages.get("dashboard.kpi.missing")).onClick(() -> navigator.navigate(AppRoutes.COLLECTIONS));
    }

    @Override
    public Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    @Override
    public void onShow() {
        LocalDate today = service.today();
        String text = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", messages.locale()));
        dateLabel.setText(Character.toUpperCase(text.charAt(0)) + text.substring(1));
        reload();
    }

    private Node build() {
        Label title = new Label(messages.get("nav.dashboard"));
        title.getStyleClass().add("page-title");
        dateLabel.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(4, title, dateLabel);
        period = new PeriodNavigator(messages, YearMonth.from(service.today()), true);
        period.monthProperty().addListener((obs, o, m) -> reload());
        period.allTimeProperty().addListener((obs, o, all) -> reload());
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("inline-spinner");
        spinner.visibleProperty().bind(loading);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(12, titles, grow, spinner, period);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");

        collectionsChart = chart();
        collectionsChart.getStyleClass().add("collections-chart");
        resultChart = chart();
        resultChart.getStyleClass().add("result-chart");
        GridPane charts = new GridPane();
        charts.setHgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            c.setHgrow(Priority.ALWAYS);
            charts.getColumnConstraints().add(c);
        }
        charts.add(panel(messages.get("dashboard.chart.collections"), AppIcon.CHART, collectionsChart), 0, 0);
        charts.add(panel(messages.get("dashboard.chart.result"), AppIcon.CHART, resultChart), 1, 0);

        table = tables.create();
        table.dateColumn(messages.get("transaction.date"), FinancialTransaction::date, 100).setMinWidth(95);
        table.badgeColumn(messages.get("transaction.direction"), t -> Tones.direction(messages, t.direction()), 95);
        table.textColumn(messages.get("transaction.category"), t -> name(lookup.categories().get(t.categoryId()),
                TransactionCategory::name), 190).setMinWidth(140);
        table.textColumn(messages.get("transaction.account"), t -> name(lookup.accounts().get(t.accountId()),
                FinancialAccount::name), 130);
        table.moneyColumn(messages.get("transaction.amount"), FinancialTransaction::amount, 120).setMinWidth(100);
        table.textColumn(messages.get("transaction.vehicle"), t -> name(lookup.vehicles().get(t.vehicleId()),
                Vehicle::registration), 100);
        table.textColumn(messages.get("transaction.label"), t -> t.label() == null ? "" : t.label(), 240)
                .setMinWidth(160);
        table.badgeColumn(messages.get("common.state"), t -> Tones.state(messages, t), 110);
        table.searchBy(t -> String.join(" ", name(lookup.categories().get(t.categoryId()), TransactionCategory::name),
                name(lookup.accounts().get(t.accountId()), FinancialAccount::name),
                name(lookup.vehicles().get(t.vehicleId()), Vehicle::registration),
                name(lookup.drivers().get(t.driverId()), Driver::displayName), t.label() == null ? "" : t.label()));
        table.onOpen(t -> navigator.navigate(AppRoutes.TRANSACTIONS));
        table.setPrefHeight(380);
        table.setMinHeight(260);
        TextField search = new TextField();
        search.setPromptText(messages.get("common.search"));
        search.textProperty().bindBidirectional(table.searchTextProperty());
        tableTitle.getStyleClass().add("panel-title");
        Region g2 = new Region();
        HBox.setHgrow(g2, Priority.ALWAYS);
        HBox tableHeader = new HBox(8, new FmsIcon(AppIcon.TRANSACTIONS, 16), tableTitle, g2, search);
        tableHeader.getStyleClass().add("panel-header");
        VBox tablePanel = new VBox(tableHeader, table);
        tablePanel.getStyleClass().add("panel");
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(16, header, money, fleet, charts, tablePanel);
        page.getStyleClass().addAll("page", "content", "dashboard");
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    private static <T> String name(T item, Function<T, String> f) {
        return item == null ? "" : f.apply(item);
    }

    private VBox panel(String title, AppIcon icon, Node content) {
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        HBox h = new HBox(8, new FmsIcon(icon, 16), t);
        h.getStyleClass().add("panel-header");
        VBox body = new VBox(content);
        body.getStyleClass().add("panel-body");
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox p = new VBox(h, body);
        p.getStyleClass().add("panel");
        return p;
    }

    private BarChart<String, Number> chart() {
        CategoryAxis x = new CategoryAxis();
        x.setTickLabelRotation(0);
        NumberAxis y = new NumberAxis();
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number n) {
                return compact(n.doubleValue());
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });
        BarChart<String, Number> c = new BarChart<>(x, y);
        c.setAnimated(false);
        c.setLegendVisible(true);
        c.setCategoryGap(14);
        c.setBarGap(2);
        c.setPrefHeight(280);
        c.setMinHeight(220);
        c.getStyleClass().add("fms-chart");
        return c;
    }

    /** 1 250 000 → « 1,25 M » ; 25 000 → « 25 k ». */
    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) {
            return trim(v / 1_000_000) + " M";
        }
        if (a >= 1_000) {
            return trim(v / 1_000) + " k";
        }
        return trim(v);
    }

    private static String trim(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                .replace('.', ',');
    }

    private void reload() {
        if (period == null) {
            return;
        }
        YearMonth month = Boolean.TRUE.equals(period.allTimeProperty().get()) ? null : period.monthProperty().get();
        tasks.job(() -> new Loaded(service.load(month), new Lookup(
                        categories.findAll().stream().collect(Collectors.toMap(TransactionCategory::id, Function.identity())),
                        accounts.findAll().stream().collect(Collectors.toMap(FinancialAccount::id, Function.identity())),
                        vehicles.findAll().stream().collect(Collectors.toMap(Vehicle::id, Function.identity())),
                        drivers.findAll().stream().collect(Collectors.toMap(Driver::id, Function.identity())))))
                .busy(loading)
                .onSuccess(this::show)
                .start();
    }

    private String money(long minor) {
        return formats.money(Money.of(minor, formats.currency()));
    }

    private String rate(Long bp) {
        return bp == null ? "—" : BigDecimal.valueOf(bp, 2).setScale(1, RoundingMode.HALF_UP).toPlainString()
                .replace('.', ',') + " %";
    }

    private void show(Loaded loaded) {
        DashboardService.Data d = loaded.data();
        lookup = loaded.lookup();
        CollectionCalendar.Summary c = d.collections();
        CollectionPolicy p = d.policy();
        OperatingResult r = d.result();

        kCollected.set(money(c.received()), messages.get("dashboard.kpi.collected.caption", money(c.expected())),
                Tone.INFO);
        kCharges.set(money(r.charges()), messages.get("dashboard.kpi.charges.caption", money(r.payrollCost())),
                Tone.WARNING);
        kResult.set(money(r.result()), messages.get("dashboard.kpi.result.caption", money(r.totalRevenue())),
                r.result() < 0 ? Tone.DANGER : Tone.SUCCESS);
        kCash.set(money(d.consolidatedCash()), messages.get("dashboard.kpi.cash.caption", d.countedAccounts()),
                d.consolidatedCash() < 0 ? Tone.DANGER : Tone.NEUTRAL);
        kVehicles.set(Integer.toString(d.vehiclesInService()),
                messages.get("dashboard.kpi.vehicles.caption", d.vehiclesBroken(), d.vehiclesTotal()),
                d.vehiclesBroken() > 0 ? Tone.WARNING : Tone.NEUTRAL);
        kDrivers.set(Integer.toString(d.activeDrivers()), null, Tone.NEUTRAL);
        Tone rateTone = c.rateBp() == null ? Tone.NEUTRAL : c.rateBp() >= p.objective().basisPoints() ? Tone.SUCCESS
                : c.rateBp() >= p.redThreshold().basisPoints() ? Tone.WARNING : Tone.DANGER;
        kRate.set(rate(c.rateBp()), messages.get("dashboard.kpi.rate.caption", formats.percent(p.objective())), rateTone);
        kMissing.set(money(c.missing()), messages.get("dashboard.kpi.missing.caption", c.driversBelowObjective()),
                c.missing() > 0 ? Tone.DANGER : Tone.SUCCESS);

        fillCharts(d.months());
        table.setItems(d.transactions());
        tableTitle.setText(messages.get("dashboard.transactions", d.transactions().size(),
                formats.date(d.from()), formats.date(d.to())));
    }

    private void fillCharts(List<DashboardService.MonthPoint> months) {
        XYChart.Series<String, Number> expected = series(messages.get("dashboard.series.expected"));
        XYChart.Series<String, Number> received = series(messages.get("dashboard.series.received"));
        XYChart.Series<String, Number> revenue = series(messages.get("dashboard.series.revenue"));
        XYChart.Series<String, Number> charges = series(messages.get("dashboard.series.charges"));
        XYChart.Series<String, Number> result = series(messages.get("dashboard.series.result"));
        for (DashboardService.MonthPoint m : months) {
            String label = monthLabel(m.month());
            add(expected, label, m.expected());
            add(received, label, m.received());
            add(revenue, label, m.result().totalRevenue());
            add(charges, label, m.result().charges());
            add(result, label, m.result().result());
        }
        collectionsChart.getData().setAll(List.of(expected, received));
        resultChart.getData().setAll(List.of(revenue, charges, result));
        for (BarChart<String, Number> chart : List.of(collectionsChart, resultChart)) {
            for (XYChart.Series<String, Number> s : chart.getData()) {
                for (XYChart.Data<String, Number> point : s.getData()) {
                    installTooltip(s.getName(), point);
                }
            }
        }
    }

    private String monthLabel(YearMonth m) {
        String name = m.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, messages.locale());
        return m.getMonthValue() == 1 ? name + " " + (m.getYear() % 100) : name;
    }

    private static XYChart.Series<String, Number> series(String name) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName(name);
        return s;
    }

    private static void add(XYChart.Series<String, Number> s, String label, long minor) {
        s.getData().add(new XYChart.Data<>(label, minor));
    }

    private void installTooltip(String series, XYChart.Data<String, Number> point) {
        String text = series + " — " + point.getXValue() + " : " + money(point.getYValue().longValue());
        Runnable install = () -> {
            Tooltip t = new Tooltip(text);
            t.setShowDelay(Duration.millis(150));
            Tooltip.install(point.getNode(), t);
        };
        if (point.getNode() != null) {
            install.run();
        } else {
            point.nodeProperty().addListener((obs, o, n) -> {
                if (n != null) {
                    install.run();
                }
            });
        }
    }
}
