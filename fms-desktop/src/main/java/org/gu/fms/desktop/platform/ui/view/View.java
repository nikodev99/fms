package org.gu.fms.desktop.platform.ui.view;

import javafx.scene.Parent;

public record View<C>(Parent root, C controller) {
}
