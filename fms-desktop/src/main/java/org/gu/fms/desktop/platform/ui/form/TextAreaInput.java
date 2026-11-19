package org.gu.fms.desktop.platform.ui.form;

import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.util.Objects;

/** Texte sur plusieurs lignes (remarques, motifs). Occupe toute la largeur par défaut. */
public final class TextAreaInput extends FormField<String, TextAreaInput> {
    private final TextArea area = new TextArea();

    TextAreaInput(String name, String label, Messages messages) {
        super(name, label, messages);
        span(2);
        area.setWrapText(true);
        area.setPrefRowCount(3);
        area.textProperty().addListener((obs, o, text) -> {
            String v = normalize(text);
            if (!Objects.equals(v, getValue())) {
                setValue(v);
            }
        });
        valueProperty().addListener((obs, o, v) -> {
            if (!Objects.equals(v, normalize(area.getText()))) {
                area.setText(v == null ? "" : v);
            }
        });
    }

    public TextAreaInput maxLength(int max) {
        area.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= max ? change : null));
        return this;
    }

    public TextAreaInput rows(int rows) {
        area.setPrefRowCount(rows);
        return this;
    }

    @Override
    protected Region buildEditor() {
        area.setMaxWidth(Double.MAX_VALUE);
        return area;
    }

    @Override
    protected Node focusTarget() {
        return area;
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String v = text.strip();
        return v.isEmpty() ? null : v;
    }
}
