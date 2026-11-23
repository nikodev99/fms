package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.kpi.KpiStrip;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.finance.AccountRules;
import org.gu.fms.domain.finance.AccountType;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comptes › Comptes de trésorerie : soldes recalculés à partir des transactions, trésorerie consolidée.
 * Le solde d'ouverture se fige dès le premier mouvement.
 */
public final class AccountsScreen extends CrudScreen<FinancialAccount> {

    private final Ledger ledger;
    private final AccountRepository accounts;
    private final AppSettings settings;
    private final Clock clock;
    private volatile Map<Long, Money> balances = Map.of();
    private volatile Set<Long> withMovements = Set.of();
    private KpiStrip.Kpi consolidated;
    private KpiStrip.Kpi cash;
    private KpiStrip.Kpi mobile;
    private KpiStrip.Kpi bank;

    @Inject
    public AccountsScreen(CrudContext ui, AccountService service, Ledger ledger, AccountRepository accounts,
                          AppSettings settings, Clock clock) {
        super(ui, service);
        this.ledger = ledger;
        this.accounts = accounts;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    protected void prepare() {
        balances = ledger.balances();
        Set<Long> moved = new HashSet<>();
        for (FinancialAccount a : accounts.findAll()) {
            if (accounts.hasMovements(a.id())) {
                moved.add(a.id());
            }
        }
        withMovements = moved;
    }

    private Money balance(FinancialAccount a) {
        return balances.getOrDefault(a.id(), a.openingBalance());
    }

    @Override
    protected Node summary() {
        KpiStrip strip = new KpiStrip();
        consolidated = strip.add(messages.get("accounts.kpi.consolidated"));
        cash = strip.add(messages.get("enum.AccountType.CASH"));
        mobile = strip.add(messages.get("enum.AccountType.MOBILE_MONEY"));
        bank = strip.add(messages.get("enum.AccountType.BANK"));
        return strip;
    }

    @Override
    protected void loaded(List<FinancialAccount> items) {
        Money zero = Money.zero(settings.currency());
        Money total = zero;
        Money[] byType = {zero, zero, zero, zero};
        int counted = 0;
        for (FinancialAccount a : items) {
            Money b = balance(a);
            byType[a.type().ordinal()] = byType[a.type().ordinal()].plus(b);
            if (a.includedInConsolidated()) {
                total = total.plus(b);
                counted++;
            }
        }
        consolidated.set(ui.formats().money(total), messages.get("accounts.kpi.consolidated.caption", counted),
                total.isNegative() ? Tone.DANGER : Tone.NEUTRAL);
        cash.set(ui.formats().money(byType[AccountType.CASH.ordinal()]), null, Tone.NEUTRAL);
        mobile.set(ui.formats().money(byType[AccountType.MOBILE_MONEY.ordinal()]), null, Tone.NEUTRAL);
        bank.set(ui.formats().money(byType[AccountType.BANK.ordinal()].plus(byType[AccountType.OTHER.ordinal()])),
                null, Tone.NEUTRAL);
    }

    @Override
    protected String title() {
        return messages.get("accounts.tab.list");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("accounts.count", total)
                : messages.get("accounts.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("account.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.ACCOUNTS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("accounts.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("accounts.empty.text");
    }

    @Override
    protected String hint() {
        return messages.get("accounts.hint");
    }

    @Override
    protected void configureTable(DataTable<FinancialAccount> table) {
        table.textColumn(messages.get("account.name"), FinancialAccount::name, 200).setMinWidth(150);
        table.badgeColumn(messages.get("account.type"), a -> new Badge(messages.label(a.type()), Tone.NEUTRAL), 140);
        table.moneyColumn(messages.get("account.balance"), this::balance, 150).setMinWidth(120);
        table.textColumn(messages.get("account.consolidated.short"),
                a -> messages.get(a.includedInConsolidated() ? "common.yes" : "common.no"), 110);
        table.dateColumn(messages.get("account.openingDate.short"), FinancialAccount::openingDate, 110);
        table.badgeColumn(messages.get("common.state"), a -> a.active()
                ? new Badge(messages.get("common.active"), Tone.SUCCESS)
                : new Badge(messages.get("common.inactive"), Tone.NEUTRAL), 100);
        table.searchBy(a -> a.name() + " " + messages.label(a.type()));
    }

    @Override
    protected EntityEditor<FinancialAccount> createEditor(FinancialAccount existing) {
        boolean locked = existing != null && withMovements.contains(existing.id());
        return new Editor(ui.forms(), messages, existing, locked,
                LocalDate.ofInstant(clock.instant(), settings.zone()));
    }

    @Override
    protected String savedMessage(FinancialAccount saved, boolean created) {
        return messages.get(created ? "account.created" : "account.updated", saved.name());
    }

    @Override
    protected Object identityOf(FinancialAccount a) {
        return a.id();
    }

    private static final class Editor implements EntityEditor<FinancialAccount> {
        private final Messages messages;
        private final FinancialAccount existing;
        private final Form form;
        private final TextInput name;
        private final ChoiceInput<AccountType> type;
        private final MoneyInput opening;
        private final DateInput openingDate;
        private final CheckInput consolidated;
        private final CheckInput overdraft;
        private final CheckInput active;
        private final TextAreaInput notes;

        Editor(Forms forms, Messages messages, FinancialAccount existing, boolean locked, LocalDate today) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            name = form.text("name", messages.get("account.name")).required().maxLength(AccountRules.NAME_MAX);
            type = form.choice("type", messages.get("account.type"), AccountType.class).required();
            opening = form.money("openingBalance", messages.get("account.openingBalance")).required()
                    .help(messages.get(locked ? "account.openingLocked.help" : "account.openingBalance.help"));
            openingDate = form.date("openingDate", messages.get("account.openingDate")).required()
                    .rule(Rules.notAfter(() -> today));
            consolidated = form.check("includedInConsolidated", messages.get("account.consolidated"))
                    .help(messages.get("account.consolidated.help"));
            overdraft = form.check("overdraftAllowed", messages.get("account.overdraft"))
                    .help(messages.get("account.overdraft.help"))
                    .shownWhen(Bindings.createBooleanBinding(() -> type.getValue() == AccountType.BANK
                            || type.getValue() == AccountType.OTHER, type.valueProperty()));
            active = form.check("active", messages.get("common.activeCheck"));
            notes = form.textArea("notes", messages.get("account.notes")).maxLength(AccountRules.NOTES_MAX).span(2);
            if (existing == null) {
                type.setValue(AccountType.CASH);
                opening.setValue(Money.zero(opening.currency()));
                openingDate.setValue(today);
                consolidated.setValue(true);
                overdraft.setValue(false);
                active.setValue(true);
            } else {
                name.setValue(existing.name());
                type.setValue(existing.type()).readOnly(locked);
                opening.setValue(existing.openingBalance()).readOnly(locked);
                openingDate.setValue(existing.openingDate()).readOnly(locked);
                consolidated.setValue(existing.includedInConsolidated());
                overdraft.setValue(existing.overdraftAllowed());
                active.setValue(existing.active());
                notes.setValue(existing.notes());
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("account.create") : messages.get("account.edit", existing.name());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public FinancialAccount toEntity() {
            AccountType t = type.getValue();
            boolean od = overdraft.getValue() && (t == AccountType.BANK || t == AccountType.OTHER);
            return new FinancialAccount(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    name.getValue(), t, opening.getValue(), openingDate.getValue(), consolidated.getValue(), od,
                    active.getValue(), notes.getValue());
        }
    }
}
