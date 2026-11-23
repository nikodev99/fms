package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.desktop.platform.ui.view.DetailList;
import org.gu.fms.domain.finance.AccountTransfer;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.finance.TransactionRules;
import org.gu.fms.domain.finance.TransferStatus;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Comptes › Virements internes : entre deux comptes, neutres pour la trésorerie consolidée. */
public final class TransfersScreen extends CrudScreen<AccountTransfer> {

    private final TransferService transfers;
    private final AccountRepository accounts;
    private final Ledger ledger;
    private final AppSettings settings;
    private final Clock clock;
    private volatile List<FinancialAccount> accountList = List.of();
    private volatile Map<Long, FinancialAccount> accountById = Map.of();
    private volatile Map<Long, Money> balances = Map.of();

    @Inject
    public TransfersScreen(CrudContext ui, TransferService service, AccountRepository accounts, Ledger ledger,
                           AppSettings settings, Clock clock) {
        super(ui, service);
        this.transfers = service;
        this.accounts = accounts;
        this.ledger = ledger;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    protected void prepare() {
        List<FinancialAccount> all = accounts.findAll();
        accountById = all.stream().collect(Collectors.toMap(FinancialAccount::id, Function.identity()));
        accountList = all;
        balances = ledger.balances();
    }

    private String name(Long accountId) {
        FinancialAccount a = accountId == null ? null : accountById.get(accountId);
        return a == null ? "" : a.name();
    }

    @Override
    protected String title() {
        return messages.get("accounts.tab.transfers");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("transfers.count", total)
                : messages.get("transfers.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("transfer.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.TRANSFER;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("transfers.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("transfers.empty.text");
    }

    @Override
    protected void configureTable(DataTable<AccountTransfer> table) {
        table.dateColumn(messages.get("transfer.date"), AccountTransfer::date, 100).setMinWidth(95);
        table.textColumn(messages.get("transfer.from"), t -> name(t.fromAccountId()), 170).setMinWidth(120);
        table.textColumn(messages.get("transfer.to"), t -> name(t.toAccountId()), 170).setMinWidth(120);
        table.moneyColumn(messages.get("transfer.amount"), AccountTransfer::amount, 130);
        table.moneyColumn(messages.get("transfer.fee"), AccountTransfer::fee, 100);
        table.textColumn(messages.get("transfer.label"), AccountTransfer::label, 200).setMinWidth(120);
        table.badgeColumn(messages.get("common.state"), t -> t.status() == TransferStatus.VALID
                ? new Badge(messages.label(t.status()), Tone.SUCCESS)
                : new Badge(messages.label(t.status()), Tone.DANGER), 110);
        table.searchBy(t -> name(t.fromAccountId()) + " " + name(t.toAccountId()) + " " + t.label() + " " + t.reference());
    }

    @Override
    protected EntityEditor<AccountTransfer> createEditor(AccountTransfer existing) {
        return existing == null ? new Editor() : new Detail(existing);
    }

    @Override
    protected List<Button> editorActions(AccountTransfer existing) {
        if (existing.status() != TransferStatus.VALID) {
            return List.of();
        }
        Button reverse = new Button(messages.get("transfer.reverse"), new FmsIcon(AppIcon.REVERSE, 16));
        reverse.getStyleClass().add("danger");
        reverse.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                ui.dialogs().reason(messages.get("transfer.reverse.title"), messages.get("transfer.reverse.text"),
                                messages.get("common.reason"), messages.get("transfer.reverse"), Tone.DANGER)
                        .thenAccept(reason -> reason.ifPresent(r -> ui.run(() -> transfers.reverse(existing.id(), r),
                                t -> messages.get("transfer.reversed"), t -> refresh())));
            }
        }));
        return List.of(reverse);
    }

    @Override
    protected String savedMessage(AccountTransfer saved, boolean created) {
        return messages.get("transfer.created", ui.formats().money(saved.amount()), name(saved.fromAccountId()),
                name(saved.toAccountId()));
    }

    @Override
    protected Object identityOf(AccountTransfer t) {
        return t.id();
    }

    private String accountLabel(FinancialAccount a) {
        return a.name() + " — " + ui.formats().money(balances.getOrDefault(a.id(), a.openingBalance()));
    }

    private final class Editor implements EntityEditor<AccountTransfer> {
        private final Form form = ui.forms().create();
        private final DateInput date;
        private final ChoiceInput<FinancialAccount> from;
        private final ChoiceInput<FinancialAccount> to;
        private final MoneyInput amount;
        private final MoneyInput fee;
        private final TextInput label;
        private final TextInput reference;

        Editor() {
            LocalDate today = LocalDate.ofInstant(clock.instant(), settings.zone());
            List<FinancialAccount> active = accountList.stream().filter(FinancialAccount::active).toList();
            date = form.date("date", messages.get("transfer.date")).required().rule(Rules.notAfter(() -> today));
            from = form.choice("fromAccountId", messages.get("transfer.from"), active, TransfersScreen.this::accountLabel)
                    .required();
            to = form.choice("toAccountId", messages.get("transfer.to"), active, TransfersScreen.this::accountLabel)
                    .required().rule(Rules.that(a -> from.getValue() == null || !a.id().equals(from.getValue().id()),
                            "transfer.sameAccount"));
            amount = form.money("amount", messages.get("transfer.amount")).required()
                    .rule(Rules.that(Money::isPositive, "validation.positive"));
            fee = form.money("fee", messages.get("transfer.fee")).help(messages.get("transfer.fee.help"));
            label = form.text("label", messages.get("transfer.label")).maxLength(TransactionRules.LABEL_MAX).span(2);
            reference = form.text("reference", messages.get("transfer.reference"))
                    .maxLength(TransactionRules.REFERENCE_MAX);
            date.setValue(today);
        }

        @Override
        public String title() {
            return messages.get("transfer.create");
        }

        @Override
        public String subtitle() {
            return messages.get("transfer.create.subtitle");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public AccountTransfer toEntity() {
            return new AccountTransfer(null, 0, date.getValue(), from.getValue() == null ? null : from.getValue().id(),
                    to.getValue() == null ? null : to.getValue().id(), amount.getValue(), fee.getValue(),
                    label.getValue(), reference.getValue(), null, null, null, TransferStatus.VALID, null, null);
        }
    }

    private final class Detail implements EntityEditor<AccountTransfer> {
        private final AccountTransfer t;

        Detail(AccountTransfer t) {
            this.t = t;
        }

        @Override
        public String title() {
            return messages.get("transfer.detail", ui.formats().date(t.date()));
        }

        @Override
        public Node header() {
            DetailList d = new DetailList();
            d.add(messages.get("transfer.from"), name(t.fromAccountId()));
            d.add(messages.get("transfer.to"), name(t.toAccountId()));
            d.add(messages.get("transfer.amount"), ui.formats().money(t.amount()));
            d.add(messages.get("transfer.fee"), t.hasFee() ? ui.formats().money(t.fee()) : null);
            d.add(messages.get("transfer.label"), t.label());
            d.add(messages.get("transfer.reference"), t.reference());
            d.add(messages.get("common.recordedBy"), t.userName());
            d.add(messages.get("common.state"), messages.label(t.status()),
                    t.status() == TransferStatus.VALID ? Tone.SUCCESS : Tone.DANGER);
            d.add(messages.get("common.reason"), t.reversalReason());
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
        public AccountTransfer toEntity() {
            return t;
        }
    }
}
