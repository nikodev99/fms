package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.finance.CategoryRules;
import org.gu.fms.domain.finance.TransactionCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionNature;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Paramètres › Finances › Catégories de transactions. Les catégories système (versements, virements, clôture, paie,
 * entretien, emprunts) sont alimentées par les modules : consultation seule. Les autres se modifient ; une catégorie
 * déjà utilisée garde son sens et sa nature, et se désactive au lieu d'être supprimée.
 */
public final class CategoriesScreen extends CrudScreen<TransactionCategory> {

    private final CategoryRepository categories;
    private volatile Set<Long> used = Set.of();

    @Inject
    public CategoriesScreen(CrudContext ui, CategoryService service, CategoryRepository categories) {
        super(ui, service);
        this.categories = categories;
    }

    @Override
    protected void prepare() {
        Set<Long> ids = new HashSet<>();
        for (TransactionCategory c : categories.findAll()) {
            if (categories.isUsed(c.id())) {
                ids.add(c.id());
            }
        }
        used = ids;
    }

    @Override
    protected String title() {
        return messages.get("transactions.tab.categories");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("txCategories.count", total)
                : messages.get("txCategories.countFiltered", visible, total);
    }

    @Override
    protected String createLabel() {
        return messages.get("txCategory.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.CATEGORY;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("txCategories.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("txCategories.empty.text");
    }

    @Override
    protected String hint() {
        return messages.get("txCategories.hint");
    }

    @Override
    protected Node filters() {
        ComboBox<TransactionDirection> direction = new ComboBox<>();
        direction.getItems().add(null);
        direction.getItems().addAll(TransactionDirection.values());
        direction.setPromptText(messages.get("transactions.filter.allDirections"));
        direction.setButtonCell(Tones.cell(messages, messages.get("transactions.filter.allDirections")));
        direction.setCellFactory(l -> Tones.cell(messages, messages.get("transactions.filter.allDirections")));
        direction.valueProperty().addListener((obs, o, d) -> table().filterProperty().set(c -> d == null || c.direction() == d));
        return direction;
    }

    @Override
    protected void configureTable(DataTable<TransactionCategory> table) {
        table.textColumn(messages.get("txCategory.name"), TransactionCategory::name, 240).setMinWidth(160);
        table.badgeColumn(messages.get("txCategory.direction"), c -> Tones.direction(messages, c.direction()), 110);
        table.textColumn(messages.get("txCategory.nature"), c -> messages.label(c.nature()), 180).setMinWidth(130);
        table.textColumn(messages.get("txCategory.directEntry.short"),
                c -> messages.get(c.directEntry() ? "common.yes" : "common.no"), 110);
        table.badgeColumn(messages.get("txCategory.kind"), c -> c.system()
                ? new Badge(messages.get("txCategory.system"), Tone.INFO)
                : new Badge(messages.get("txCategory.custom"), Tone.NEUTRAL), 110);
        table.badgeColumn(messages.get("common.state"), c -> c.active()
                ? new Badge(messages.get("common.active"), Tone.SUCCESS)
                : new Badge(messages.get("common.inactive"), Tone.NEUTRAL), 100);
        table.searchBy(c -> c.name() + " " + messages.label(c.nature()) + " " + messages.label(c.direction()));
    }

    @Override
    protected EntityEditor<TransactionCategory> createEditor(TransactionCategory existing) {
        return new Editor(ui.forms(), messages, existing, existing != null && used.contains(existing.id()));
    }

    @Override
    protected String savedMessage(TransactionCategory saved, boolean created) {
        return messages.get(created ? "txCategory.created" : "txCategory.updated", saved.name());
    }

    @Override
    protected Object identityOf(TransactionCategory c) {
        return c.id();
    }

    private static final class Editor implements EntityEditor<TransactionCategory> {
        private final Messages messages;
        private final TransactionCategory existing;
        private final Form form;
        private final TextInput name;
        private final ChoiceInput<TransactionDirection> direction;
        private final ChoiceInput<TransactionNature> nature;
        private final CheckInput directEntry;
        private final CheckInput requiresVehicle;
        private final CheckInput active;
        private final boolean system;

        Editor(Forms forms, Messages messages, TransactionCategory existing, boolean used) {
            this.messages = messages;
            this.existing = existing;
            this.system = existing != null && existing.system();
            form = forms.create();
            name = form.text("name", messages.get("txCategory.name")).required().maxLength(CategoryRules.NAME_MAX).span(2);
            direction = form.choice("direction", messages.get("txCategory.direction"), TransactionDirection.class)
                    .required();
            List<TransactionNature> natures = Arrays.stream(TransactionNature.values())
                    .filter(n -> n != TransactionNature.TRANSFER || system).toList();
            nature = form.choice("nature", messages.get("txCategory.nature"), natures, messages::label).required()
                    .help(messages.get("txCategory.nature.help"));
            directEntry = form.check("directEntry", messages.get("txCategory.directEntry"))
                    .help(messages.get("txCategory.directEntry.help"));
            requiresVehicle = form.check("requiresVehicle", messages.get("txCategory.requiresVehicle"));
            active = form.check("active", messages.get("common.activeCheck"));
            if (existing == null) {
                direction.setValue(TransactionDirection.OUTFLOW);
                nature.setValue(TransactionNature.OPERATING);
                directEntry.setValue(true);
                requiresVehicle.setValue(false);
                active.setValue(true);
            } else {
                name.setValue(existing.name());
                direction.setValue(existing.direction()).readOnly(system || used);
                nature.setValue(existing.nature()).readOnly(system || used);
                directEntry.setValue(existing.directEntry()).readOnly(system);
                requiresVehicle.setValue(existing.requiresVehicle());
                active.setValue(existing.active()).readOnly(system);
                if (system) {
                    name.help(messages.get("txCategory.system.help"));
                } else if (used) {
                    direction.help(messages.get("txCategory.flowLocked.help"));
                }
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("txCategory.create") : messages.get("txCategory.edit", existing.name());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public boolean readOnly() {
            return system;
        }

        @Override
        public TransactionCategory toEntity() {
            return new TransactionCategory(existing == null ? null : existing.id(),
                    existing == null ? 0 : existing.version(), existing == null ? null : existing.code(),
                    name.getValue(), direction.getValue(), nature.getValue(), directEntry.getValue(),
                    requiresVehicle.getValue(), existing != null && existing.system(), active.getValue());
        }
    }
}