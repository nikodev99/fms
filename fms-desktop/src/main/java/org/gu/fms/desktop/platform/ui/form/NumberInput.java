package org.gu.fms.desktop.platform.ui.form;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.util.Objects;
import java.util.regex.Pattern;

/** Nombre entier positif (kilométrage, jours, quantités), avec unité facultative en suffixe. */
public final class NumberInput extends FormField<Long, NumberInput> {
    private static final Pattern ALLOWED = Pattern.compile("[0-9 \\u00A0\\u202F]*");

    private final Formats formats;
    private final TextField field = new TextField();
    private String unit;
    private boolean fromText;

    NumberInput(String name, String label, Messages messages, Formats formats) {
        super(name, label, messages);
        this.formats = formats;
        rule(Rules.notNegative());
        field.setTextFormatter(new TextFormatter<String>(c -> ALLOWED.matcher(c.getControlNewText()).matches() ? c : null));
        field.setAlignment(Pos.CENTER_RIGHT);
        field.getStyleClass().add("numeric");

        field.textProperty().addListener((obs, o, text) -> parse(text));
        valueProperty().addListener((obs, o, v) -> {
            if (!fromText) {
                field.setText(v == null ? "" : formats.number(v));
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && inputErrorProperty().get() == null && getValue() != null) {
                fromText = true;
                try {
                    field.setText(formats.number(getValue()));
                } finally {
                    fromText = false;
                }
            }
        });
    }

    public NumberInput unit(String text) {
        this.unit = text;
        return this;
    }

    @Override
    protected Region buildEditor() {
        field.setMaxWidth(Double.MAX_VALUE);
        if (unit == null) {
            return field;
        }
        Label suffix = new Label(unit);
        suffix.getStyleClass().add("field-suffix");
        HBox group = new HBox(field, suffix);
        group.getStyleClass().add("input-group");
        group.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return group;
    }

    @Override
    protected Node focusTarget() {
        return field;
    }

    private void parse(String text) {
        fromText = true;
        try {
            Long parsed = formats.parseLong(text).orElse(null);
            inputErrorProperty().set(null);
            if (!Objects.equals(parsed, getValue())) {
                setValue(parsed);
            }
        } catch (IllegalArgumentException e) {
            inputErrorProperty().set(messages.get("validation.invalidNumber"));
            setValue(null);
        } finally {
            fromText = false;
        }
    }
}
