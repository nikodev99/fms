package org.gu.fms.desktop.platform.ui.overlay;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

import java.util.concurrent.CompletableFuture;

/**
 * Dialogues modaux intégrés à la fenêtre (pas de fenêtre système) : même style dans les deux thèmes.
 * Toutes les méthodes s'appellent sur le fil JavaFX.
 */
@Singleton
public final class Dialogs {
    private final OverlayHost host;
    private final Messages messages;

    @Inject
    public Dialogs(OverlayHost host, Messages messages) {
        this.host = host;
        this.messages = messages;
    }
    
    public CompletableFuture<Boolean> confirm(String title, String message, String confirmLabel, Tone tone) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        
        Button cancel = new Button(messages.get("action.cancel"));
        cancel.getStyleClass().add("secondary");
        
        Button confirm = new Button(confirmLabel);
        confirm.getStyleClass().add(tone == Tone.DANGER ? "danger": "primary");
        if (tone == Tone.DANGER) {
            cancel.setDefaultButton(true);
        }else {
            confirm.setDefaultButton(false);
        }

        StackPane layer = show(tone, title, message, cancel, confirm);
        cancel.setOnAction(e -> close(layer, result, false));
        confirm.setOnAction(e -> close(layer, result, true));
        layer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                close(layer, result, false);
            }
        });
        (tone == Tone.DANGER ? cancel : confirm).requestFocus();
        return result;
    }

    /** Message à lire, avec un seul bouton. */
    public CompletableFuture<Boolean> alert(Tone tone, String title, String message) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Button ok = new Button(messages.get("action.ok"));
        ok.getStyleClass().add("primary");
        ok.setDefaultButton(true);
        StackPane layer = show(tone, title, message, ok);
        ok.setOnAction(e -> close(layer, result, true));
        layer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                close(layer, result, true);
            }
        });
        ok.requestFocus();
        return result;
    }

    public CompletableFuture<Boolean> error(String title, String message) {
        return alert(Tone.DANGER, title, message);
    }

    private StackPane show(Tone tone, String title, String message, Button... buttons) {
        FmsIcon icon = new FmsIcon(iconFor(tone), 22);
        icon.getStyleClass().add("dialog-icon");

        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().addAll("dialog-icon-box", tone.styleClass());

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");
        titleLabel.setWrapText(true);

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("dialog-message");
        messageLabel.setWrapText(true);

        VBox texts = new VBox(6, titleLabel, messageLabel);
        HBox top = new HBox(14, iconBox, texts);
        top.setAlignment(Pos.TOP_LEFT);

        HBox actions = new HBox(8, buttons);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("dialog-actions");

        VBox card = new VBox(20, top, actions);
        card.getStyleClass().add("dialog");
        card.setMaxWidth(460);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        Region scrim = new Region();
        scrim.getStyleClass().add("scrim");

        StackPane layer = new StackPane(scrim, card);
        layer.getStyleClass().addAll("overlay-layer", "dialog-layer");
        host.push(layer);

        card.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(140), card);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(140), card);
        scale.setFromX(0.97);
        scale.setFromY(0.97);
        scale.setToX(1);
        scale.setToY(1);
        fade.play();
        scale.play();

        return layer;
    }

    private void close(StackPane layer, CompletableFuture<Boolean> result, boolean value) {
        host.remove(layer);
        result.complete(value);
    }

    static AppIcon iconFor(Tone tone) {
        return switch (tone) {
            case SUCCESS -> AppIcon.SUCCESS;
            case DANGER -> AppIcon.ERROR;
            case WARNING -> AppIcon.WARNING;
            case INFO, NEUTRAL -> AppIcon.INFO;
        };
    }
}
