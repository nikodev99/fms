package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.AccountRepository;
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
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.finance.TransactionRules;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.payroll.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Paie › Bulletins (instructions §4.7). Préparer le mois crée un brouillon par chauffeur ; chaque brouillon se
 * revoit (ajustement, retenues), se valide (montants figés, retenues portées au compte chauffeur), puis le paiement
 * fait hors application est enregistré.
 */
public final class PayslipsScreen extends CrudScreen<Payslip> {

    private final PayrollService payroll;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final AppSettings settings;
    private final Clock clock;
    private final MonthPicker month;
    private volatile Map<Long, Driver> driverById = Map.of();
    private volatile List<FinancialAccount> accountList = List.of();
    private volatile Map<Long, PayrollResult> calculations = Map.of();
    private KpiStrip.Kpi gross;
    private KpiStrip.Kpi net;
    private KpiStrip.Kpi cost;
    private KpiStrip.Kpi progress;

    @Inject
    public PayslipsScreen(CrudContext ui, PayrollService service, DriverRepository drivers,
                          AccountRepository accounts, AppSettings settings, Clock clock) {
        super(ui, service);
        this.payroll = service;
        this.drivers = drivers;
        this.accounts = accounts;
        this.settings = settings;
        this.clock = clock;
        this.month = new MonthPicker(YearMonth.from(today()), 24, settings.locale());
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    private YearMonth period() {
        return month.getValue() == null ? YearMonth.from(today()) : month.getValue();
    }

    @Override
    protected void prepare() {
        driverById = drivers.findAll().stream().collect(Collectors.toMap(Driver::id, Function.identity()));
        accountList = accounts.findActive();
    }

    @Override
    protected List<Payslip> load() {
        List<Payslip> result = new ArrayList<>();
        Map<Long, PayrollResult> calc = new HashMap<>();
        for (Payslip p : payroll.findForPeriod(period())) {
            Payslip full = payroll.withLines(p.id());
            result.add(full);
            if (full.status() == PayslipStatus.DRAFT) {
                calc.put(full.id(), payroll.calculation(full));
            }
        }
        calculations = calc;
        return result;
    }

    private String driverName(Long id) {
        Driver d = id == null ? null : driverById.get(id);
        return d == null ? "" : d.displayName();
    }

    @Override
    protected boolean canCreate() {
        return false;
    }

    @Override
    protected Node filters() {
        month.valueProperty().addListener((obs, o, m) -> refresh());
        return month;
    }

    @Override
    protected List<Button> headerActions() {
        Button recompute = new Button(messages.get("payroll.recompute"), new FmsIcon(AppIcon.CALCULATE, 16));
        recompute.getStyleClass().add("secondary");
        recompute.setOnAction(e -> {
            YearMonth p = period();
            ui.run(() -> payroll.recomputeDrafts(p), n -> messages.get("payroll.recomputed", n), n -> refresh());
        });
        Button prepare = new Button(messages.get("payroll.prepare"), new FmsIcon(AppIcon.ADD, 16));
        prepare.getStyleClass().add("primary");
        prepare.setOnAction(e -> {
            YearMonth p = period();
            ui.run(() -> payroll.prepare(p), n -> messages.get("payroll.prepared", n,
                    month.getConverter().toString(p)), n -> refresh());
        });
        return List.of(recompute, prepare);
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        gross = strip.add(messages.get("payroll.kpi.gross"));
        net = strip.add(messages.get("payroll.kpi.net"));
        cost = strip.add(messages.get("payroll.kpi.cost"));
        progress = strip.add(messages.get("payroll.kpi.progress"));
        return strip;
    }

    @Override
    protected void loaded(List<Payslip> items) {
        var c = settings.currency();
        List<Payslip> live = items.stream().filter(p -> p.status() != PayslipStatus.CANCELLED).toList();
        gross.set(ui.formats().money(Money.of(live.stream().mapToLong(p -> p.gross().minor()).sum(), c)), null,
                Tone.NEUTRAL);
        net.set(ui.formats().money(Money.of(live.stream().mapToLong(p -> p.netPayable().minor()).sum(), c)), null,
                Tone.NEUTRAL);
        cost.set(ui.formats().money(Money.of(live.stream().mapToLong(p -> p.employerCost().minor()).sum(), c)),
                messages.get("payroll.kpi.cost.caption"), Tone.NEUTRAL);
        long paid = live.stream().filter(p -> p.status() == PayslipStatus.PAID).count();
        long drafts = live.stream().filter(p -> p.status() == PayslipStatus.DRAFT).count();
        progress.set(paid + " / " + live.size(), messages.get("payroll.kpi.progress.caption", drafts),
                live.isEmpty() || paid < live.size() ? Tone.WARNING : Tone.SUCCESS);
    }

    @Override
    protected String title() {
        return messages.get("payroll.tab.payslips");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("payroll.count", total)
                : messages.get("payroll.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("payroll.prepare");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.PAYROLL;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("payroll.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("payroll.empty.text");
    }

    @Override
    protected void configureTable(DataTable<Payslip> table) {
        table.textColumn(messages.get("payroll.driver"), p -> driverName(p.driverId()), 180).setMinWidth(130);
        table.textColumn(messages.get("payroll.scheme"), p -> messages.label(p.payScheme()), 140);
        table.moneyColumn(messages.get("payroll.base.short"), Payslip::collectionsBase, 120);
        table.moneyColumn(messages.get("payroll.gross"), Payslip::gross, 120);
        table.moneyColumn(messages.get("payroll.deductions"), p -> p.employeeContributions().plus(p.its()), 110);
        table.moneyColumn(messages.get("payroll.recoveries"), p -> p.recoveries().total(), 110);
        table.moneyColumn(messages.get("payroll.netPayable"), Payslip::netPayable, 120);
        table.moneyColumn(messages.get("payroll.employerCost.short"), Payslip::employerCost, 120);
        table.badgeColumn(messages.get("common.state"), this::state, 110);
        table.searchBy(p -> driverName(p.driverId()) + " " + messages.label(p.status()));
    }

    private Badge state(Payslip p) {
        Tone tone = switch (p.status()) {
            case DRAFT -> Tone.WARNING;
            case VALIDATED -> Tone.INFO;
            case PAID -> Tone.SUCCESS;
            case CANCELLED -> Tone.NEUTRAL;
        };
        return new Badge(messages.label(p.status()), tone);
    }

    @Override
    protected EntityEditor<Payslip> createEditor(Payslip existing) {
        return existing.status() == PayslipStatus.DRAFT ? new Editor(existing) : new Detail(existing);
    }

    @Override
    protected List<Button> editorActions(Payslip p) {
        List<Button> actions = new ArrayList<>();
        switch (p.status()) {
            case DRAFT -> {
                actions.add(action("payroll.validate", AppIcon.VALIDATE, "secondary", () -> ui.dialogs().confirm(
                        messages.get("payroll.validate.title", driverName(p.driverId())),
                        messages.get("payroll.validate.text", ui.formats().money(p.netPayable())),
                        messages.get("payroll.validate"), Tone.WARNING).thenAccept(ok -> {
                    if (ok) {
                        ui.run(() -> payroll.validate(p.id()), s -> messages.get("payroll.validated"),
                                s -> refresh());
                    }
                })));
                actions.add(cancelAction(p));
            }
            case VALIDATED -> {
                actions.add(action("payroll.pay", AppIcon.PAY, "secondary", () -> openPayment(p)));
                actions.add(cancelAction(p));
            }
            case PAID -> actions.add(action("payroll.unpay", AppIcon.REVERSE, "danger", () -> ui.dialogs().reason(
                            messages.get("payroll.unpay.title"), messages.get("payroll.unpay.text"),
                            messages.get("common.reason"), messages.get("payroll.unpay"), Tone.DANGER)
                    .thenAccept(r -> r.ifPresent(reason -> ui.run(() -> payroll.reversePayment(p.id(), reason),
                            t -> messages.get("payroll.unpaid"), t -> refresh())))));
            case CANCELLED -> {
            }
        }
        return actions;
    }

    private Button cancelAction(Payslip p) {
        return action("payroll.cancel", AppIcon.CANCEL, "danger", () -> ui.dialogs().reason(
                        messages.get("payroll.cancel.title"), messages.get(p.status() == PayslipStatus.VALIDATED
                                ? "payroll.cancel.textValidated" : "payroll.cancel.text"),
                        messages.get("common.reason"), messages.get("payroll.cancel"), Tone.DANGER)
                .thenAccept(r -> r.ifPresent(reason -> ui.run(() -> payroll.cancel(p.id(), reason),
                        s -> messages.get("payroll.cancelled"), s -> refresh()))));
    }

    /** Bouton du tiroir : ferme d'abord le tiroir (avec confirmation si modifié), puis lance l'action. */
    private Button action(String key, AppIcon icon, String style, Runnable then) {
        Button b = new Button(messages.get(key), new FmsIcon(icon, 16));
        b.getStyleClass().add(style);
        b.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                then.run();
            }
        }));
        return b;
    }

    private void openPayment(Payslip p) {
        Form form = ui.forms().create();
        LocalDate today = today();
        ChoiceInput<FinancialAccount> account = form.choice("accountId", messages.get("payroll.payment.account"),
                accountList, FinancialAccount::name).required();
        DateInput date = form.date("date", messages.get("payroll.payment.date")).required()
                .rule(Rules.notAfter(() -> today));
        TextInput reference = form.text("reference", messages.get("payroll.payment.reference"))
                .maxLength(TransactionRules.REFERENCE_MAX).span(2).help(messages.get("payroll.payment.reference.help"));
        date.setValue(today);
        if (!accountList.isEmpty()) {
            account.setValue(accountList.get(0));
        }
        DetailList summary = new DetailList();
        summary.add(messages.get("payroll.driver"), driverName(p.driverId()));
        summary.add(messages.get("payroll.period"), month.getConverter().toString(p.period()));
        summary.total(messages.get("payroll.netPayable"), ui.formats().money(p.netPayable()));
        ui.<Payslip>formDrawer(messages.get("payroll.pay"))
                .subtitle(messages.get("payroll.payment.subtitle"))
                .header(summary)
                .form(form)
                .saveLabel(messages.get("payroll.pay"), AppIcon.PAY)
                .onSave(() -> {
                    Long accountId = account.getValue() == null ? null : account.getValue().id();
                    LocalDate d = date.getValue();
                    String ref = reference.getValue();
                    return () -> payroll.pay(p.id(), accountId, d, ref);
                })
                .onSuccess(s -> {
                    ui.toasts().success(messages.get("payroll.paid", driverName(s.driverId())));
                    refresh();
                })
                .open();
    }

    @Override
    protected String savedMessage(Payslip saved, boolean created) {
        return messages.get("payroll.draftSaved", driverName(saved.driverId()));
    }

    @Override
    protected Object identityOf(Payslip p) {
        return p.id();
    }

    private DetailList calculation(Payslip p) {
        DetailList d = new DetailList(messages.get("payroll.calculation"));
        d.add(messages.get("payroll.base"), ui.formats().money(p.collectionsBase()));
        d.add(messages.get("payroll.fixed"), p.fixedPart().isZero() ? null : ui.formats().money(p.fixedPart()));
        d.add(messages.get("payroll.commission"), p.commission().isZero() ? null : ui.formats().money(p.commission()));
        d.add(messages.get("payroll.adjustment"), p.adjustment().isZero() ? null : ui.formats().money(p.adjustment()));
        d.add(messages.get("payroll.gross"), ui.formats().money(p.gross()));
        for (PayslipLine l : p.lines()) {
            if (l.payer() == ContributionPayer.EMPLOYEE) {
                d.add(l.name() + " (" + ui.formats().percent(l.rate()) + ")", "− " + ui.formats().money(l.amount()));
            }
        }
        d.add(messages.get("payroll.its", p.itsParts().stripTrailingZeros().toPlainString()),
                p.its().isZero() ? null : "− " + ui.formats().money(p.its()));
        d.add(messages.get("payroll.netBefore"), ui.formats().money(p.netBeforeRecoveries()));
        d.add(messages.get("enum.LedgerRubric.ADVANCE"),
                p.advanceRecovered().isZero() ? null : "− " + ui.formats().money(p.advanceRecovered()));
        d.add(messages.get("enum.LedgerRubric.SHORTAGE"),
                p.shortageRecovered().isZero() ? null : "− " + ui.formats().money(p.shortageRecovered()));
        d.add(messages.get("enum.LedgerRubric.OTHER_DEBT"),
                p.otherDebtRecovered().isZero() ? null : "− " + ui.formats().money(p.otherDebtRecovered()));
        d.add(messages.get("payroll.employerCharges"), ui.formats().money(p.employerCharges()));
        d.add(messages.get("payroll.employerCost"), ui.formats().money(p.employerCost()));
        d.total(messages.get("payroll.netPayable"), ui.formats().money(p.netPayable()));
        return d;
    }

    private final class Editor implements EntityEditor<Payslip> {
        private final Payslip p;
        private final PayrollResult calc;
        private final Form form = ui.forms().create();
        private final MoneyInput adjustment;
        private final TextInput adjustmentReason;
        private final MoneyInput advance;
        private final MoneyInput shortage;
        private final MoneyInput other;
        private final TextAreaInput note;

        Editor(Payslip p) {
            this.p = p;
            this.calc = calculations.get(p.id());
            form.section(messages.get("payroll.section.adjustment"));
            adjustment = form.money("adjustment", messages.get("payroll.adjustment")).allowNegative()
                    .help(messages.get("payroll.adjustment.help"));
            adjustmentReason = form.text("adjustmentReason", messages.get("payroll.adjustmentReason")).maxLength(300);
            adjustmentReason.shownWhen(Bindings.createBooleanBinding(
                    () -> adjustment.getValue() != null && !adjustment.getValue().isZero(), adjustment.valueProperty()));
            form.section(messages.get("payroll.section.recoveries"));
            advance = recovery("advanceRecovered", "enum.LedgerRubric.ADVANCE", calc == null ? null
                    : calc.maxRecoveries().advance());
            shortage = recovery("shortageRecovered", "enum.LedgerRubric.SHORTAGE", calc == null ? null
                    : calc.maxRecoveries().shortage());
            other = recovery("otherDebtRecovered", "enum.LedgerRubric.OTHER_DEBT", calc == null ? null
                    : calc.maxRecoveries().otherDebt());
            note = form.textArea("recoveryNote", messages.get("payroll.recoveryNote")).maxLength(300).rows(2).span(2)
                    .help(messages.get("payroll.recoveryNote.help"));
            adjustment.setValue(p.adjustment());
            adjustmentReason.setValue(p.adjustmentReason());
            advance.setValue(p.advanceRecovered());
            shortage.setValue(p.shortageRecovered());
            other.setValue(p.otherDebtRecovered());
            note.setValue(p.recoveryNote());
        }

        private MoneyInput recovery(String name, String labelKey, Money max) {
            MoneyInput input = form.money(name, messages.get(labelKey)).required();
            if (max != null) {
                input.help(messages.get("payroll.recovery.max", ui.formats().money(max)))
                        .rule(Rules.that(v -> v.compareTo(max) <= 0, "payroll.recovery.aboveMaxShort"));
            }
            return input;
        }

        @Override
        public String title() {
            return driverName(p.driverId());
        }

        @Override
        public String subtitle() {
            return messages.get("payroll.editor.subtitle", month.getConverter().toString(p.period()));
        }

        @Override
        public Node header() {
            VBox box = new VBox(12, calculation(p));
            if (calc != null && !calc.warnings().isEmpty()) {
                DetailList w = new DetailList();
                calc.warnings().forEach(k -> w.add(messages.get("payroll.warning"), messages.get(k), Tone.WARNING));
                box.getChildren().add(0, w);
            }
            return box;
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public String saveLabel() {
            return messages.get("payroll.saveDraft");
        }

        @Override
        public Payslip toEntity() {
            Money zero = Money.zero(settings.currency());
            return new Payslip(p.id(), p.version(), p.period(), p.driverId(), p.payScheme(), p.collectionsBase(),
                    p.fixedPart(), p.commission(), adjustment.getValue() == null ? zero : adjustment.getValue(),
                    adjustmentReason.getValue(), p.gross(), p.employeeContributions(), p.its(), p.itsParts(),
                    p.netBeforeRecoveries(), advance.getValue(), shortage.getValue(), other.getValue(), note.getValue(),
                    p.netPayable(), p.employerCharges(), p.employerCost(), p.status(), null, null, null, null, null,
                    null, null, p.lines());
        }
    }

    private final class Detail implements EntityEditor<Payslip> {
        private final Payslip p;

        Detail(Payslip p) {
            this.p = p;
        }

        @Override
        public String title() {
            return driverName(p.driverId());
        }

        @Override
        public String subtitle() {
            return messages.get("payroll.detail.subtitle", month.getConverter().toString(p.period()),
                    messages.label(p.status()));
        }

        @Override
        public Node header() {
            DetailList state = new DetailList();
            state.add(messages.get("payroll.adjustmentReason"), p.adjustmentReason());
            state.add(messages.get("payroll.recoveryNote"), p.recoveryNote());
            state.add(messages.get("payroll.validatedBy"), p.validatedBy() == null ? null
                    : p.validatedBy() + " — " + ui.formats().dateTime(p.validatedAt()));
            state.add(messages.get("payroll.payment.date"), p.paidDate() == null ? null : ui.formats().date(p.paidDate()));
            state.add(messages.get("payroll.payment.reference"), p.paymentReference());
            state.add(messages.get("common.reason"), p.cancelReason());
            return new VBox(12, calculation(p), state);
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
        public Payslip toEntity() {
            return p;
        }
    }
}
