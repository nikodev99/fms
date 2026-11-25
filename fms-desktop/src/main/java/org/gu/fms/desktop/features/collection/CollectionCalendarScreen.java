package org.gu.fms.desktop.features.collection;

import org.gu.fms.desktop.features.report.QuickReports;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Screen;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.filter.PeriodNavigator;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.domain.collection.CalendarState;
import org.gu.fms.domain.collection.CollectionCalendar;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.money.Money;

/**
 * Versements › Calendrier : une ligne par chauffeur, une case par jour, colorée selon le versement du jour
 * (vert : complet ; orange : partiel ; rouge : sous le seuil ou rien ; gris-bleu : exempté — panne, repos accordé ;
 * gris : repos de la flotte). Montants en milliers. Bandeau : attendu, encaissé, taux de recouvrement, manquant.
 */
public final class CollectionCalendarScreen implements Screen, Screen.Embeddable {

    private final CrudContext ui;
    private final Messages messages;
    private final CollectionCalendarService service;
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final KpiStrip kpis = new KpiStrip();
    private final KpiStrip.Kpi kExpected;
    private final KpiStrip.Kpi kReceived;
    private final KpiStrip.Kpi kRate;
    private final KpiStrip.Kpi kMissing;
    private final GridPane grid = new GridPane();
    private final Label empty = new Label();
    private PeriodNavigator navigator;
    private final QuickReports quickReports;
    private final SimpleBooleanProperty downloading = new SimpleBooleanProperty();
    private Node view;
    private boolean embedded;

    @Inject
    public CollectionCalendarScreen(CrudContext ui, CollectionCalendarService service, QuickReports quickReports) {
        this.quickReports = quickReports;
        this.ui = ui;
        this.messages = ui.messages();
        this.service = service;
        kExpected = kpis.add(messages.get("calendar.kpi.expected"));
        kReceived = kpis.add(messages.get("calendar.kpi.received"));
        kRate = kpis.add(messages.get("calendar.kpi.rate"));
        kMissing = kpis.add(messages.get("calendar.kpi.missing"));
    }

    @Override
    public void embed() {
        embedded = true;
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
        reload();
    }

    private Node build() {
        navigator = new PeriodNavigator(messages, YearMonth.from(service.today()), false);
        navigator.monthProperty().addListener((obs, o, m) -> reload());
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("inline-spinner");
        spinner.visibleProperty().bind(loading);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Label unit = new Label(messages.get("calendar.unit", ui.formats().currency().code()));
        unit.getStyleClass().add("page-subtitle");
        Button report = new Button(messages.get("calendar.report"),
                new FmsIcon(AppIcon.DOWNLOAD, 16));
        report.getStyleClass().add("secondary");
        report.disableProperty().bind(downloading);
        report.setOnAction(e -> quickReports.monthlyCollections(navigator.monthProperty().get(), downloading));
        HBox header = new HBox(12, navigator, grow, spinner, unit, report);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");

        grid.getStyleClass().add("calendar-grid");
        grid.setHgap(1);
        grid.setVgap(2);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("calendar-scroll");
        VBox panel = new VBox(10, scroll, legend());
        panel.getStyleClass().addAll("panel", "calendar-panel");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(panel, Priority.ALWAYS);
        empty.getStyleClass().add("empty-text");
        empty.setText(messages.get("calendar.empty"));

        VBox page = new VBox(16, header, kpis, panel);
        page.getStyleClass().add(embedded ? "section-page" : "page");
        if (!embedded) {
            page.getStyleClass().add("content");
        }
        return page;
    }

