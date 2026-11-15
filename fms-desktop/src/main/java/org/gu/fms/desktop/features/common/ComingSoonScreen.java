package org.gu.fms.desktop.features.common;

import jakarta.inject.Inject;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.nav.Screen;

import java.awt.*;

/** Module visible dans le menu, mais pas encore construit : le dit clairement, sans fausses données. */
public final class ComingSoonScreen implements Screen, Screen.RouteAware {
    private final Messages messages;
    private Route route;
    private Node view;

    @Inject
    public ComingSoonScreen(Messages messages) {
        this.messages = messages;
    }

    @Override
    public Node view() {
        if (view == null) {
            Label title = new Label(messages.get(route.titleKey()));
            title.getStyleClass().add("page-title");

            FmsIcon icon = new FmsIcon(AppIcon.UNDER_CONSTRUCTION, 30);
            icon.getStyleClass().add("empty-icon");
            Label heading = new Label(messages.get("comingSoon.title"));
            heading.getStyleClass().add("empty-title");
            Label text = new Label(messages.get("comingSoon.text"));
            text.getStyleClass().add("empty-text");
            text.setWrapText(true);
            text.setMaxWidth(440);

            VBox empty = new VBox(8, icon, heading, text);
            empty.getStyleClass().add("empty-state");
            empty.setAlignment(Pos.CENTER);
            VBox panel = new VBox(empty);
            panel.getStyleClass().add("panel");
            panel.setAlignment(Pos.CENTER);
            VBox.setVgrow(empty, Priority.ALWAYS);
            VBox.setVgrow(panel, Priority.ALWAYS);

            VBox page = new VBox(20, title, panel);
            page.getStyleClass().addAll("page", "content");
            view = page;
        }
        return view;
    }

    @Override
    public void setRoute(Route route) {
        this.route = route;
    }
}
