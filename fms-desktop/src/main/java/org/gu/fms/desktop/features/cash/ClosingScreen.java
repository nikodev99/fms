package org.gu.fms.desktop.features.cash;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.gu.fms.desktop.features.finance.ClosingRow;
import org.gu.fms.desktop.features.finance.ClosingService;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.filter.DayPicker;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.MoneyInput;
import org.gu.fms.desktop.platform.ui.form.TextAreaInput;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.finance.CashClosing;
import org.gu.fms.domain.finance.ClosingFigures;
import org.gu.fms.domain.finance.ClosingRules;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Caisse du jour › Clôture (instructions §4.5). Pour chaque compte : solde de la veille, entrées, sorties,
 * virements, solde théorique ; le caissier saisit le solde compté. Un écart est expliqué et passé en
 * « Excédent / Manquant de caisse ». La journée clôturée est verrouillée pour ce compte.
 */
public final class ClosingScreen extends CrudScreen<ClosingRow> {

    private final ClosingService closings;
    private final AppSettings settings;
    private final Clock clock;
    private final DayPicker day;
    private KpiStrip.Kpi closedCount;
    private KpiStrip.Kpi expectedTotal;
    private KpiStrip.Kpi differences;

    @Inject
    public ClosingScreen(CrudContext ui, ClosingService closings, AppSettings settings, Clock clock) {
        this(ui, new Adapter(closings), closings, settings, clock);
    }

