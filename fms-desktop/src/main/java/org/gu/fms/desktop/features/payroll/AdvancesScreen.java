package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.gu.fms.desktop.features.collection.DriverLedgerService;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.finance.ReversalService;
import org.gu.fms.desktop.features.finance.Tones;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Paie › Avances et acomptes : versement d'une avance (sortie de trésorerie + créance au compte chauffeur, retenue
 * en paie), remboursement en espèces. Une avance erronée se contre-passe ; elle quitte alors le compte chauffeur.
 */
public final class AdvancesScreen extends CrudScreen<FinancialTransaction> {

    private final AdvanceService advances;
    private final ReversalService reversals;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final DriverLedgerService ledger;
    private final AppSettings settings;
    private final Clock clock;
    private volatile Map<Long, Driver> driverById = Map.of();
    private volatile Map<Long, FinancialAccount> accountById = Map.of();

    @Inject
    public AdvancesScreen(CrudContext ui, AdvanceService advances, ReversalService reversals,
                          DriverRepository drivers, AccountRepository accounts, DriverLedgerService ledger,
                          AppSettings settings, Clock clock) {
        this(ui, new Adapter(advances), advances, reversals, drivers, accounts, ledger, settings, clock);
    }

    private AdvancesScreen(CrudContext ui, Adapter adapter, AdvanceService advances, ReversalService reversals,
                           DriverRepository drivers, AccountRepository accounts, DriverLedgerService ledger,
                           AppSettings settings, Clock clock) {
        super(ui, adapter);
        this.advances = advances;
        this.reversals = reversals;
        this.drivers = drivers;
        this.accounts = accounts;
        this.ledger = ledger;
        this.settings = settings;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        driverById = drivers.findAll().stream().collect(Collectors.toMap(Driver::id, Function.identity()));
        accountById = accounts.findAll().stream().collect(Collectors.toMap(FinancialAccount::id, Function.identity()));
    }

    private String driverName(Long id) {
        Driver d = id == null ? null : driverById.get(id);
        return d == null ? "" : d.displayName();
    }

    private String account(Long id) {
        FinancialAccount a = id == null ? null : accountById.get(id);
        return a == null ? "" : a.name();
    }

    @Override
    protected List<Button> headerActions() {
        Button repay = new Button(messages.get("advance.repay"), new FmsIcon(AppIcon.REVERSE, 16));
        repay.getStyleClass().add("secondary");
        repay.setOnAction(e -> openRepayment());
        return List.of(repay);
    }

