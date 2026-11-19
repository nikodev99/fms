package org.gu.fms.desktop.platform.ui.table;

import javafx.scene.control.TableView;
import javafx.util.Callback;

/** Politique de largeur des colonnes, centralisée (l'API a évolué entre les versions de JavaFX). */
public class ResizePolicies {
    public ResizePolicies() {
    }

    @SuppressWarnings("rawtypes")
    public static Callback<TableView.ResizeFeatures, Boolean> fill() {
        return TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN;
    }
}
