package org.gu.fms.desktop.platform.ui.overlay;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Panneau latéral droit pour créer ou modifier un enregistrement sans quitter la liste.
 *
 * <pre>{@code
 * Drawer d = drawers.create("Nouveau véhicule")
 *         .content(form.view())
 *         .actions(cancelButton, saveButton)
 *         .beforeClose(() -> form.isDirty() ? dialogs.confirm(...) : completed(true));
 * d.open();
 * }</pre>
 *
 * Fermeture par Échap, par la croix ou par un clic à côté : passe par {@link #beforeClose(Supplier)}.
 */
public final class Drawer {
    private static final Duration ANIMATION = Duration.millis(180);
    private static final double MIN_WIDTH = 440;
    private static final double MAX_WIDTH = 620;
    private static final double SHARE = 0.42;

    private final OverlayHost host;
    private final StackPane layer = new StackPane();
    private final Region scrim = new Region();
    private final BorderPane panel = new BorderPane();
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final ScrollPane body = new ScrollPane();
    private final HBox footer = new HBox(8);
    private final StackPane busyCover = new StackPane(new ProgressIndicator());
    private final BooleanProperty busy = new SimpleBooleanProperty(this, "busy");
    private Supplier<CompletableFuture<Boolean>> beforeClose = () -> CompletableFuture.completedFuture(true);
    private boolean open;

    Drawer(OverlayHost host, String titleText, String closeLabel) {
        this.host = host;
        layer.getStyleClass().addAll("overlay-layer", "drawer-layer");
        scrim.getStyleClass().add("scrim");
        panel.getStyleClass().add("drawer");

        title.setText(titleText);
        title.getStyleClass().add("drawer-title");
        subtitle.getStyleClass().add("drawer-subtitle");
        subtitle.managedProperty().bind(subtitle.textProperty().isNotEmpty());
        subtitle.visibleProperty().bind(subtitle.managedProperty());

        Button close = new Button();
        close.setGraphic(new FmsIcon(AppIcon.CLOSE, 18));
        close.getStyleClass().add("icon-button");
        close.setTooltip(new Tooltip(closeLabel));
        close.setOnAction(e -> requestClose());

        VBox titles = new VBox(2, title, subtitle);
        HBox header = new HBox(12, titles, spacer(), close);
        header.getStyleClass().add("drawer-header");
        header.setAlignment(Pos.CENTER_LEFT);

        body.getStyleClass().add("drawer-body");
        body.setFitToWidth(true);
        body.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        footer.getStyleClass().add("drawer-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        busyCover.getStyleClass().add("busy-cover");
        busyCover.visibleProperty().bind(busy);

        panel.setTop(header);
        panel.setCenter(new StackPane(body, busyCover));
        panel.setBottom(footer);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        layer.widthProperty().addListener((obs, o, w) ->
                panel.setPrefWidth(Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, w.doubleValue() * SHARE))));
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);

        layer.getChildren().addAll(scrim, panel);
        scrim.setOnMouseClicked(e -> requestClose());
        layer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                requestClose();
            }
        });
    }

    public Drawer subtitle(String text) {
        subtitle.setText(text == null ? "" : text);
        return this;
    }

    public Drawer content(Node node) {
        body.setContent(node);
        return this;
    }

    /** Boutons du pied, alignés à droite ; le dernier est l'action principale. */
    public Drawer actions(Node... nodes) {
        footer.getChildren().setAll(nodes);
        return this;
    }

    /** Appelé avant toute fermeture demandée par l'utilisateur ; renvoyer faux pour garder le tiroir ouvert. */
    public Drawer beforeClose(Supplier<CompletableFuture<Boolean>> check) {
        this.beforeClose = check;
        return this;
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if (open) {
            return;
        }
        open = true;
        host.push(layer);
        layer.applyCss();
        layer.layout();
        panel.setTranslateX(panel.getPrefWidth() > 0 ? panel.getPrefWidth() : MIN_WIDTH);
        scrim.setOpacity(0);
        TranslateTransition slide = new TranslateTransition(ANIMATION, panel);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(ANIMATION, scrim);
        fade.setToValue(1);
        new ParallelTransition(slide, fade).play();
        layer.requestFocus();
    }

    /** Fermeture demandée par l'utilisateur : passe par {@link #beforeClose(Supplier)}. */
    public CompletableFuture<Boolean> requestClose() {
        if (!open || busy.get()) {
            return CompletableFuture.completedFuture(false);
        }
        return beforeClose.get().thenApply(ok -> {
            if (ok) {
                close();
            }
            return ok;
        });
    }

    /** Fermeture immédiate, sans confirmation (ex. après un enregistrement réussi). */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        TranslateTransition slide = new TranslateTransition(ANIMATION, panel);
        slide.setToX(panel.getWidth());
        slide.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fade = new FadeTransition(ANIMATION, scrim);
        fade.setToValue(0);
        ParallelTransition out = new ParallelTransition(slide, fade);
        out.setOnFinished(e -> host.remove(layer));
        out.play();
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
