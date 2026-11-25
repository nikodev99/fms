package org.gu.fms.desktop.features.loan;

import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.settings.AppSettings;
import jakarta.inject.Inject;
import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
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
import org.gu.fms.desktop.platform.ui.table.ResizePolicies;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.loan.*;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finances › Emprunts : conditions, tableau d'amortissement, paiement des échéances. L'échéance indiquée par la banque
 * fait foi si elle diffère du calcul (le taux réel est alors recalculé).
 */
public final class LoansScreen extends CrudScreen<Loan> {

    private final LoanService service;
    private final LoanInstalmentRepository instalmentRepo;
    private final VehicleRepository vehicles;
    private final AccountRepository accounts;
    private final Clock clock;
    private final AppSettings settings;
    private final KpiStrip kpis = new KpiStrip();
    private final KpiStrip.Kpi kOutstanding;
    private final KpiStrip.Kpi kMonth;
    private final KpiStrip.Kpi kOverdue;
    private final KpiStrip.Kpi kInterest;
    private volatile List<Vehicle> vehicleList = List.of();
    private volatile List<FinancialAccount> accountList = List.of();
    private volatile Map<Long, List<LoanInstalment>> schedules = Map.of();

    @Inject
    public LoansScreen(CrudContext ui, LoanService service, LoanInstalmentRepository instalmentRepo,
                       VehicleRepository vehicles, AccountRepository accounts, Clock clock, AppSettings settings) {
        super(ui, service);
        this.service = service;
        this.instalmentRepo = instalmentRepo;
        this.vehicles = vehicles;
        this.accounts = accounts;
        this.clock = clock;
        this.settings = settings;
        kOutstanding = kpis.add(messages.get("loans.kpi.outstanding"));
        kMonth = kpis.add(messages.get("loans.kpi.month"));
        kOverdue = kpis.add(messages.get("loans.kpi.overdue"));
        kInterest = kpis.add(messages.get("loans.kpi.interest"));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    private Money zero() {
        return Money.zero(settings.currency());
    }

    @Override
    protected void prepare() {
        vehicleList = vehicles.findAll();
        accountList = accounts.findAll();
        Map<Long, List<LoanInstalment>> map = new HashMap<>();
        for (LoanInstalment i : instalmentRepo.findAll()) {
            map.computeIfAbsent(i.loanId(), k -> new java.util.ArrayList<>()).add(i);
        }
        schedules = map;
    }

    private List<LoanInstalment> schedule(Loan l) {
        return schedules.getOrDefault(l.id(), List.of());
    }

    private Optional<LoanInstalment> nextDue(Loan l) {
        return schedule(l).stream().filter(i -> i.status() == InstalmentStatus.DUE).findFirst();
    }

    private Optional<LoanInstalment> lastPaid(Loan l) {
        return schedule(l).stream().filter(LoanInstalment::isPaid).reduce((a, b) -> b);
    }

    private Money outstanding(Loan l) {
        return LoanService.outstanding(l, schedule(l), zero());
    }

    @Override
    protected void loaded(List<Loan> items) {
        LocalDate today = today();
        long outstanding = 0;
        long month = 0;
        long overdue = 0;
        int overdueCount = 0;
        long interest = 0;
        for (Loan l : items) {
            if (l.status() != LoanStatus.ACTIVE) {
                continue;
            }
            outstanding += outstanding(l).minor();
            for (LoanInstalment i : schedule(l)) {
                if (i.status() != InstalmentStatus.DUE) {
                    continue;
                }
                interest += i.interest().minor();
                if (i.dueDate().getYear() == today.getYear() && i.dueDate().getMonth() == today.getMonth()) {
                    month += i.total().minor();
                }
                if (i.isOverdue(today)) {
                    overdue += i.total().minor();
                    overdueCount++;
                }
            }
        }
        kOutstanding.set(ui.formats().money(Money.of(outstanding, settings.currency())), null, Tone.NEUTRAL);
        kMonth.set(ui.formats().money(Money.of(month, settings.currency())), null, Tone.INFO);
        kOverdue.set(ui.formats().money(Money.of(overdue, settings.currency())),
                messages.get("loans.kpi.overdue.caption", overdueCount), overdueCount > 0 ? Tone.DANGER : Tone.SUCCESS);
        kInterest.set(ui.formats().money(Money.of(interest, settings.currency())), null, Tone.NEUTRAL);
    }

    @Override
    protected Node summary() {
        return kpis;
    }

    @Override
    protected String title() {
        return messages.get("nav.loans");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("loans.count", total) : messages.get("loans.countFiltered", visible, total);
    }

    @Override
    protected String hint() {
        return messages.get("loans.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("loan.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.LOAN;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("loans.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("loans.empty.text");
    }

    private Badge status(Loan l) {
        if (l.status() == LoanStatus.ACTIVE && nextDue(l).map(i -> i.isOverdue(today())).orElse(false)) {
            return new Badge(messages.get("loan.status.overdue"), Tone.DANGER);
        }
        return new Badge(messages.label(l.status()), switch (l.status()) {
            case ACTIVE -> Tone.INFO;
            case REPAID -> Tone.SUCCESS;
            case CANCELLED -> Tone.NEUTRAL;
        });
    }

    private String term(Loan l) {
        return messages.get("loan.term.value", l.termCount(), messages.label(l.frequency()));
    }

    @Override
    protected void configureTable(DataTable<Loan> table) {
        table.textColumn(messages.get("loan.lender"), Loan::label, 200).setMinWidth(150);
        table.moneyColumn(messages.get("loan.principal"), Loan::principal, 130).setMinWidth(110);
        table.textColumn(messages.get("loan.annualRate"), l -> ui.formats().percent(l.annualRate()), 80);
        table.textColumn(messages.get("loan.term"), this::term, 130);
        table.moneyColumn(messages.get("loans.column.instalment"),
                l -> schedule(l).isEmpty() ? null : schedule(l).get(0).total(), 120);
        table.moneyColumn(messages.get("loans.column.outstanding"), this::outstanding, 130).setMinWidth(110);
        table.textColumn(messages.get("loans.column.nextDue"),
                l -> nextDue(l).map(i -> ui.formats().date(i.dueDate())).orElse("—"), 120);
        table.badgeColumn(messages.get("loan.status"), this::status, 110);
        table.searchBy(l -> l.label() + " " + (l.purpose() == null ? "" : l.purpose()) + " "
                + messages.label(l.status()));
    }

    @Override
    protected List<Button> editorActions(Loan existing) {
        List<Button> actions = new java.util.ArrayList<>();
        if (existing.status() == LoanStatus.ACTIVE) {
            nextDue(existing).ifPresent(i -> {
                Button pay = new Button(messages.get("loan.pay", i.seq()), new FmsIcon(AppIcon.PAY, 16));
                pay.getStyleClass().add("secondary");
                pay.setOnAction(e -> closeEditor().thenAccept(closed -> {
                    if (closed) {
                        openPayment(existing, i);
                    }
                }));
                actions.add(pay);
            });
        }
        lastPaid(existing).ifPresent(i -> {
            Button reverse = new Button(messages.get("loan.reversePayment", i.seq()), new FmsIcon(AppIcon.REVERSE, 16));
            reverse.getStyleClass().add("danger");
            reverse.setOnAction(e -> closeEditor().thenAccept(closed -> {
                if (closed) {
                    ui.dialogs().reason(messages.get("loan.reversePayment.title"),
                                    messages.get("loan.reversePayment.message", i.seq(), ui.formats().money(i.total())),
                                    messages.get("common.reason"), messages.get("loan.reversePayment.confirm"), Tone.DANGER)
                            .thenAccept(reason -> reason.ifPresent(r -> ui.run(
                                    () -> service.reversePayment(i.id(), r, null),
                                    x -> messages.get("loan.paymentReversed", i.seq()), x -> refresh())));
                }
            }));
            actions.add(reverse);
        });
        if (existing.status() != LoanStatus.CANCELLED && lastPaid(existing).isEmpty()) {
            Button cancel = new Button(messages.get("loan.cancel"), new FmsIcon(AppIcon.CANCEL, 16));
            cancel.getStyleClass().add("danger");
            cancel.setOnAction(e -> closeEditor().thenAccept(closed -> {
                if (closed) {
                    ui.dialogs().reason(messages.get("loan.cancel.title"), messages.get("loan.cancel.message"),
                                    messages.get("common.reason"), messages.get("loan.cancel"), Tone.DANGER)
                            .thenAccept(reason -> reason.ifPresent(r -> ui.run(() -> service.cancel(existing.id(), r),
                                    x -> messages.get("loan.cancelled"), x -> refresh())));
                }
            }));
            actions.add(cancel);
        }
        return actions;
    }

    private void openPayment(Loan loan, LoanInstalment i) {
        Form form = ui.forms().create();
        LocalDate today = today();
        ChoiceInput<FinancialAccount> account = form.choice("accountId", messages.get("loan.payment.account"),
                accountList.stream().filter(FinancialAccount::active).toList(), FinancialAccount::name).required();
        DateInput date = form.date("date", messages.get("loan.payment.date")).required()
                .rule(Rules.notAfter(() -> today));
        TextInput reference = form.text("reference", messages.get("loan.payment.reference")).maxLength(60);
        date.setValue(i.dueDate().isAfter(today) ? today : i.dueDate());
        if (loan.disbursementAccountId() != null) {
            account.setValue(Choices.byId(accountList, FinancialAccount::id, loan.disbursementAccountId()));
        }
        DetailList details = new DetailList();
        details.add(messages.get("loan.instalment.principal"), ui.formats().money(i.principal()));
        details.add(messages.get("loan.instalment.interest"), ui.formats().money(i.interest()));
        details.add(messages.get("loan.instalment.insurance"), ui.formats().money(i.insurance()));
        details.total(messages.get("loan.instalment.total"), ui.formats().money(i.total()));
        ui.<LoanInstalment>formDrawer(messages.get("loan.pay.title", i.seq(), loan.termCount()))
                .subtitle(loan.label() + " — " + messages.get("loan.pay.due", ui.formats().date(i.dueDate())))
                .header(details)
                .form(form)
                .saveLabel(messages.get("loan.pay.confirm"), AppIcon.PAY)
                .onSave(() -> {
                    Long accountId = account.getValue() == null ? null : account.getValue().id();
                    LocalDate d = date.getValue();
                    String ref = reference.getValue();
                    return () -> service.pay(i.id(), accountId, d, ref);
                })
                .onSuccess(paid -> {
                    ui.toasts().success(messages.get("loan.paid", i.seq()));
                    reload(l -> l.id().equals(loan.id()));
                })
                .open();
    }

    @Override
    protected EntityEditor<Loan> createEditor(Loan existing) {
        return new Editor(existing);
    }

    @Override
    protected String savedMessage(Loan saved, boolean created) {
        return messages.get(created ? "loan.created" : "loan.updated", saved.label());
    }

    @Override
    protected Object identityOf(Loan l) {
        return l.id();
    }

    private final class Editor implements EntityEditor<Loan> {
        private final Loan existing;
        private final Form form;
        private final TextInput lender;
        private final TextInput reference;
        private final TextAreaInput purpose;
        private final MoneyInput principal;
        private final PercentInput rate;
        private final NumberInput term;
        private final ChoiceInput<LoanFrequency> frequency;
        private final DateInput firstDue;
        private final MoneyInput insurance;
        private final MoneyInput bankInstalment;
        private final MultiChoiceInput<Vehicle> financed;
        private final ChoiceInput<FinancialAccount> account;
        private final DateInput disbursed;
        private final TextAreaInput notes;
        private final Label preview = new Label();

        Editor(Loan existing) {
            this.existing = existing;
            Messages m = messages;
            form = ui.forms().create();
            form.section(m.get("loan.section.lender"));
            lender = form.text("lender", m.get("loan.lender")).required().maxLength(LoanRules.LENDER_MAX)
                    .prompt(m.get("loan.lender.prompt"));
            reference = form.text("reference", m.get("loan.reference")).maxLength(LoanRules.REFERENCE_MAX);
            purpose = form.textArea("purpose", m.get("loan.purpose")).maxLength(LoanRules.PURPOSE_MAX).span(2);

            form.section(m.get("loan.section.terms"));
            principal = form.money("principal", m.get("loan.principal")).required();
            rate = form.percent("annualRate", m.get("loan.annualRate")).required().help(m.get("loan.annualRate.help"));
            term = form.number("termCount", m.get("loan.termCount")).required()
                    .rule(Rules.range(1, LoanRules.TERM_MAX));
            frequency = form.choice("frequency", m.get("loan.frequency"), LoanFrequency.class).required();
            firstDue = form.date("firstDueDate", m.get("loan.firstDueDate")).required();
            insurance = form.money("insurancePerInstalment", m.get("loan.insurance"))
                    .help(m.get("loan.insurance.help"));
            bankInstalment = form.money("bankInstalment", m.get("loan.bankInstalment")).span(2)
                    .help(m.get("loan.bankInstalment.help"));

            form.section(m.get("loan.section.vehicles"));
            List<Long> selected = existing == null ? List.of() : existing.vehicleIds();
            List<Vehicle> choices = vehicleList.stream().filter(v -> selected.contains(v.id())
                    || (v.status() != VehicleStatus.SOLD && v.status() != VehicleStatus.INACTIVE)).toList();
            financed = form.multi("vehicleIds", m.get("loan.vehicles"), choices, Vehicle::registration).span(2)
                    .help(m.get("loan.vehicles.help"));

            form.section(m.get("loan.section.disbursement"));
            Long currentAccount = existing == null ? null : existing.disbursementAccountId();
            account = form.choice("disbursementAccountId", m.get("loan.disbursementAccount"),
                    Choices.activePlus(accountList, FinancialAccount::active, FinancialAccount::id, currentAccount),
                    FinancialAccount::name).allowEmpty().help(m.get("loan.disbursementAccount.help"));
            disbursed = form.date("disbursementDate", m.get("loan.disbursementDate"));
            notes = form.textArea("notes", m.get("loan.notes")).maxLength(LoanRules.NOTES_MAX).span(2);

            if (existing == null) {
                frequency.setValue(LoanFrequency.MONTHLY);
                rate.setValue(Percent.ZERO);
            } else {
                lender.setValue(existing.lender());
                reference.setValue(existing.reference());
                purpose.setValue(existing.purpose());
                principal.setValue(existing.principal());
                rate.setValue(existing.annualRate());
                term.setValue(existing.termCount() == null ? null : existing.termCount().longValue());
                frequency.setValue(existing.frequency());
                firstDue.setValue(existing.firstDueDate());
                insurance.setValue(existing.insurancePerInstalment());
                bankInstalment.setValue(existing.bankInstalment());
                financed.setValue(choices.stream().filter(v -> selected.contains(v.id())).toList());
                account.setValue(Choices.byId(accountList, FinancialAccount::id, currentAccount));
                disbursed.setValue(existing.disbursementDate());
                notes.setValue(existing.notes());
                boolean termsLocked = existing.status() != LoanStatus.ACTIVE || lastPaid(existing).isPresent();
                for (var f : List.of(principal, insurance, bankInstalment)) {
                    f.readOnly(termsLocked);
                }
                rate.readOnly(termsLocked);
                term.readOnly(termsLocked);
                frequency.readOnly(termsLocked);
                firstDue.readOnly(termsLocked);
                if (termsLocked) {
                    principal.help(m.get("loan.termsLocked.help"));
                }
                boolean disbursementLocked = existing.disbursementTransactionId() != null
                        || existing.status() != LoanStatus.ACTIVE;
                account.readOnly(disbursementLocked);
                disbursed.readOnly(disbursementLocked);
            }
            preview.getStyleClass().add("form-preview");
            preview.setWrapText(true);
            for (Observable o : List.of(principal.valueProperty(), rate.valueProperty(), term.valueProperty(),
                    frequency.valueProperty(), firstDue.valueProperty(), insurance.valueProperty(),
                    bankInstalment.valueProperty())) {
                o.addListener(x -> updatePreview());
            }
            updatePreview();
        }

        private void updatePreview() {
            LoanSchedule.Result r = LoanService.preview(draft());
            if (r == null) {
                preview.setText(messages.get("loan.preview.incomplete"));
                return;
            }
            Money ins = Money.of(r.lines().get(0).insurance(), settings.currency());
            String rateText = r.annualRatePercent().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String text = messages.get("loan.preview", ui.formats().money(Money.of(r.annuity(), settings.currency())),
                    ui.formats().money(ins), ui.formats().money(Money.of(r.annuity() + ins.minor(), settings.currency())),
                    ui.formats().money(Money.of(r.lines().get(r.lines().size() - 1).total(), settings.currency())),
                    ui.formats().money(Money.of(r.totalInterest(), settings.currency())));
            if (r.bankOverride()) {
                text += "\n" + messages.get("loan.preview.implied", rateText);
            }
            preview.setText(text);
        }

        private Loan draft() {
            Long count = term.getValue();
            return new Loan(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    lender.getValue(), reference.getValue(), purpose.getValue(), principal.getValue(), rate.getValue(),
                    count == null ? null : Math.toIntExact(Math.min(count, Integer.MAX_VALUE)), frequency.getValue(),
                    firstDue.getValue(), insurance.getValue(), bankInstalment.getValue(), disbursed.getValue(),
                    account.getValue() == null ? null : account.getValue().id(),
                    existing == null ? null : existing.disbursementTransactionId(),
                    existing == null ? LoanStatus.ACTIVE : existing.status(),
                    existing == null ? null : existing.cancelReason(), notes.getValue(),
                    financed.getValue().stream().map(Vehicle::id).toList());
        }

        @Override
        public String title() {
            return existing == null ? messages.get("loan.create") : messages.get("loan.edit", existing.label());
        }

        @Override
        public Node header() {
            VBox box = new VBox(12, preview);
            if (existing != null) {
                List<LoanInstalment> sched = schedule(existing);
                DetailList details = new DetailList();
                details.add(messages.get("loan.status"), status(existing).text(), status(existing).tone());
                details.add(messages.get("loans.column.outstanding"), ui.formats().money(outstanding(existing)));
                long paid = sched.stream().filter(LoanInstalment::isPaid).count();
                details.add(messages.get("loan.progress"), messages.get("loan.progress.value", paid, sched.size()));
                if (existing.cancelReason() != null) {
                    details.add(messages.get("common.reason"), existing.cancelReason());
                }
                box.getChildren().addAll(details, scheduleTable(sched));
            }
            return box;
        }

        private TableView<LoanInstalment> scheduleTable(List<LoanInstalment> sched) {
            TableView<LoanInstalment> t = new TableView<>();
            t.getStyleClass().add("compact-table");
            t.setPrefHeight(Math.min(320, 40 + 30.0 * sched.size()));
            t.setColumnResizePolicy(ResizePolicies.fill());
            t.getColumns().add(col("N°", i -> Integer.toString(i.seq())));
            t.getColumns().add(col(messages.get("loan.instalment.dueDate"),
                    i -> i.dueDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"))));
            t.getColumns().add(col(messages.get("loan.instalment.principal"), i -> ui.formats().amount(i.principal())));
            t.getColumns().add(col(messages.get("loan.instalment.interest"), i -> ui.formats().amount(i.interest())));
            t.getColumns().add(col(messages.get("loan.instalment.total"), i -> ui.formats().amount(i.total())));
            t.getColumns().add(col(messages.get("loan.instalment.status"), i -> i.isPaid()
                    ? messages.label(i.status())
                    : i.isOverdue(today()) ? messages.get("loan.status.overdue") : messages.label(i.status())));
            t.getColumns().get(0).setMinWidth(32);
            t.getColumns().get(0).setMaxWidth(44);
            t.getItems().setAll(sched);
            return t;
        }

        private TableColumn<LoanInstalment, String> col(String title, java.util.function.Function<LoanInstalment, String> f) {
            TableColumn<LoanInstalment, String> c = new TableColumn<>(title);
            c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(f.apply(d.getValue())));
            c.setSortable(false);
            return c;
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public Loan toEntity() {
            return draft();
        }
    }
}
