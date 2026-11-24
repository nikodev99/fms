package org.gu.fms.desktop.features.alert;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.overlay.Drawer;
import org.gu.fms.desktop.platform.ui.overlay.Drawers;

/** Liste des alertes (cloche de la barre supérieure) : chacune ouvre le module où la régler. */
@Singleton
public final class AlertsPanel {

    private final AlertCenter center;
    private final Drawers drawers;
    private final Navigator navigator;
    private final Messages messages;

    @Inject
    public AlertsPanel(AlertCenter center, Drawers drawers, Navigator navigator, Messages messages) {
        this.center = center;
        this.drawers = drawers;
        this.navigator = navigator;
        this.messages = messages;
    }

    public void open() {
        Drawer drawer = drawers.create(messages.get("alerts.title"));
        drawer.subtitle(messages.get("alerts.subtitle", center.totalCountProperty().get(),
                center.criticalCountProperty().get()));
        VBox list = new VBox(8);
        list.getStyleClass().add("alerts-list");
        if (center.alerts().isEmpty()) {
            Label none = new Label(messages.get("alerts.none"));
            none.getStyleClass().add("empty-text");
            list.getChildren().add(none);
        }
        for (AppAlert a : center.alerts()) {
            Tone tone = switch (a.severity()) {
                case CRITICAL -> Tone.DANGER;
                case WARNING -> Tone.WARNING;
                case INFO -> Tone.INFO;
            };
            FmsIcon icon = new FmsIcon(a.critical() ? AppIcon.ERROR : AppIcon.WARNING, 18);
            icon.getStyleClass().addAll("alert-icon", tone.styleClass());
            Label t = new Label(a.title());
            t.getStyleClass().add("alert-row-title");
            t.setWrapText(true);
            Label d = new Label(a.detail());
            d.getStyleClass().add("alert-row-detail");
            d.setWrapText(true);
            Button open = new Button(messages.get("alert.open"));
            open.getStyleClass().add("secondary");
            open.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            open.setOnAction(e -> drawer.requestClose().thenAccept(closed -> {
                if (closed) {
                    navigator.navigate(a.route());
                }
            }));
            VBox texts = new VBox(2, t, d);
            HBox.setHgrow(texts, Priority.ALWAYS);
            HBox row = new HBox(10, icon, texts, open);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("alert-row", tone.styleClass());
            list.getChildren().add(row);
        }
        drawer.content(list);
        drawer.open();
    }
}
