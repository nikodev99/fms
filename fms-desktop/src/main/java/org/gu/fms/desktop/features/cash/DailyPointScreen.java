package org.gu.fms.desktop.features.cash;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.gu.fms.desktop.features.collection.DailyPointService;
import org.gu.fms.desktop.features.collection.PointRow;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.filter.DayPicker;
import org.gu.fms.desktop.platform.ui.form.ChoiceInput;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.TextAreaInput;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.collection.DailyPointRules;
import org.gu.fms.domain.collection.DaySituation;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caisse du jour › Point journalier (instructions §4.3). Pour chaque chauffeur : attendu (affectations), reçu
 * (versements − avoirs), écart. Avant validation, un chauffeur peut être exempté (véhicule immobilisé, maladie…)
 * avec un motif. La validation fige les montants et porte les écarts au compte des chauffeurs.
 */
public final class DailyPointScreen extends CrudScreen<PointRow> {

    private final DailyPointService points;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final AppSettings settings;
    private final Clock clock;
    private final Decisions decisions;
    private final DayPicker day;
    private volatile Map<Long, Driver> driverById = Map.of();
    private volatile Map<Long, Vehicle> vehicleById = Map.of();
    private KpiStrip.Kpi expected;
    private KpiStrip.Kpi received;
    private KpiStrip.Kpi difference;
    private KpiStrip.Kpi pending;
    private Button validate;

    @Inject
    public DailyPointScreen(CrudContext ui, DailyPointService points, DriverRepository drivers,
                            VehicleRepository vehicles, AppSettings settings, Clock clock) {
        this(ui, new Decisions(), points, drivers, vehicles, settings, clock);
    }

    private DailyPointScreen(CrudContext ui, Decisions decisions, DailyPointService points, DriverRepository drivers,
                             VehicleRepository vehicles, AppSettings settings, Clock clock) {
        super(ui, decisions);
        this.decisions = decisions;
        this.points = points;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.settings = settings;
        this.clock = clock;
        this.day = new DayPicker(ui.formats(), this::today, messages.get("common.previousDay"),
                messages.get("common.nextDay"), messages.get("common.today"));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        driverById = drivers.findAll().stream().collect(Collectors.toMap(Driver::id, Function.identity()));
        vehicleById = vehicles.findAll().stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
    }

    @Override
    protected List<PointRow> load() {
        LocalDate d = day.day();
        if (!d.equals(decisions.day)) {
            decisions.reset(d);
        }
        List<PointRow> rows = new ArrayList<>();
        for (PointRow r : points.preview(d)) {
            PointRow decided = decisions.byDriver.get(r.driverId());
            if (decided != null && !r.validated()) {
                rows.add(r.decided(decided.situation(), decided.reason()));
            } else if (!r.validated() && r.vehicleUnavailable()) {
                rows.add(r.decided(DaySituation.EXEMPT, messages.get("dailyPoint.reason.vehicleUnavailable")));
            } else {
                rows.add(r);
            }
        }
        return rows;
    }

    private String driverName(long id) {
        Driver d = driverById.get(id);
        return d == null ? "" : d.displayName();
    }

    private String plate(Long id) {
        Vehicle v = id == null ? null : vehicleById.get(id);
        return v == null ? "" : v.registration();
    }

    @Override
    protected boolean canCreate() {
        return false;
    }

    @Override
    protected Node filters() {
        day.dayProperty().addListener((obs, o, d) -> refresh());
        return day;
    }

    @Override
    protected List<Button> headerActions() {
        validate = new Button(messages.get("dailyPoint.validate"), new FmsIcon(AppIcon.VALIDATE, 16));
        validate.getStyleClass().add("primary");
        validate.setOnAction(e -> confirmValidation());
        return List.of(validate);
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        expected = strip.add(messages.get("dailyPoint.kpi.expected"));
        received = strip.add(messages.get("dailyPoint.kpi.received"));
        difference = strip.add(messages.get("dailyPoint.kpi.difference"));
        pending = strip.add(messages.get("dailyPoint.kpi.pending"));
        return strip;
    }