    private ClosingScreen(CrudContext ui, Adapter adapter, ClosingService closings, AppSettings settings, Clock clock) {
        super(ui, adapter);
        this.closings = closings;
        this.settings = settings;
        this.clock = clock;
        this.day = new DayPicker(ui.formats(), this::today, messages.get("common.previousDay"),
                messages.get("common.nextDay"), messages.get("common.today"));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected List<ClosingRow> load() {
        return closings.rows(day.day());
    }

    @Override
    protected boolean canCreate() {
        return false;
    }

    @Override
    protected boolean canOpen(ClosingRow row) {
        return row.figures() != null;
    }

    @Override
    protected Node filters() {
        day.dayProperty().addListener((obs, o, d) -> refresh());
        return day;
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        closedCount = strip.add(messages.get("closing.kpi.closed"));
        expectedTotal = strip.add(messages.get("closing.kpi.expected"));
        differences = strip.add(messages.get("closing.kpi.differences"));
        return strip;
    }

    @Override
    protected void loaded(List<ClosingRow> items) {
        var c = settings.currency();
        long closed = items.stream().filter(ClosingRow::closed).count();
        long expected = items.stream().filter(r -> r.figures() != null).mapToLong(r -> r.figures().expected().minor()).sum();
        long diff = items.stream().filter(ClosingRow::closed).mapToLong(r -> r.closing().difference().minor()).sum();
        closedCount.set(closed + " / " + items.size(), null, closed == items.size() ? Tone.SUCCESS : Tone.WARNING);
        expectedTotal.set(ui.formats().money(Money.of(expected, c)), messages.get("closing.kpi.expected.caption"),
                Tone.NEUTRAL);
        differences.set(ui.formats().money(Money.of(diff, c)), null, diff == 0 ? Tone.NEUTRAL : Tone.DANGER);
    }

    @Override
    protected String title() {
        return messages.get("cash.tab.closing");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("closing.count", visible);
    }

    @Override
    protected String createLabel() {
        return messages.get("closing.close");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.LOCK;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("closing.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("closing.empty.text");
    }

    @Override
    protected String hint() {
        return messages.get("closing.hint");
    }

    private Money figure(ClosingRow r, java.util.function.Function<ClosingFigures, Money> f) {
        if (r.closed()) {
            CashClosing c = r.closing();
            return f.apply(new ClosingFigures(c.openingBalance(), c.inflows(), c.outflows(), c.transfersIn(),
                    c.transfersOut()));
        }
        return r.figures() == null ? null : f.apply(r.figures());
    }

    @Override
    protected void configureTable(DataTable<ClosingRow> table) {
        table.textColumn(messages.get("closing.account"), r -> r.account().name(), 170).setMinWidth(120);
        table.moneyColumn(messages.get("closing.opening"), r -> figure(r, ClosingFigures::opening), 120);
        table.moneyColumn(messages.get("closing.inflows"), r -> figure(r, ClosingFigures::inflows), 110);
        table.moneyColumn(messages.get("closing.outflows"), r -> figure(r, ClosingFigures::outflows), 110);
        table.moneyColumn(messages.get("closing.transfers"),
                r -> figure(r, f -> f.transfersIn().minus(f.transfersOut())), 110);
        table.moneyColumn(messages.get("closing.expected"), r -> figure(r, ClosingFigures::expected), 120);
        table.moneyColumn(messages.get("closing.actual"), r -> r.closed() ? r.closing().actual() : null, 120);
        table.moneyColumn(messages.get("closing.difference"), r -> r.closed() ? r.closing().difference() : null, 110);
        table.badgeColumn(messages.get("common.state"), this::state, 130);
        table.searchBy(r -> r.account().name());
    }

    private Badge state(ClosingRow r) {
        if (r.figures() == null) {
            return new Badge(messages.get("closing.state.notOpen"), Tone.NEUTRAL);
        }
        if (r.closed()) {
            return new Badge(messages.get("closing.state.closed"), Tone.SUCCESS);
        }
        if (r.lockedAfter()) {
            return new Badge(messages.get("closing.state.locked"), Tone.NEUTRAL);
        }
        return new Badge(messages.get("closing.state.open"), Tone.WARNING);
    }

    @Override
    protected EntityEditor<ClosingRow> createEditor(ClosingRow existing) {
        return existing.closed() || existing.lockedAfter() ? new Detail(existing) : new Editor(existing);
    }

    @Override
    protected List<Button> editorActions(ClosingRow existing) {
        if (!existing.closed() || !existing.latest()) {
            return List.of();
        }
        Button reopen = new Button(messages.get("closing.reopen"), new FmsIcon(AppIcon.UNLOCK, 16));
        reopen.getStyleClass().add("danger");
        reopen.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                ui.dialogs().reason(messages.get("closing.reopen.title"),
                                messages.get("closing.reopen.text", existing.account().name(), ui.formats().date(existing.date())),
                                messages.get("common.reason"), messages.get("closing.reopen"), Tone.DANGER)
                        .thenAccept(reason -> reason.ifPresent(r -> ui.run(
                                () -> closings.reopen(existing.closing().id(), r),
                                c -> messages.get("closing.reopened"), c -> refresh())));
            }
        }));
        return List.of(reopen);
    }

    @Override
    protected String savedMessage(ClosingRow saved, boolean created) {
        return messages.get("closing.done", saved.account().name(), ui.formats().date(saved.date()));
    }

    @Override
    protected Object identityOf(ClosingRow r) {
        return r.account().id();
    }

    /** Enregistrement d'une clôture depuis le tiroir. */
    private static final class Adapter implements CrudService<ClosingRow> {
        private final ClosingService closings;

        Adapter(ClosingService closings) {
            this.closings = closings;
        }

        @Override
        public List<ClosingRow> findAll() {
            return List.of();
        }

        @Override
        public ClosingRow save(ClosingRow row) {
            CashClosing saved = closings.close(row.account().id(), row.date(), row.actual(), row.explanation());
            return new ClosingRow(row.account(), row.date(), row.figures(), saved, false, true, null, null);
        }
    }

    private DetailList figures(ClosingFigures f) {
        DetailList d = new DetailList(messages.get("closing.figures"));
        d.add(messages.get("closing.opening.long"), ui.formats().money(f.opening()));
        d.add(messages.get("closing.inflows"), "+ " + ui.formats().money(f.inflows()));
        d.add(messages.get("closing.outflows"), "− " + ui.formats().money(f.outflows()));
        if (f.transfersIn().isPositive()) {
            d.add(messages.get("closing.transfersIn"), "+ " + ui.formats().money(f.transfersIn()));
        }
        if (f.transfersOut().isPositive()) {
            d.add(messages.get("closing.transfersOut"), "− " + ui.formats().money(f.transfersOut()));
        }
        d.total(messages.get("closing.expected"), ui.formats().money(f.expected()));
        return d;
    }

    private final class Editor implements EntityEditor<ClosingRow> {
        private final ClosingRow row;
        private final Form form = ui.forms().create();
        private final MoneyInput actual;
        private final TextAreaInput explanation;

        Editor(ClosingRow row) {
            this.row = row;
            actual = form.money("actual", messages.get("closing.actual.long")).required().span(2);
            if (row.account().overdraftAllowed()) {
                actual.allowNegative();
            }
            explanation = form.textArea("explanation", messages.get("closing.explanation"))
                    .maxLength(ClosingRules.EXPLANATION_MAX).rows(2).span(2)
                    .help(messages.get("closing.explanation.help"));
            Money expected = row.figures().expected();
            explanation.shownWhen(Bindings.createBooleanBinding(
                    () -> actual.getValue() != null && !Objects.equals(actual.getValue(), expected),
                    actual.valueProperty()));
            actual.valueProperty().addListener((obs, o, v) -> actual.help(v == null ? null
                    : messages.get("closing.differencePreview", ui.formats().money(v.minus(expected)))));
            actual.setValue(expected);
        }

        @Override
        public String title() {
            return messages.get("closing.editor.title", row.account().name());
        }

        @Override
        public String subtitle() {
            return ui.formats().date(row.date());
        }

        @Override
        public Node header() {
            return figures(row.figures());
        }

        @Override
        public String saveLabel() {
            return messages.get("closing.close");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public ClosingRow toEntity() {
            return row.withInput(actual.getValue(), explanation.getValue());
        }
    }

    private final class Detail implements EntityEditor<ClosingRow> {
        private final ClosingRow row;

        Detail(ClosingRow row) {
            this.row = row;
        }

        @Override
        public String title() {
            return messages.get("closing.detail.title", row.account().name());
        }

        @Override
        public String subtitle() {
            return ui.formats().date(row.date());
        }

        @Override
        public Node header() {
            if (!row.closed()) {
                DetailList d = figures(row.figures());
                d.add(messages.get("common.state"), messages.get("closing.lockedAfter.text"), Tone.INFO);
                return d;
            }
            CashClosing c = row.closing();
            DetailList d = figures(new ClosingFigures(c.openingBalance(), c.inflows(), c.outflows(), c.transfersIn(),
                    c.transfersOut()));
            d.add(messages.get("closing.actual.long"), ui.formats().money(c.actual()));
            d.add(messages.get("closing.difference"), ui.formats().money(c.difference()),
                    c.difference().isZero() ? Tone.SUCCESS : Tone.DANGER);
            d.add(messages.get("closing.explanation"), c.explanation());
            d.add(messages.get("closing.cashier"), c.cashier() + " — " + ui.formats().dateTime(c.closedAt()));
            if (!row.latest()) {
                d.add(messages.get("closing.reopen"), messages.get("closing.notLatest"));
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
        public ClosingRow toEntity() {
            return row;
        }
    }
}