    @Override
    protected String title() {
        return messages.get("payroll.tab.advances");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("advances.count", total)
                : messages.get("advances.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("advance.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.ADVANCE;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("advances.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("advances.empty.text");
    }

    @Override
    protected String hint() {
        return messages.get("advances.hint");
    }

    @Override
    protected void configureTable(DataTable<FinancialTransaction> table) {
        table.dateColumn(messages.get("advance.date"), FinancialTransaction::date, 100).setMinWidth(95);
        table.textColumn(messages.get("advance.driver"), t -> driverName(t.driverId()), 190).setMinWidth(130);
        table.textColumn(messages.get("advance.kind"), t -> messages.get(t.direction() == TransactionDirection.OUTFLOW
                ? (t.isReversal() ? "advance.kind.repayment" : "advance.kind.advance")
                : (t.isReversal() ? "advance.kind.advance" : "advance.kind.repayment")), 140);
        table.moneyColumn(messages.get("advance.amount"), FinancialTransaction::amount, 120);
        table.textColumn(messages.get("advance.account"), t -> account(t.accountId()), 140);
        table.textColumn(messages.get("advance.label"), FinancialTransaction::label, 220);
        table.badgeColumn(messages.get("common.state"), t -> Tones.state(messages, t), 130);
        table.searchBy(t -> driverName(t.driverId()) + " " + t.label());
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
                ui.dialogs().reason(messages.get("advance.reverse.title"), messages.get("advance.reverse.text"),
                                messages.get("common.reason"), messages.get("transaction.reverse"), Tone.DANGER)
                        .thenAccept(r -> r.ifPresent(reason -> ui.run(() -> reversals.reverse(existing.id(), reason),
                                t -> messages.get("transaction.reversed"), t -> refresh())));
            }
        }));
        return List.of(reverse);
    }

    @Override
    protected String savedMessage(FinancialTransaction saved, boolean created) {
        return messages.get("advance.created", ui.formats().money(saved.amount()), driverName(saved.driverId()));
    }

    @Override
    protected Object identityOf(FinancialTransaction t) {
        return t.id();
    }

    private void openRepayment() {
        Form form = ui.forms().create();
        LocalDate today = today();
        ChoiceInput<Driver> driver = form.choice("driverId", messages.get("advance.driver"),
                List.copyOf(driverById.values()), Driver::displayName).required().span(2);
        ChoiceInput<FinancialAccount> account = form.choice("accountId", messages.get("advance.account"),
                accountById.values().stream().filter(FinancialAccount::active).toList(), FinancialAccount::name).required();
        DateInput date = form.date("date", messages.get("advance.date")).required().rule(Rules.notAfter(() -> today));
        MoneyInput amount = form.money("amount", messages.get("advance.amount")).required()
                .rule(Rules.that(Money::isPositive, "validation.positive"));
        TextInput label = form.text("label", messages.get("advance.label")).maxLength(TransactionRules.LABEL_MAX).span(2);
        driver.valueProperty().addListener((obs, o, d) -> {
            if (d != null) {
                ui.tasks().job(() -> ledger.balance(d.id()).advanceDue()).onSuccess(due ->
                        amount.help(messages.get("advance.due", ui.formats().money(due)))).start();
            }
        });
        date.setValue(today);
        ui.<FinancialTransaction>formDrawer(messages.get("advance.repay"))
                .subtitle(messages.get("advance.repay.subtitle"))
                .form(form)
                .onSave(() -> {
                    Long d = driver.getValue() == null ? null : driver.getValue().id();
                    Long a = account.getValue() == null ? null : account.getValue().id();
                    LocalDate day = date.getValue();
                    Money m = amount.getValue();
                    String l = label.getValue();
                    return () -> advances.repay(d, a, day, m, l);
                })
                .onSuccess(t -> {
                    ui.toasts().success(messages.get("advance.repaid", ui.formats().money(t.amount())));
                    refresh();
                })
                .open();
    }

    /** Liste des avances et enregistrement d'une nouvelle avance. */
    private static final class Adapter implements CrudService<FinancialTransaction> {
        private final AdvanceService advances;

        Adapter(AdvanceService advances) {
            this.advances = advances;
        }

        @Override
        public List<FinancialTransaction> findAll() {
            return advances.movements();
        }

        @Override
        public FinancialTransaction save(FinancialTransaction t) {
            return advances.grant(t.driverId(), t.accountId(), t.date(), t.amount(), t.label());
        }
    }

    private final class Editor implements EntityEditor<FinancialTransaction> {
        private final Form form = ui.forms().create();
        private final ChoiceInput<Driver> driver;
        private final ChoiceInput<FinancialAccount> account;
        private final DateInput date;
        private final MoneyInput amount;
        private final TextInput label;

        Editor() {
            LocalDate today = today();
            driver = form.choice("driverId", messages.get("advance.driver"),
                            driverById.values().stream().filter(d -> d.status().canBeAssigned()).toList(), Driver::displayName)
                    .required().span(2);
            account = form.choice("accountId", messages.get("advance.account"),
                            accountById.values().stream().filter(FinancialAccount::active).toList(), FinancialAccount::name)
                    .required();
            date = form.date("date", messages.get("advance.date")).required().rule(Rules.notAfter(() -> today));
            amount = form.money("amount", messages.get("advance.amount")).required()
                    .rule(Rules.that(Money::isPositive, "validation.positive")).help(messages.get("advance.amount.help"));
            label = form.text("label", messages.get("advance.label")).maxLength(TransactionRules.LABEL_MAX).span(2);
            date.setValue(today);
            accountById.values().stream().filter(FinancialAccount::active).findFirst().ifPresent(account::setValue);
        }

        @Override
        public String title() {
            return messages.get("advance.create");
        }

        @Override
        public String subtitle() {
            return messages.get("advance.create.subtitle");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public FinancialTransaction toEntity() {
            return new FinancialTransaction(null, 0, date.getValue(), null, TransactionDirection.OUTFLOW, null, null,
                    account.getValue() == null ? null : account.getValue().id(), amount.getValue(), null,
                    driver.getValue() == null ? null : driver.getValue().id(), label.getValue(), null,
                    TransactionSource.ADVANCE, null, null, null, null);
        }
    }

    private final class Detail implements EntityEditor<FinancialTransaction> {
        private final FinancialTransaction t;

        Detail(FinancialTransaction t) {
            this.t = t;
        }

        @Override
        public String title() {
            return driverName(t.driverId());
        }

        @Override
        public String subtitle() {
            return ui.formats().date(t.date());
        }

        @Override
        public Node header() {
            DetailList d = new DetailList();
            d.add(messages.get("advance.amount"), ui.formats().money(t.amount()));
            d.add(messages.get("advance.account"), account(t.accountId()));
            d.add(messages.get("advance.label"), t.label());
            d.add(messages.get("common.recordedBy"), t.userName() + " — " + ui.formats().dateTime(t.recordedAt()));
            d.add(messages.get("common.state"), Tones.state(messages, t).text(), Tones.state(messages, t).tone());
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
