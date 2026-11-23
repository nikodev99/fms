package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.collection.DriverLedgerEntry;
import org.gu.fms.domain.collection.LedgerRubric;
import org.gu.fms.domain.collection.LedgerRules;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Versements › Comptes chauffeurs : ce que chaque chauffeur doit (manquants, avances, autres dettes) et l'historique
 * de ses mouvements. Ajustement manuel avec libellé obligatoire (dette constatée, remise, correction).
 */
public final class DriverAccountsScreen extends CrudScreen<DriverAccountRow> {

    private final DriverLedgerService ledger;
    private final AppSettings settings;
    private final Clock clock;
    private KpiStrip.Kpi totalDue;
    private KpiStrip.Kpi shortages;
    private KpiStrip.Kpi advances;

    @Inject
    public DriverAccountsScreen(CrudContext ui, DriverAccountService service, DriverLedgerService ledger,
                                AppSettings settings, Clock clock) {
        super(ui, service);
        this.ledger = ledger;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    protected boolean canCreate() {
        return false;
    }

    @Override
    protected List<Button> headerActions() {
        Button adjust = new Button(messages.get("ledger.adjust"), new FmsIcon(AppIcon.EDIT, 16));
        adjust.getStyleClass().add("secondary");
        adjust.setOnAction(e -> openAdjustment(null));
        return List.of(adjust);
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        totalDue = strip.add(messages.get("ledger.kpi.totalDue"));
        shortages = strip.add(messages.get("enum.LedgerRubric.SHORTAGE"));
        advances = strip.add(messages.get("enum.LedgerRubric.ADVANCE"));
        return strip;
    }

    @Override
    protected void loaded(List<DriverAccountRow> items) {
        Money zero = Money.zero(settings.currency());
        Money total = zero;
        Money s = zero;
        Money a = zero;
        long debtors = 0;
        for (DriverAccountRow r : items) {
            total = total.plus(r.balance().totalDue());
            s = s.plus(r.balance().shortageDue());
            a = a.plus(r.balance().advanceDue());
            if (r.balance().totalDue().isPositive()) {
                debtors++;
            }
        }
        totalDue.set(ui.formats().money(total), messages.get("ledger.kpi.totalDue.caption", debtors),
                total.isPositive() ? Tone.WARNING : Tone.NEUTRAL);
        shortages.set(ui.formats().money(s), null, Tone.NEUTRAL);
        advances.set(ui.formats().money(a), null, Tone.NEUTRAL);
    }

    @Override
    protected String title() {
        return messages.get("collections.tab.accounts");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("ledger.count", total) : messages.get("ledger.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("ledger.adjust");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.LEDGER;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("ledger.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("ledger.empty.text");
    }

    @Override
    protected String hint() {
        return messages.get("ledger.hint");
    }

    @Override
    protected void configureTable(DataTable<DriverAccountRow> table) {
        table.textColumn(messages.get("ledger.driver"), r -> r.driver().displayName(), 200).setMinWidth(140);
        table.badgeColumn(messages.get("driver.status"), r -> new Badge(messages.label(r.driver().status()), Tone.NEUTRAL), 110);
        table.moneyColumn(messages.get("enum.LedgerRubric.SHORTAGE"), r -> r.balance().shortageDue(), 120);
        table.moneyColumn(messages.get("enum.LedgerRubric.ADVANCE"), r -> r.balance().advanceDue(), 120);
        table.moneyColumn(messages.get("enum.LedgerRubric.OTHER_DEBT"), r -> r.balance().otherDebtDue(), 120);
        table.moneyColumn(messages.get("ledger.totalDue"), r -> r.balance().totalDue(), 130);
        table.moneyColumn(messages.get("ledger.credit"), r -> r.balance().credit(), 110);
        table.searchBy(r -> r.driver().displayName() + " " + r.driver().phone());
    }

    @Override
    protected EntityEditor<DriverAccountRow> createEditor(DriverAccountRow existing) {
        return new Detail(existing);
    }

    @Override
    protected List<Button> editorActions(DriverAccountRow existing) {
        Button adjust = new Button(messages.get("ledger.adjust"), new FmsIcon(AppIcon.EDIT, 16));
        adjust.getStyleClass().add("secondary");
        adjust.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                openAdjustment(existing.driver());
            }
        }));
        return List.of(adjust);
    }

    @Override
    protected String savedMessage(DriverAccountRow saved, boolean created) {
        return "";
    }

    @Override
    protected Object identityOf(DriverAccountRow r) {
        return r.driver().id();
    }

    private enum Way { INCREASE, DECREASE }

    private void openAdjustment(Driver preset) {
        Form form = ui.forms().create();
        LocalDate today = LocalDate.ofInstant(clock.instant(), settings.zone());
        List<Driver> list = table().items().stream().map(DriverAccountRow::driver).toList();
        ChoiceInput<Driver> driver = form.choice("driverId", messages.get("ledger.driver"), list, Driver::displayName)
                .required().span(2);
        ChoiceInput<LedgerRubric> rubric = form.choice("rubric", messages.get("ledger.rubric"),
                        List.of(LedgerRubric.SHORTAGE, LedgerRubric.OTHER_DEBT), messages::label).required()
                .help(messages.get("ledger.rubric.help"));
        ChoiceInput<Way> way = form.choice("way", messages.get("ledger.way"), List.of(Way.values()),
                w -> messages.get("ledger.way." + w.name().toLowerCase(java.util.Locale.ROOT))).required();
        MoneyInput amount = form.money("amount", messages.get("ledger.amount")).required()
                .rule(Rules.that(Money::isPositive, "validation.positive"));
        DateInput date = form.date("date", messages.get("ledger.date")).required().rule(Rules.notAfter(() -> today));
        TextInput label = form.text("label", messages.get("ledger.label")).required().maxLength(LedgerRules.LABEL_MAX)
                .span(2).help(messages.get("ledger.label.help"));
        driver.setValue(preset);
        rubric.setValue(LedgerRubric.OTHER_DEBT);
        way.setValue(Way.INCREASE);
        date.setValue(today);
        ui.<DriverLedgerEntry>formDrawer(messages.get("ledger.adjust"))
                .subtitle(messages.get("ledger.adjust.subtitle"))
                .form(form)
                .onSave(() -> {
                    long signed = way.getValue() == Way.INCREASE ? amount.getValue().minor() : -amount.getValue().minor();
                    DriverLedgerEntry entry = new DriverLedgerEntry(null, 0, date.getValue(), null,
                            driver.getValue() == null ? null : driver.getValue().id(), rubric.getValue(),
                            Money.of(signed, settings.currency()), null, null, label.getValue(), null);
                    return () -> ledger.adjust(entry);
                })
                .onSuccess(saved -> {
                    ui.toasts().success(messages.get("ledger.adjusted"));
                    refresh();
                })
                .open();
    }

    private final class Detail implements EntityEditor<DriverAccountRow> {
        private final DriverAccountRow row;

        Detail(DriverAccountRow row) {
            this.row = row;
        }

        @Override
        public String title() {
            return messages.get("ledger.detail", row.driver().displayName());
        }

        @Override
        public Node header() {
            DriverBalance b = row.balance();
            DetailList summary = new DetailList(messages.get("ledger.balances"));
            summary.add(messages.get("enum.LedgerRubric.SHORTAGE"), ui.formats().money(b.shortageDue()));
            summary.add(messages.get("enum.LedgerRubric.ADVANCE"), ui.formats().money(b.advanceDue()));
            summary.add(messages.get("enum.LedgerRubric.OTHER_DEBT"), ui.formats().money(b.otherDebtDue()));
            if (b.credit().isPositive()) {
                summary.add(messages.get("ledger.credit"), ui.formats().money(b.credit()), Tone.SUCCESS);
            }
            summary.total(messages.get("ledger.totalDue"), ui.formats().money(b.totalDue()));

            DataTable<DriverLedgerEntry> movements = ui.tables().create();
            movements.dateColumn(messages.get("ledger.date"), DriverLedgerEntry::date, 95);
            movements.textColumn(messages.get("ledger.rubric"), e -> messages.label(e.rubric()), 110);
            movements.textColumn(messages.get("ledger.label"), DriverLedgerEntry::label, 200);
            movements.moneyColumn(messages.get("ledger.amount"), DriverLedgerEntry::amount, 110);
            javafx.scene.control.Label none = new javafx.scene.control.Label(messages.get("ledger.movements.empty"));
            none.getStyleClass().add("empty-text");
            movements.placeholder(none);
            movements.setPrefHeight(320);
            movements.setMinHeight(200);
            ui.tasks().job(() -> ledger.movements(row.driver().id())).onSuccess(movements::setItems).start();
            return new VBox(16, summary, movements);
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
        public DriverAccountRow toEntity() {
            return row;
        }
    }
}
