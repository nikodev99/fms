package org.gu.fms.desktop.features.allocation;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Screen;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.allocation.AllocationBasis;
import org.gu.fms.domain.allocation.AllocationPurpose;
import org.gu.fms.domain.allocation.AllocationRule;
import org.gu.fms.domain.allocation.AllocationRules;

/** Paramètres › Paie et provisions › Provisions. */
public final class AllocationRulesScreen extends CrudScreen<AllocationRule> {

    @Inject
    public AllocationRulesScreen(CrudContext ui, AllocationRuleService service) {
        super(ui, service);
    }

    @Override
    protected String title() {
        return messages.get("settings.provisions");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("allocations.count", total);
    }

    @Override
    protected String hint() {
        return messages.get("allocations.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("allocation.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.PROVISIONS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("allocations.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("allocations.empty.text");
    }

    static String value(AllocationRule r, Formats formats, Messages messages) {
        if (r.basis() == null) {
            return "";
        }
        return switch (r.basis()) {
            case COLLECTIONS_PERCENT, NET_COLLECTIONS_PERCENT -> formats.percent(r.rate());
            case FIXED_MONTHLY -> messages.get("allocation.value.monthly", formats.money(r.amount()));
            case FIXED_MONTHLY_PER_VEHICLE -> messages.get("allocation.value.perVehicle", formats.money(r.amount()));
            case PER_KM -> messages.get("allocation.value.perKm", formats.money(r.amount()));
        };
    }

    @Override
    protected void configureTable(DataTable<AllocationRule> table) {
        table.textColumn(messages.get("allocation.name"), AllocationRule::name, 180);
        table.textColumn(messages.get("allocation.purpose"), r -> messages.label(r.purpose()), 170);
        table.textColumn(messages.get("allocation.basis"), r -> messages.label(r.basis()), 220);
        table.textColumn(messages.get("allocation.value"), r -> value(r, ui.formats(), messages), 150);
        table.badgeColumn(messages.get("common.state"), r -> r.active()
                ? new Badge(messages.get("common.active"), Tone.SUCCESS)
                : new Badge(messages.get("common.inactive"), Tone.NEUTRAL), 100);
        table.searchBy(r -> r.name() + " " + messages.label(r.purpose()) + " " + r.method());
    }

    @Override
    protected EntityEditor<AllocationRule> createEditor(AllocationRule existing) {
        return new Editor(ui.forms(), messages, existing);
    }

    @Override
    protected String savedMessage(AllocationRule saved, boolean created) {
        return messages.get(created ? "allocation.created" : "allocation.updated", saved.name());
    }

    @Override
    protected Object identityOf(AllocationRule r) {
        return r.id();
    }

    private static final class Editor implements EntityEditor<AllocationRule> {
        private final Messages messages;
        private final AllocationRule existing;
        private final Form form;
        private final TextInput name;
        private final ChoiceInput<AllocationPurpose> purpose;
        private final ChoiceInput<AllocationBasis> basis;
        private final PercentInput rate;
        private final MoneyInput amount;
        private final CheckInput active;
        private final TextAreaInput method;

        Editor(Forms forms, Messages messages, AllocationRule existing) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            name = form.text("name", messages.get("allocation.name")).required().maxLength(AllocationRules.NAME_MAX);
            purpose = form.choice("purpose", messages.get("allocation.purpose"), AllocationPurpose.class).required();
            basis = form.choice("basis", messages.get("allocation.basis"), AllocationBasis.class).required().span(2);
            var percentBasis = Bindings.createBooleanBinding(
                    () -> basis.getValue() != null && basis.getValue().isPercentage(), basis.valueProperty());
            var amountBasis = Bindings.createBooleanBinding(
                    () -> basis.getValue() != null && !basis.getValue().isPercentage(), basis.valueProperty());
            rate = form.percent("rate", messages.get("allocation.rate")).shownWhen(percentBasis)
                    .help(messages.get("allocation.rate.help"));
            amount = form.money("amount", messages.get("allocation.amount")).shownWhen(amountBasis);
            active = form.check("active", messages.get("common.activeCheck"));
            method = form.textArea("method", messages.get("allocation.method")).required()
                    .maxLength(AllocationRules.METHOD_MAX).help(messages.get("allocation.method.help"));
            if (existing == null) {
                purpose.setValue(AllocationPurpose.MAINTENANCE);
                basis.setValue(AllocationBasis.COLLECTIONS_PERCENT);
                active.setValue(true);
            } else {
                name.setValue(existing.name());
                purpose.setValue(existing.purpose());
                basis.setValue(existing.basis());
                rate.setValue(existing.rate());
                amount.setValue(existing.amount());
                active.setValue(existing.active());
                method.setValue(existing.method());
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("allocation.create") : messages.get("allocation.edit", existing.name());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public AllocationRule toEntity() {
            return new AllocationRule(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    name.getValue(), purpose.getValue(), basis.getValue(), rate.getValue(), amount.getValue(),
                    active.getValue(), method.getValue());
        }
    }
}
