package org.gu.fms.desktop.platform.ui.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.ui.Tone;

/**
 * Liste « libellé — valeur » en lecture seule (détail d'une transaction, calcul d'un bulletin, chiffres d'une
 * clôture). Une ligne de total se met en évidence avec {@link #total(String, String)}.
 */
public final class DetailList extends VBox {

    private final VBox rows = new VBox();
    private HBox last;

    public DetailList() {
        getStyleClass().addAll("panel", "detail-list");
        rows.getStyleClass().add("panel-body");
        getChildren().add(rows);
        setMinHeight(Region.USE_PREF_SIZE);
    }

    public DetailList(String title) {
        this();
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        HBox header = new HBox(t);
        header.getStyleClass().add("panel-header");
        getChildren().add(0, header);
    }

    public DetailList add(String label, String value) {
        return add(label, value, null);
    }

    /** Ligne ignorée si la valeur est vide. */
    public DetailList add(String label, String value, Tone tone) {
        if (value == null || value.isBlank()) {
            return this;
        }
        if (last != null) {
            last.getStyleClass().remove("last");
        }
        Label name = new Label(label);
        name.getStyleClass().add("balance-name");
        name.setMinWidth(Region.USE_PREF_SIZE);
        Label v = new Label(value);
        v.getStyleClass().add("balance-value");
        v.setWrapText(true);
        v.setAlignment(Pos.CENTER_RIGHT);
        v.setMaxWidth(320);
        if (tone != null && tone != Tone.NEUTRAL) {
            v.getStyleClass().add("tone-" + tone.styleClass());
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, name, spacer, v);
        row.getStyleClass().addAll("balance-row", "last");
        rows.getChildren().add(row);
        last = row;
        return this;
    }

    public DetailList total(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("balance-name");
        Label v = new Label(value);
        v.getStyleClass().add("balance-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, name, spacer, v);
        row.getStyleClass().add("balance-total");
        getChildren().add(row);
        return this;
    }
}
