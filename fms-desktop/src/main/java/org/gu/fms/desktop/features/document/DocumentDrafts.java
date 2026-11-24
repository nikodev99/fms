package org.gu.fms.desktop.features.document;

import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.form.DateInput;
import org.gu.fms.desktop.platform.ui.form.FilesInput;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.TextInput;
import org.gu.fms.domain.document.DocumentRules;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.FleetDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloc « Documents » d'une fiche en création (véhicule ou chauffeur) : numéro, date d'expiration et fichiers (scan,
 * photo) de chaque type actif, tous facultatifs. Permet de régulariser des documents déjà en main dès la création ;
 * la suite (renouvellement, historique) se gère dans le module Documents.
 */
public final class DocumentDrafts {

    private final DocumentType.DocumentSubject subject;
    private final Map<DocumentType, TextInput> numbers = new LinkedHashMap<>();
    private final Map<DocumentType, DateInput> expiries = new LinkedHashMap<>();
    private final Map<DocumentType, FilesInput> files = new LinkedHashMap<>();

    private DocumentDrafts(DocumentType.DocumentSubject subject) {
        this.subject = subject;
    }

    /** Ajoute la section au formulaire ; rien si aucun type actif n'existe pour ce sujet. */
    public static DocumentDrafts addTo(Form form, Messages messages, DocumentType.DocumentSubject subject, List<DocumentType> types) {
        DocumentDrafts drafts = new DocumentDrafts(subject);
        List<DocumentType> active = types.stream().filter(t -> t.subject() == subject && t.active()).toList();
        if (active.isEmpty()) {
            return drafts;
        }
        form.section(messages.get("documents.drafts.section"));
        int i = 0;
        for (DocumentType t : active) {
            String suffix = t.mandatory() ? " *" : "";
            TextInput number = form.text("doc" + i + "Number", messages.get("documents.drafts.number", t.name() + suffix))
                    .maxLength(DocumentRules.NUMBER_MAX);
            DateInput expiry = form.date("doc" + i + "Expiry", messages.get("documents.drafts.expiry", t.name()));
            FilesInput attached = form.files("doc" + i + "Files", messages.get("documents.drafts.files", t.name()))
                    .span(2);
            if (i == 0) {
                number.help(messages.get("documents.drafts.help"));
            }
            drafts.numbers.put(t, number);
            drafts.expiries.put(t, expiry);
            drafts.files.put(t, attached);
            i++;
        }
        return drafts;
    }

    /** Documents renseignés (numéro, date ou fichier), sans titulaire : rattachés à l'enregistrement de la fiche. */
    public List<DocumentDraft> drafts() {
        List<DocumentDraft> result = new ArrayList<>();
        numbers.forEach((type, number) -> {
            String n = number.getValue();
            var expiry = expiries.get(type).getValue();
            var chosen = files.get(type).getValue();
            if ((n != null && !n.isBlank()) || expiry != null || (chosen != null && !chosen.isEmpty())) {
                result.add(new DocumentDraft(new FleetDocument(null, 0, type.id(), subject, null, null, n, null, null,
                        expiry, null, false, null), chosen));
            }
        });
        return result;
    }
}
