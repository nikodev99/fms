package org.gu.fms.desktop.features.document;

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
import org.gu.fms.domain.document.DocumentType;

/** Paramètres › Flotte › Types de documents (le suivi des dates arrivera avec le module Documents). */
public final class DocumentTypesScreen extends CrudScreen<DocumentType> {

    @Inject
    public DocumentTypesScreen(CrudContext ui, DocumentTypeService service) {
        super(ui, service);
    }

    @Override
    protected String title() {
        return messages.get("settings.documentTypes");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("documentTypes.count", total);
    }

    @Override
    protected String hint() {
        return messages.get("documentTypes.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("documentType.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.DOCUMENT;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("documentTypes.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("documentTypes.empty.text");
    }

    @Override
    protected void configureTable(DataTable<DocumentType> table) {
        table.textColumn(messages.get("documentType.name"), DocumentType::name, 220);
        table.textColumn(messages.get("documentType.subject"), t -> messages.label(t.subject()), 120);
        table.badgeColumn(messages.get("documentType.mandatory"), t -> t.mandatory()
                ? new Badge(messages.get("common.yes"), Tone.INFO) : new Badge(messages.get("common.no"), Tone.NEUTRAL), 110);
        table.badgeColumn(messages.get("common.state"), t -> t.active()
                ? new Badge(messages.get("common.active"), Tone.SUCCESS)
                : new Badge(messages.get("common.inactive"), Tone.NEUTRAL), 110);
        table.searchBy(t -> t.name() + " " + messages.label(t.subject()));
    }

    @Override
    protected EntityEditor<DocumentType> createEditor(DocumentType existing) {
        return new Editor(ui.forms(), messages, existing);
    }

    @Override
    protected String savedMessage(DocumentType saved, boolean created) {
        return messages.get(created ? "documentType.created" : "documentType.updated", saved.name());
    }

    @Override
    protected Object identityOf(DocumentType t) {
        return t.id();
    }

    private static final class Editor implements EntityEditor<DocumentType> {
        private final Messages messages;
        private final DocumentType existing;
        private final Form form;
        private final TextInput name;
        private final ChoiceInput<DocumentType.DocumentSubject> subject;
        private final CheckInput mandatory;
        private final CheckInput active;

        Editor(Forms forms, Messages messages, DocumentType existing) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            name = form.text("name", messages.get("documentType.name")).required().maxLength(DocumentType.NAME_MAX);
            subject = form.choice("subject", messages.get("documentType.subject"), DocumentType.DocumentSubject.class).required();
            mandatory = form.check("mandatory", messages.get("documentType.mandatory.check"))
                    .help(messages.get("documentType.mandatory.help"));
            active = form.check("active", messages.get("common.activeCheck"));
            if (existing == null) {
                subject.setValue(DocumentType.DocumentSubject.VEHICLE);
                mandatory.setValue(true);
                active.setValue(true);
            } else {
                name.setValue(existing.name());
                subject.setValue(existing.subject());
                mandatory.setValue(existing.mandatory());
                active.setValue(existing.active());
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("documentType.create")
                    : messages.get("documentType.edit", existing.name());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public DocumentType toEntity() {
            return new DocumentType(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    name.getValue(), subject.getValue(), mandatory.getValue(), active.getValue());
        }
    }
}