    @Override
    protected void loaded(List<PointRow> items) {
        var c = settings.currency();
        long e = items.stream().mapToLong(r -> r.expected().minor()).sum();
        long r = items.stream().mapToLong(x -> x.shownReceived().minor()).sum();
        long todo = items.stream().filter(x -> !x.validated()).count();
        expected.set(ui.formats().money(Money.of(e, c)), null, Tone.NEUTRAL);
        received.set(ui.formats().money(Money.of(r, c)), null, Tone.SUCCESS);
        Money gap = Money.of(r - e, c);
        difference.set(ui.formats().money(gap), messages.get("dailyPoint.kpi.difference.caption"),
                gap.isNegative() ? Tone.DANGER : gap.isPositive() ? Tone.SUCCESS : Tone.NEUTRAL);
        pending.set(String.valueOf(todo), todo == 0 && !items.isEmpty() ? messages.get("dailyPoint.kpi.allDone")
                : null, todo == 0 ? Tone.SUCCESS : Tone.WARNING);
        validate.setDisable(todo == 0);
    }

    private void confirmValidation() {
        LocalDate d = day.day();
        List<PointRow> todo = table().items().stream().filter(r -> !r.validated()).toList();
        for (PointRow r : todo) {
            if (!DailyPointRules.validate(r.situation(), r.reason()).isEmpty()) {
                ui.dialogs().error(messages.get("dailyPoint.invalid.title"),
                        messages.get("dailyPoint.exemptReasonFor", driverName(r.driverId())));
                return;
            }
        }
        long shortages = todo.stream().filter(r -> r.difference().isNegative()).count();
        ui.dialogs().confirm(messages.get("dailyPoint.confirm.title", ui.formats().date(d)),
                messages.get("dailyPoint.confirm.text", todo.size(), shortages),
                messages.get("dailyPoint.validate"), Tone.WARNING).thenAccept(ok -> {
            if (ok) {
                Map<Long, PointRow> chosen = todo.stream()
                        .collect(Collectors.toMap(PointRow::driverId, Function.identity()));
                ui.run(() -> points.validate(d, chosen),
                        n -> messages.get("dailyPoint.validated", n, ui.formats().date(d)), n -> {
                            decisions.reset(d);
                            refresh();
                        });
            }
        });
    }

