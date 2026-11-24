package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.file.StoredFile;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.form.FilesInput;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.preview.Previews;
import org.gu.fms.domain.document.DocumentStatus;
import org.gu.fms.domain.document.DocumentType;

import java.util.Comparator;
import java.util.List;

/**
 * Affichage des documents et de leurs pièces jointes : liste des fichiers d'un document (aperçu, retrait) et liste
 * des documents d'un véhicule (les siens et ceux de ses chauffeurs), avec aperçu à la demande.
 */
@Singleton
public final class DocumentFilesView {

    private final DocumentService documents;
    private final Previews previews;
    private final Messages messages;
    private final Formats formats;

    @Inject
    public DocumentFilesView(DocumentService documents, Previews previews, Messages messages, Formats formats) {
        this.documents = documents;
        this.previews = previews;
        this.messages = messages;
        this.formats = formats;
    }

    /** Aperçu d'une pièce jointe, avec « Télécharger ». */
    public void preview(String title, StoredFile file) {
        previews.preview(title)
                .subtitle(file.fileName() + " · " + FilesInput.sizeLabel(file.sizeBytes()))
                .content(() -> documents.content(file))
                .downloadAs(file.fileName())
                .open();
    }

    /** Retrait d'une pièce jointe : {@code removeLine} enlève sa ligne de la liste une fois le retrait fait. */
    public interface RemoveHandler {
        void remove(StoredFile file, Runnable removeLine);
    }

    /**
     * Pièces jointes d'un document : une ligne par fichier (« Aperçu », et « Retirer » si {@code onRemove} n'est pas
     * nul). Vide : un texte l'indique.
     */
    public Node attachments(String title, List<StoredFile> files, RemoveHandler onRemove) {
        VBox box = new VBox(6);
        box.getStyleClass().add("attachments");
        Label heading = new Label(messages.get("document.files"));
        heading.getStyleClass().add("field-label");
        box.getChildren().add(heading);
        if (files.isEmpty()) {
            Label none = new Label(messages.get("document.files.none"));
            none.getStyleClass().add("muted-text");
            box.getChildren().add(none);
            return box;
        }
        for (StoredFile f : files) {
            Label name = new Label(f.fileName());
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Label size = new Label(FilesInput.sizeLabel(f.sizeBytes()));
            size.getStyleClass().add("files-size");
            Button open = new Button(messages.get("preview.open"), new FmsIcon(AppIcon.PREVIEW, 16));
            open.getStyleClass().add("secondary");
            open.setOnAction(e -> preview(title, f));
            HBox row = new HBox(8, new FmsIcon(f.kind().isImage() ? AppIcon.IMAGE : AppIcon.PDF, 16), name, size, open);
            if (onRemove != null) {
                Button remove = new Button();
                remove.setGraphic(new FmsIcon(AppIcon.REMOVE, 16));
                remove.getStyleClass().add("icon-button");
                remove.setTooltip(new Tooltip(messages.get("document.files.remove")));
                row.getChildren().add(remove);
                remove.setOnAction(e -> onRemove.remove(f, () -> box.getChildren().remove(row)));
            }
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("files-row");
            box.getChildren().add(row);
        }
        return box;
    }

    /**
     * Documents d'un véhicule (fiche véhicule) : type, titulaire (le véhicule ou le chauffeur), échéance, état et
     * fichiers, les plus urgents d'abord.
     */
    public Node vehicleDocuments(List<ComplianceRow> rows) {
        VBox box = new VBox(8);
        box.getStyleClass().add("vehicle-documents");
        Label heading = new Label(messages.get("vehicle.documents"));
        heading.getStyleClass().add("panel-title");
        box.getChildren().add(heading);
        if (rows.isEmpty()) {
            Label none = new Label(messages.get("vehicle.documents.none"));
            none.getStyleClass().add("muted-text");
            box.getChildren().add(none);
            return box;
        }
        List<ComplianceRow> sorted = rows.stream().sorted(Comparator.comparing(ComplianceRow::status)
                .thenComparing(ComplianceRow::subject).thenComparing(r -> r.type().name())).toList();
        for (ComplianceRow r : sorted) {
            Label type = new Label(r.type().name());
            type.getStyleClass().add("vehicle-document-type");
            String holder = r.subject() == DocumentType.DocumentSubject.DRIVER ? r.ownerLabel() : messages.get("vehicle.documents.vehicle");
            String expiry = r.document() == null ? messages.get("document.status.missingShort")
                    : r.document().expiresOn() == null ? messages.get("document.noExpiry")
                    : messages.get("vehicle.documents.until", formats.date(r.document().expiresOn()));
            Label meta = new Label(holder + " · " + expiry);
            meta.getStyleClass().add("muted-text");
            VBox texts = new VBox(2, type, meta);
            HBox.setHgrow(texts, Priority.ALWAYS);
            Label badge = new Label(messages.label(r.status()));
            badge.getStyleClass().setAll("label", "chip", tone(r.status()).styleClass());
            badge.setMinWidth(Region.USE_PREF_SIZE);
            HBox files = new HBox(4);
            files.setAlignment(Pos.CENTER_RIGHT);
            files.setMinWidth(Region.USE_PREF_SIZE);
            for (StoredFile f : r.files()) {
                Button open = new Button(null, new FmsIcon(f.kind().isImage() ? AppIcon.IMAGE : AppIcon.PDF, 16));
                open.getStyleClass().add("icon-button");
                open.setTooltip(new Tooltip(messages.get("preview.openFile", f.fileName())));
                open.setOnAction(e -> preview(r.type().name() + " — " + r.ownerLabel(), f));
                files.getChildren().add(open);
            }
            HBox line = new HBox(10, texts, files, badge);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getStyleClass().add("vehicle-document");
            box.getChildren().add(line);
        }
        return box;
    }

    static Tone tone(DocumentStatus s) {
        return switch (s) {
            case EXPIRED -> Tone.DANGER;
            case MISSING, EXPIRING -> Tone.WARNING;
            case VALID -> Tone.SUCCESS;
        };
    }
}
