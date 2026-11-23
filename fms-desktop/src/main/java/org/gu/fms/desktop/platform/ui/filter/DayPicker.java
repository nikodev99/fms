package org.gu.fms.desktop.platform.ui.filter;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Choix d'une journée avec « jour précédent / suivant / aujourd'hui » (point journalier, clôture).
 * Jamais au-delà d'aujourd'hui.
 */
public final class DayPicker extends HBox {

    private final DatePicker picker = new DatePicker();

    public DayPicker(Formats formats, Supplier<LocalDate> today, String previousTip, String nextTip, String todayLabel) {
        super(4);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("day-picker");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate d) {
                return d == null ? "" : formats.date(d);
            }

            @Override
            public LocalDate fromString(String text) {
                return formats.parseDate(text).orElse(null);
            }
        });
        picker.setValue(today.get());
        picker.setPrefWidth(150);
        picker.setDayCellFactory(p -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isAfter(today.get()));
            }
        });
        picker.valueProperty().addListener((obs, o, d) -> {
            if (d == null || d.isAfter(today.get())) {
                picker.setValue(o == null ? today.get() : o);
            }
        });
        Button previous = iconButton(AppIcon.CHEVRON_LEFT, previousTip);
        previous.setOnAction(e -> picker.setValue(picker.getValue().minusDays(1)));
        Button next = iconButton(AppIcon.CHEVRON_RIGHT, nextTip);
        next.setOnAction(e -> picker.setValue(picker.getValue().plusDays(1)));
        next.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> picker.getValue() == null || !picker.getValue().isBefore(today.get()), picker.valueProperty()));
        Button now = new Button(todayLabel);
        now.getStyleClass().add("secondary");
        now.setOnAction(e -> picker.setValue(today.get()));
        getChildren().addAll(previous, picker, next, now);
    }

    public ObjectProperty<LocalDate> dayProperty() {
        return picker.valueProperty();
    }

    public LocalDate day() {
        return picker.getValue();
    }

    private static Button iconButton(AppIcon icon, String tip) {
        Button b = new Button();
        b.setGraphic(new FmsIcon(icon, 18));
        b.getStyleClass().add("icon-button");
        b.setTooltip(new Tooltip(tip));
        return b;
    }
}