    @Override
    protected String title() {
        return messages.get("cash.tab.point");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("dailyPoint.count", total)
                : messages.get("dailyPoint.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("dailyPoint.validate");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.POINT;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("dailyPoint.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("dailyPoint.empty.text");
    }

    @Override
    protected String hint() {
        return messages.get("dailyPoint.hint");
    }

    @Override
    protected void configureTable(DataTable<PointRow> table) {
        table.textColumn(messages.get("dailyPoint.driver"), r -> driverName(r.driverId()), 190).setMinWidth(130);
        table.textColumn(messages.get("dailyPoint.vehicle"), r -> plate(r.vehicleId()), 100);
        table.moneyColumn(messages.get("dailyPoint.expected"), PointRow::expected, 120);
        table.moneyColumn(messages.get("dailyPoint.received"), PointRow::shownReceived, 120);
        table.moneyColumn(messages.get("dailyPoint.difference"), PointRow::difference, 120);
        table.badgeColumn(messages.get("dailyPoint.situation"), r -> r.shownSituation() == DaySituation.EXEMPT
                ? new Badge(messages.label(r.shownSituation()), Tone.INFO)
                : new Badge(messages.label(r.shownSituation()), Tone.NEUTRAL), 110);
        table.textColumn(messages.get("common.reason"), PointRow::shownReason, 180);
        table.badgeColumn(messages.get("common.state"), r -> r.validated()
                ? new Badge(messages.get("dailyPoint.state.done"), Tone.SUCCESS)
                : new Badge(messages.get("dailyPoint.state.pending"), Tone.WARNING), 110);
        table.searchBy(r -> driverName(r.driverId()) + " " + plate(r.vehicleId()));
    }

    @Override
    protected EntityEditor<PointRow> createEditor(PointRow existing) {
        return existing.validated() ? new Detail(existing) : new Editor(existing);
    }

    @Override
    protected String savedMessage(PointRow saved, boolean created) {
        return messages.get("dailyPoint.decided", driverName(saved.driverId()));
    }

    @Override
    protected Object identityOf(PointRow r) {
        return r.driverId();
    }

    /** Décisions prises avant validation (situation, motif), gardées en mémoire pour la journée affichée. */
    private static final class Decisions implements CrudService<PointRow> {
        private final Map<Long, PointRow> byDriver = new ConcurrentHashMap<>();
        private volatile LocalDate day;

        void reset(LocalDate newDay) {
            byDriver.clear();
            day = newDay;
        }

        @Override
        public List<PointRow> findAll() {
            return List.of();
        }

        @Override
        public PointRow save(PointRow row) {
            ValidationException.throwIfAny(DailyPointRules.validate(row.situation(), row.reason()));
            if (row.validated()) {
                throw ValidationException.of(FieldError.of(null, "dailyPoint.alreadyValidated"));
            }
            byDriver.put(row.driverId(), row);
            return row;
        }
    }

    private final class Editor implements EntityEditor<PointRow> {
        private final PointRow row;
        private final Form form = ui.forms().create();
        private final ChoiceInput<DaySituation> situation;
        private final TextAreaInput reason;

        Editor(PointRow row) {
            this.row = row;
            situation = form.choice("situation", messages.get("dailyPoint.situation"), DaySituation.class).required()
                    .span(2).help(messages.get("dailyPoint.situation.help"));
            reason = form.textArea("reason", messages.get("common.reason")).maxLength(DailyPointRules.REASON_MAX)
                    .rows(2).span(2);
            reason.shownWhen(Bindings.createBooleanBinding(() -> situation.getValue() == DaySituation.EXEMPT,
                    situation.valueProperty()));
            situation.setValue(row.situation());
            reason.setValue(row.reason());
        }

        @Override
        public String title() {
            return driverName(row.driverId());
        }

        @Override
        public String subtitle() {
            return messages.get("dailyPoint.editor.subtitle", ui.formats().date(row.date()));
        }

        @Override
        public Node header() {
            DetailList d = new DetailList();
            d.add(messages.get("dailyPoint.vehicle"), plate(row.vehicleId()));
            d.add(messages.get("dailyPoint.planned"), ui.formats().money(row.plannedExpected()));
            d.add(messages.get("dailyPoint.received"), ui.formats().money(row.received()));
            if (row.vehicleUnavailable()) {
                d.add(messages.get("dailyPoint.vehicle"), messages.get("dailyPoint.reason.vehicleUnavailable"), Tone.WARNING);
            }
            return d;
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public String saveLabel() {
            return messages.get("dailyPoint.apply");
        }

        @Override
        public PointRow toEntity() {
            DaySituation s = situation.getValue();
            return row.decided(s, s == DaySituation.EXEMPT ? reason.getValue() : null);
        }
    }

    private final class Detail implements EntityEditor<PointRow> {
        private final PointRow row;

        Detail(PointRow row) {
            this.row = row;
        }

        @Override
        public String title() {
            return driverName(row.driverId());
        }

        @Override
        public String subtitle() {
            return messages.get("dailyPoint.detail.subtitle", ui.formats().date(row.date()));
        }

        @Override
        public Node header() {
            DetailList d = new DetailList();
            d.add(messages.get("dailyPoint.vehicle"), plate(row.vehicleId()));
            d.add(messages.get("dailyPoint.expected"), ui.formats().money(row.expected()));
            d.add(messages.get("dailyPoint.received"), ui.formats().money(row.shownReceived()));
            Money diff = row.difference();
            d.add(messages.get("dailyPoint.difference"), ui.formats().money(diff),
                    diff.isNegative() ? Tone.DANGER : diff.isPositive() ? Tone.SUCCESS : Tone.NEUTRAL);
            d.add(messages.get("dailyPoint.situation"), messages.label(row.shownSituation()));
            d.add(messages.get("common.reason"), row.shownReason());
            d.add(messages.get("dailyPoint.validatedBy"), row.point().validatedBy() + " — "
                    + ui.formats().dateTime(row.point().validatedAt()));
            d.add(messages.get("dailyPoint.afterwards"), messages.get("dailyPoint.afterwards.text"));
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
        public PointRow toEntity() {
            return row;
        }
    }
}
