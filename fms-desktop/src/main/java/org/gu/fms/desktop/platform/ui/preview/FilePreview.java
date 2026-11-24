package org.gu.fms.desktop.platform.ui.preview;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.file.FileKind;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.task.UiTasks;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Aperçu d'un fichier PDF ou d'une image : pages les unes sous les autres, ajustées à la largeur par défaut (sans
 * agrandissement), zoom,
 * rotation (images, ex. photo prise de côté). Le contenu est lu et rendu hors du fil JavaFX.
 */
public final class FilePreview extends BorderPane {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 3;
    private static final double PADDING = 48;

    private final Messages messages;
    private final UiTasks tasks;
    private final VBox pages = new VBox(16);
    private final ScrollPane scroll = new ScrollPane();
    private final Label status = new Label();
    private final Label zoomLabel = new Label();
    private final Button rotate = new Button();
    private final SimpleBooleanProperty loading = new SimpleBooleanProperty();
    private final ObjectProperty<byte[]> content = new SimpleObjectProperty<>(this, "content");
    private final List<ImageView> views = new ArrayList<>();
    private boolean fitWidth = true;
    private double zoom = 1;
    private double rotation;
    private int generation;

    public FilePreview(Messages messages, UiTasks tasks) {
        this.messages = messages;
        this.tasks = tasks;
        getStyleClass().add("file-preview");

        Button zoomOut = iconButton(AppIcon.ZOOM_OUT, messages.get("preview.zoomOut"));
        zoomOut.setOnAction(e -> setZoom(currentZoom() / 1.25));
        Button zoomIn = iconButton(AppIcon.ZOOM_IN, messages.get("preview.zoomIn"));
        zoomIn.setOnAction(e -> setZoom(currentZoom() * 1.25));
        Button fit = iconButton(AppIcon.FIT_WIDTH, messages.get("preview.fitWidth"));
        fit.setOnAction(e -> {
            fitWidth = true;
            resize();
        });
        rotate.setGraphic(new FmsIcon(AppIcon.ROTATE, 18));
        rotate.getStyleClass().add("icon-button");
        rotate.setTooltip(new Tooltip(messages.get("preview.rotate")));
        rotate.setOnAction(e -> {
            rotation = (rotation + 90) % 360;
            resize();
        });
        rotate.setVisible(false);
        rotate.managedProperty().bind(rotate.visibleProperty());
        zoomLabel.getStyleClass().add("preview-zoom");
        zoomLabel.setMinWidth(48);
        zoomLabel.setAlignment(Pos.CENTER);
        status.getStyleClass().add("preview-status");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox toolbar = new HBox(4, zoomOut, zoomLabel, zoomIn, fit, rotate, grow, status);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("preview-toolbar");

        pages.setAlignment(Pos.TOP_CENTER);
        pages.getStyleClass().add("preview-pages");
        scroll.setContent(pages);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("preview-scroll");
        scroll.viewportBoundsProperty().addListener((obs, o, b) -> {
            if (fitWidth) {
                resize();
            }
        });
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.visibleProperty().bind(loading);
        spinner.setMaxSize(48, 48);
        StackPane center = new StackPane(scroll, spinner);
        setTop(toolbar);
        setCenter(center);
        updateZoomLabel();
    }

    /** Contenu affiché (null tant qu'il n'est pas chargé) : pour le télécharger sans le relire. */
    public ReadOnlyObjectProperty<byte[]> contentProperty() {
        return content;
    }

    /** Lit le fichier ({@code loader} hors du fil JavaFX) puis l'affiche. */
    public void show(Callable<byte[]> loader) {
        int run = ++generation;
        views.clear();
        pages.getChildren().clear();
        content.set(null);
        rotation = 0;
        fitWidth = true;
        status.setText("");
        tasks.job(() -> load(loader)).busy(loading).onSuccess(loaded -> {
            if (run != generation) {
                return;
            }
            content.set(loaded.bytes());
            rotate.setVisible(loaded.image());
            for (Image image : loaded.images()) {
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true);
                view.setSmooth(true);
                views.add(view);
                StackPane page = new StackPane(new Group(view));
                page.getStyleClass().add("preview-page");
                page.setMaxWidth(Region.USE_PREF_SIZE);
                pages.getChildren().add(page);
            }
            status.setText(loaded.image() ? messages.get("preview.image")
                    : loaded.total() > loaded.images().size()
                    ? messages.get("preview.pagesTruncated", loaded.images().size(), loaded.total())
                    : messages.get("preview.pages", loaded.total()));
            resize();
        }).onFailure(error -> {
            if (run == generation) {
                showMessage(messages.get("preview.failed"));
            }
        }).start();
    }

    /** Message à la place du contenu (fichier illisible, aucun fichier…). */
    public void showMessage(String text) {
        views.clear();
        Label label = new Label(text);
        label.getStyleClass().add("empty-text");
        label.setWrapText(true);
        pages.getChildren().setAll(label);
        status.setText("");
    }

    private record Loaded(byte[] bytes, List<Image> images, int total, boolean image) {
    }

    private static Loaded load(Callable<byte[]> loader) throws Exception {
        byte[] bytes = loader.call();
        FileKind kind = FileKind.detect(bytes).orElseThrow(() -> new IllegalArgumentException("Format inconnu"));
        if (kind.isImage()) {
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) {
                throw new IllegalArgumentException("Image illisible", image.getException());
            }
            return new Loaded(bytes, List.of(image), 1, true);
        }
        PdfPages.Rendered rendered = PdfPages.render(bytes, PdfPages.DPI, PdfPages.MAX_PAGES);
        List<Image> images = rendered.pages().stream().map(PdfPages::toFx).toList();
        return new Loaded(bytes, images, rendered.total(), false);
    }

    private double currentZoom() {
        if (!fitWidth || views.isEmpty()) {
            return zoom;
        }
        ImageView first = views.get(0);
        double natural = quarter() ? first.getImage().getHeight() : first.getImage().getWidth();
        // Ajusté à la largeur, sans agrandir au-delà de la taille de rendu (texte net)
        return natural <= 0 ? 1 : Math.min(1, available() / natural);
    }

    private void setZoom(double value) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
        fitWidth = false;
        resize();
    }

    private boolean quarter() {
        return rotation == 90 || rotation == 270;
    }

    private double available() {
        return Math.max(200, scroll.getViewportBounds().getWidth() - PADDING);
    }

    /** Applique zoom et rotation : la largeur affichée (après rotation) vaut la largeur disponible ou le zoom. */
    private void resize() {
        double z = currentZoom();
        for (ImageView view : views) {
            Image image = view.getImage();
            double shownWidth = (quarter() ? image.getHeight() : image.getWidth()) * z;
            view.setRotate(rotation);
            if (quarter()) {
                view.setFitWidth(0);
                view.setFitHeight(shownWidth);
            } else {
                view.setFitHeight(0);
                view.setFitWidth(shownWidth);
            }
        }
        updateZoomLabel();
    }

    private void updateZoomLabel() {
        zoomLabel.setText(Math.round(currentZoom() * 100) + " %");
    }

    private static Button iconButton(AppIcon icon, String tooltip) {
        Button b = new Button();
        b.setGraphic(new FmsIcon(icon, 18));
        b.getStyleClass().add("icon-button");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }
}
