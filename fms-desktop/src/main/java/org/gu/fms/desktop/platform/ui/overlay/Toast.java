package org.gu.fms.desktop.platform.ui.overlay;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

/**
 * Notifications discrètes en bas à droite, qui disparaissent seules.
 * Pour confirmer une action réussie (« Véhicule enregistré »), jamais pour une erreur qui exige une décision.
 */
@Singleton
public final class Toast {
    private static final Duration SHORT = Duration.seconds(4);
    private static final Duration LONG = Duration.seconds(7);
    private static final int MAX_VISIBLE = 4;

    private final OverlayHost host;

    @Inject
    public Toast(OverlayHost host) {
        this.host = host;
    }

    public void success(String message) {
        show(Tone.SUCCESS, message);
    }

    public void info(String message) {
        show(Tone.INFO, message);
    }

    public void warning(String message) {
        show(Tone.WARNING, message);
    }

    public void error(String message) {
        show(Tone.DANGER, message);
    }

    private void show(Tone tone, String message) {
        FmsIcon icon = new FmsIcon(Dialogs.iconFor(tone), 18);

        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(340);
        text.getStyleClass().add("toast-text");

        HBox toast = new HBox(10, icon, text);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", tone.styleClass());
        toast.setOpacity(0);

        var layer = host.toastLayer().getChildren();
        if (layer.size() >= MAX_VISIBLE) {
            layer.remove(0);
        }
        layer.add(toast);

        FadeTransition in = new FadeTransition(Duration.millis(160), toast);
        in.setToValue(1);
        PauseTransition stay = new PauseTransition(tone == Tone.DANGER || tone == Tone.WARNING ? LONG : SHORT);
        FadeTransition out = new FadeTransition(Duration.millis(220), toast);
        out.setToValue(0);
        SequentialTransition life = new SequentialTransition(in, stay, out);
        life.setOnFinished(e -> layer.remove(toast));
        toast.setOnMouseClicked(e -> {
            life.stop();
            layer.remove(toast);
        });
        life.play();
    }
}