    private Node legend() {
        FlowPane legend = new FlowPane(16, 6);
        legend.getStyleClass().add("calendar-legend");
        for (CalendarState s : new CalendarState[] {CalendarState.FULL, CalendarState.PARTIAL, CalendarState.MISSED,
                CalendarState.EXEMPT, CalendarState.REST, CalendarState.BONUS, CalendarState.PENDING}) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("calendar-swatch", styleOf(s));
            Label l = new Label(messages.label(s), swatch);
            l.getStyleClass().add("calendar-legend-item");
            legend.getChildren().add(l);
        }
        return legend;
    }

    private void reload() {
        if (navigator == null) {
            return;
        }
        YearMonth m = navigator.monthProperty().get();
        ui.tasks().job(() -> new Loaded(service.period(m.atDay(1), m.atEndOfMonth()), service.policy(), service.today()))
                .busy(loading)
                .onSuccess(this::show)
                .start();
    }

    private record Loaded(CollectionCalendar.Month month, CollectionPolicy policy, LocalDate today) {
    }

    private void show(Loaded data) {
        CollectionCalendar.Summary s = data.month().summary();
        CollectionPolicy p = data.policy();
        kExpected.set(money(s.expected()), messages.get("calendar.kpi.expected.caption", s.workedDays()), Tone.NEUTRAL);
        kReceived.set(money(s.received()), messages.get("calendar.kpi.received.caption", s.entries()), Tone.INFO);
        Tone rateTone = s.rateBp() == null ? Tone.NEUTRAL
                : s.rateBp() >= p.objective().basisPoints() ? Tone.SUCCESS
                : s.rateBp() >= p.redThreshold().basisPoints() ? Tone.WARNING : Tone.DANGER;
        kRate.set(rate(s.rateBp()), messages.get("calendar.kpi.rate.caption", ui.formats().percent(p.objective())),
                rateTone);
        kMissing.set(money(s.missing()), messages.get("calendar.kpi.missing.caption", s.exemptDays()),
                s.missing() > 0 ? Tone.DANGER : Tone.SUCCESS);
        fillGrid(data);
    }

    private String money(long minor) {
        return ui.formats().money(Money.of(minor, ui.formats().currency()));
    }

    String rate(Long bp) {
        if (bp == null) {
            return "—";
        }
        return BigDecimal.valueOf(bp, 2).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " %";
    }

    /** Montant en milliers, une décimale au plus : 25 000 → « 25 », 12 500 → « 12,5 ». */
    static String thousands(long minor) {
        BigDecimal k = BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return k.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private void fillGrid(Loaded data) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        YearMonth m = navigator.monthProperty().get();
        int days = m.lengthOfMonth();
        Locale locale = messages.locale();
        ColumnConstraints name = new ColumnConstraints();
        name.setMinWidth(110);
        name.setPrefWidth(170);
        grid.getColumnConstraints().add(name);
        for (int d = 1; d <= days; d++) {
            ColumnConstraints c = new ColumnConstraints(22, 32, 60);
            c.setHgrow(Priority.SOMETIMES);
            c.setHalignment(HPos.CENTER);
            grid.getColumnConstraints().add(c);
        }
        for (int i = 0; i < 3; i++) {
            ColumnConstraints c = new ColumnConstraints(50, 70, 110);
            c.setHalignment(HPos.RIGHT);
            grid.getColumnConstraints().add(c);
        }

        grid.add(head(messages.get("calendar.driver"), "calendar-head-name"), 0, 0);
        for (int d = 1; d <= days; d++) {
            LocalDate date = m.atDay(d);
            String dow = date.getDayOfWeek().getDisplayName(TextStyle.NARROW_STANDALONE, locale);
            Label h = head(d + "\n" + dow.toUpperCase(locale), "calendar-head-day");
            if (data.policy().isRestDay(date)) {
                h.getStyleClass().add("rest");
            }
            if (date.equals(data.today())) {
                h.getStyleClass().add("today");
            }
            grid.add(h, d, 0);
        }
        grid.add(head(messages.get("calendar.total"), "calendar-head-total"), days + 1, 0);
        grid.add(head(messages.get("calendar.expected"), "calendar-head-total"), days + 2, 0);
        grid.add(head(messages.get("calendar.rate"), "calendar-head-total"), days + 3, 0);

        int row = 1;
        for (CollectionCalendar.DriverRow r : data.month().rows()) {
            Label n = new Label(r.label());
            n.getStyleClass().add("calendar-name");
            n.setMaxWidth(Double.MAX_VALUE);
            grid.add(n, 0, row);
            for (CollectionCalendar.Cell c : r.cells()) {
                grid.add(cell(c), c.date().getDayOfMonth(), row);
            }
            grid.add(total(thousands(r.received()), null), days + 1, row);
            grid.add(total(thousands(r.expected()), null), days + 2, row);
            grid.add(total(rate(r.rateBp()), r.belowObjective() ? "below" : "ok"), days + 3, row);
            row++;
        }
        if (data.month().rows().isEmpty()) {
            grid.add(empty, 0, 1, days + 4, 1);
        }
    }

    private Label head(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().addAll("calendar-head", style);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    private Label total(String text, String tone) {
        Label l = new Label(text);
        l.getStyleClass().add("calendar-total");
        if (tone != null) {
            l.getStyleClass().add(tone);
        }
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER_RIGHT);
        return l;
    }

    private Label cell(CollectionCalendar.Cell c) {
        String text = switch (c.state()) {
            case NONE, REST -> "";
            case EXEMPT -> "0";
            case PENDING -> c.received() == 0 ? "" : thousands(c.received());
            default -> thousands(c.received());
        };
        Label l = new Label(text);
        l.getStyleClass().addAll("calendar-cell", styleOf(c.state()));
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMaxHeight(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        if (c.state() != CalendarState.NONE) {
            Tooltip t = new Tooltip(messages.get("calendar.cell.tooltip", ui.formats().date(c.date()),
                    messages.label(c.state()), money(c.expected()), money(c.received())));
            t.setShowDelay(Duration.millis(250));
            l.setTooltip(t);
        }
        return l;
    }

    static String styleOf(CalendarState s) {
        return "cal-" + s.name().toLowerCase(Locale.ROOT);
    }
}
