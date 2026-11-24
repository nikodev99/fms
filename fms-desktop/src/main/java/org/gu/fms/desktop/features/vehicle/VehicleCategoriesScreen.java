package org.gu.fms.desktop.features.vehicle;

import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.vehicle.VehicleCategory;
import org.gu.fms.domain.vehicle.VehicleCategoryRules;

/** Paramètres › Flotte › Catégories de véhicules. */
public final class VehicleCategoriesScreen extends CrudScreen<VehicleCategory> {

    @Inject
    public VehicleCategoriesScreen(CrudContext ui, VehicleCategoryService service) {
        super(ui, service);
    }

    @Override
    protected String title() {
        return messages.get("settings.categories");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("categories.count", total);
    }

    @Override
    protected String hint() {
        return messages.get("categories.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("category.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.CATEGORY;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("categories.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("categories.empty.text");
    }

    @Override
    protected void configureTable(DataTable<VehicleCategory> table) {
        table.textColumn(messages.get("category.name"), VehicleCategory::name, 220);
        table.numberColumn(messages.get("category.sortOrder"), c -> (long) c.sortOrder(), null, 90);
        table.badgeColumn(messages.get("common.state"), c -> c.active()
                ? new Badge(messages.get("common.active"), Tone.SUCCESS)
                : new Badge(messages.get("common.inactive"), Tone.NEUTRAL), 110);
        table.searchBy(VehicleCategory::name);
    }

    @Override
    protected EntityEditor<VehicleCategory> createEditor(VehicleCategory existing) {
        return new Editor(ui.forms(), messages, existing);
    }

    @Override
    protected String savedMessage(VehicleCategory saved, boolean created) {
        return messages.get(created ? "category.created" : "category.updated", saved.name());
    }

    @Override
    protected Object identityOf(VehicleCategory c) {
        return c.id();
    }

    private static final class Editor implements EntityEditor<VehicleCategory> {
        private final Messages messages;
        private final VehicleCategory existing;
        private final Form form;
        private final TextInput name;
        private final NumberInput sortOrder;
        private final CheckInput active;

        Editor(Forms forms, Messages messages, VehicleCategory existing) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            name = form.text("name", messages.get("category.name")).required().maxLength(VehicleCategoryRules.NAME_MAX)
                    .prompt(messages.get("category.name.prompt"));
            sortOrder = form.number("sortOrder", messages.get("category.sortOrder"))
                    .rule(Rules.range(0, VehicleCategoryRules.SORT_MAX)).help(messages.get("category.sortOrder.help"));
            active = form.check("active", messages.get("category.active"))
                    .help(messages.get("category.active.help"));
            if (existing == null) {
                sortOrder.setValue(100L);
                active.setValue(true);
            } else {
                name.setValue(existing.name());
                sortOrder.setValue((long) existing.sortOrder());
                active.setValue(existing.active());
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("category.create") : messages.get("category.edit", existing.name());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public VehicleCategory toEntity() {
            Long order = sortOrder.getValue();
            return new VehicleCategory(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    name.getValue(), order == null ? 0 : order.intValue(), active.getValue(),
                    existing != null && existing.system());
        }
    }
}
