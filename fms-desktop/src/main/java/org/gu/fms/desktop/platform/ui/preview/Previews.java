package org.gu.fms.desktop.platform.ui.preview;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.file.Downloads;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.overlay.OverlayHost;
import org.gu.fms.desktop.platform.ui.overlay.Toast;
import org.gu.fms.desktop.platform.ui.task.UiTasks;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Aperçu plein écran d'un fichier (pièce jointe, rapport) avec « Télécharger », et téléchargement direct sans aperçu.
 * Télécharger = écrire le fichier dans « Téléchargements » puis proposer de l'ouvrir.
 */
@Singleton
public final class Previews {

    private final OverlayHost host;
    private final Messages messages;
    private final UiTasks tasks;
    private final Toast toasts;
    private final Downloads downloads;

    @Inject
    public Previews(OverlayHost host, Messages messages, UiTasks tasks, Toast toasts, Downloads downloads) {
        this.host = host;
        this.messages = messages;
        this.tasks = tasks;
        this.toasts = toasts;
        this.downloads = downloads;
    }

    public Builder preview(String title) {
        return new Builder(title);
    }

    /** Téléchargement direct : {@code content} est produit hors du fil JavaFX (ex. génération d'un rapport). */
    public void download(Callable<Named> content, BooleanProperty busy) {
        var job = tasks.job(() -> {
            Named named = content.call();
            return downloads.save(named.fileName(), named.bytes());
        }).onSuccess(this::downloaded).onFailure(error -> toasts.error(messages.get("download.failed")));
        if (busy != null) {
            job.busy(busy);
        }
        job.start();
    }

    /** Fichier à télécharger : nom proposé et contenu. */
    public record Named(String fileName, byte[] bytes) {
    }

    private void save(String fileName, byte[] bytes) {
        tasks.job(() -> downloads.save(fileName, bytes)).onSuccess(this::downloaded)
                .onFailure(error -> toasts.error(messages.get("download.failed"))).start();
    }

    private void downloaded(Path path) {
        toasts.show(Tone.SUCCESS, messages.get("download.done", path.getFileName(), downloads.directory()),
                messages.get("download.open"), () -> tasks.job(() -> downloads.open(path)).onSuccess(opened -> {
                    if (!opened) {
                        toasts.info(messages.get("download.openUnavailable", path));
                    }
                }).start());
    }

    public final class Builder {
        private final String title;
        private String subtitle;
        private Callable<byte[]> loader;
        private String fileName;

        private Builder(String title) {
            this.title = title;
        }

        public Builder subtitle(String text) {
            this.subtitle = text;
            return this;
        }

        public Builder content(Callable<byte[]> loader) {
            this.loader = loader;
            return this;
        }

        /** Ajoute « Télécharger » ; le fichier garde ce nom. */
        public Builder downloadAs(String name) {
            this.fileName = name;
            return this;
        }

        public void open() {
            Objects.requireNonNull(loader, "content");
            FilePreview preview = new FilePreview(messages, tasks);

            Label t = new Label(title);
            t.getStyleClass().add("drawer-title");
            Label s = new Label(subtitle == null ? "" : subtitle);
            s.getStyleClass().add("drawer-subtitle");
            s.managedProperty().bind(s.textProperty().isNotEmpty());
            s.visibleProperty().bind(s.managedProperty());
            Button close = new Button();
            close.setGraphic(new FmsIcon(AppIcon.CLOSE, 18));
            close.getStyleClass().add("icon-button");
            close.setTooltip(new Tooltip(messages.get("action.close")));
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            HBox header = new HBox(12, new VBox(2, t, s), grow, close);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("drawer-header");

            Button closeFooter = new Button(messages.get("action.close"));
            closeFooter.getStyleClass().add("secondary");
            HBox footer = new HBox(8, closeFooter);
            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.getStyleClass().add("drawer-footer");
            if (fileName != null) {
                Button download = new Button(messages.get("download.action"), new FmsIcon(AppIcon.DOWNLOAD, 16));
                download.getStyleClass().add("primary");
                download.setDefaultButton(true);
                download.disableProperty().bind(preview.contentProperty().isNull());
                download.setOnAction(e -> save(fileName, preview.contentProperty().get()));
                footer.getChildren().add(download);
            }

            BorderPane panel = new BorderPane(preview, header, null, footer, null);
            panel.getStyleClass().add("preview-dialog");
            Region scrim = new Region();
            scrim.getStyleClass().add("scrim");
            StackPane layer = new StackPane(scrim, panel);
            layer.getStyleClass().addAll("overlay-layer", "preview-layer");
            panel.maxWidthProperty().bind(layer.widthProperty().multiply(0.92));
            panel.maxHeightProperty().bind(layer.heightProperty().multiply(0.94));
            Runnable dismiss = () -> host.remove(layer);
            close.setOnAction(e -> dismiss.run());
            closeFooter.setOnAction(e -> dismiss.run());
            scrim.setOnMouseClicked(e -> dismiss.run());
            layer.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    dismiss.run();
                }
            });
            host.push(layer);
            layer.requestFocus();
            preview.show(loader);
        }
    }
}
