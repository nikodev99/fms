package org.gu.fms.desktop.platform.ui.form;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * Fichiers du poste à joindre (PDF, PNG, JPEG) : choix multiple, liste des fichiers retenus, retrait avant
 * enregistrement. Le contenu n'est lu qu'à l'enregistrement.
 */
public final class FilesInput extends FormField<List<Path>, FilesInput> {

    private final Button choose;
    private final VBox chosen = new VBox(4);
    private final VBox box;

    FilesInput(String name, String label, Messages messages) {
        super(name, label, messages);
        choose = new Button(messages.get("files.choose"), new FmsIcon(AppIcon.ATTACHMENT, 16));
        choose.getStyleClass().add("secondary");
        choose.setOnAction(e -> pick());
        chosen.getStyleClass().add("files-chosen");
        box = new VBox(6, choose, chosen);
        setValue(List.of());
        markClean();
        valueProperty().addListener((obs, o, v) -> refresh());
    }

    @Override
    protected boolean isEmpty(List<Path> v) {
        return v == null || v.isEmpty();
    }

    @Override
    protected Region buildEditor() {
        return box;
    }

    @Override
    protected Node focusTarget() {
        return choose;
    }

    private void pick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(label());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("files.filter"),
                "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.PDF", "*.PNG", "*.JPG", "*.JPEG"));
        List<File> files = chooser.showOpenMultipleDialog(choose.getScene() == null ? null : choose.getScene().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Path> next = new ArrayList<>(getValue() == null ? List.of() : getValue());
        for (File f : files) {
            Path p = f.toPath();
            if (!next.contains(p)) {
                next.add(p);
            }
        }
        setValue(List.copyOf(next));
    }

    private void refresh() {
        chosen.getChildren().clear();
        for (Path p : getValue() == null ? List.<Path>of() : getValue()) {
            Label name = new Label(p.getFileName().toString());
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            long size = p.toFile().length();
            Label weight = new Label(sizeLabel(size));
            boolean pdf = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
            long limit = pdf ? FileStore.MAX_BYTES : FileStore.MAX_PHOTO_BYTES;
            weight.getStyleClass().add(size > limit ? "files-too-large" : "files-size");
            Button remove = new Button();
            remove.setGraphic(new FmsIcon(AppIcon.CLOSE, 14));
            remove.getStyleClass().add("icon-button");
            remove.setTooltip(new Tooltip(messages.get("files.remove")));
            remove.setOnAction(e -> {
                List<Path> next = new ArrayList<>(getValue());
                next.remove(p);
                setValue(List.copyOf(next));
            });
            HBox row = new HBox(8, new FmsIcon(AppIcon.ATTACHMENT, 14), name, weight, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("files-row");
            chosen.getChildren().add(row);
        }
    }

    /** « 820 Ko », « 2,4 Mo ». */
    public static String sizeLabel(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " Ko";
        }
        return String.format(java.util.Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0));
    }
}
