package org.gu.fms.desktop.features.maintenance;

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
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.MaintenanceType;
import org.gu.fms.domain.maintenance.MaintenanceTypeRules;

/** Paramètres › Flotte › Types d'entretien. */
public final class MaintenanceTypesScreen extends CrudScreen<MaintenanceType> {

    @Inject
    public MaintenanceTypesScreen(CrudContext ui, MaintenanceTypeService service) {
        super(ui, service);
    }

    @Override
    protected String title() {
        return messages.get("settings.maintenanceTypes");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("maintenanceTypes.count", total);
    }

    @Override
    protected String hint() {
        return messages.get("maintenanceTypes.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("maintenanceType.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.MAINTENANCE;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("maintenanceTypes.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("maintenanceTypes.empty.text");
    }

    @Override
    protected void configureTable(DataTable<MaintenanceType> table) {
        table.textColumn(messages.get("maintenanceType.name"), MaintenanceType::name, 200);
        table.badgeColumn(messages.get("maintenanceType.kind"), t -> new Badge(messages.label(t.kind()),
                t.kind() == MaintenanceEvent.MaintenanceKind.REPAIR ? Tone.WARNING : Tone.INFO), 130);
        table.numberColumn(messages.get("maintenanceType.intervalDays"), MaintenanceType::defaultIntervalDays,
                messages.get("unit.days"), 120);
        table.numberColumn(messages.get("maintenanceType.intervalKm"), MaintenanceType::defaultIntervalKm,
                messages.get("unit.km"), 120);
        table.badgeColumn(messages.get("common.state"), t -> t.active()
                ? new Badge(messages.get("common.active"), Tone.SUCCESS)
                : new Badge(messages.get("common.inactive"), Tone.NEUTRAL), 100);
        table.searchBy(t -> t.name() + " " + messages.label(t.kind()));
    }

    @Override
    protected EntityEditor<MaintenanceType> createEditor(MaintenanceType existing) {
        return new Editor(ui.forms(), messages, existing);
    }

    @Override
    protected String savedMessage(MaintenanceType saved, boolean created) {
        return messages.get(created ? "maintenanceType.created" : "maintenanceType.updated", saved.name());
    }

    @Override
    protected Object identityOf(MaintenanceType t) {
        return t.id();
    }

    private static final class Editor implements EntityEditor<MaintenanceType> {
        private final Messages messages;
        private final MaintenanceType existing;
        private final Form form;
        private final TextInput name;
        private final ChoiceInput<MaintenanceEvent.MaintenanceKind> kind;
        private final NumberInput days;
        private final NumberInput km;
        private final CheckInput active;

        Editor(Forms forms, Messages messages, MaintenanceType existing) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            name = form.text("name", messages.get("maintenanceType.name")).required()
                    .maxLength(MaintenanceTypeRules.NAME_MAX);
            kind = form.choice("kind", messages.get("maintenanceType.kind"), MaintenanceEvent.MaintenanceKind.class).required()
                    .help(messages.get("maintenanceType.kind.help"));
            form.section(messages.get("maintenanceType.section.interval"));
            days = form.number("defaultIntervalDays", messages.get("maintenanceType.intervalDays"))
                    .unit(messages.get("unit.days")).rule(Rules.positive());
            km = form.number("defaultIntervalKm", messages.get("maintenanceType.intervalKm"))
                    .unit(messages.get("unit.km")).rule(Rules.positive())
                    .help(messages.get("maintenanceType.interval.help"));
            active = form.check("active", messages.get("common.activeCheck"));
            if (existing == null) {
                kind.setValue(MaintenanceEvent.MaintenanceKind.SERVICE);
                active.setValue(true);
            } else {
                name.setValue(existing.name());
                kind.setValue(existing.kind());
                days.setValue(existing.defaultIntervalDays());
                km.setValue(existing.defaultIntervalKm());
                active.setValue(existing.active());
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("maintenanceType.create")
                    : messages.get("maintenanceType.edit", existing.name());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public MaintenanceType toEntity() {
            return new MaintenanceType(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    name.getValue(), kind.getValue(), days.getValue(), km.getValue(), active.getValue());
        }
    }
}
