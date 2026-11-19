package org.gu.fms.desktop.platform.ui.form;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** Texte sur une ligne. Valeur : texte sans espaces de bord, ou null si vide. */
public class TextInput extends FormField<String, TextInput> {
    private final TextField field = new TextField();
    private int maxLength;
    private UnaryOperator<String> onBlur = UnaryOperator.identity();

    TextInput(String name, String label, Messages messages) {
        super(name, label, messages);
        field.textProperty().addListener((obs, o, text) -> {
            String v = normalize(text);
            if (!Objects.equals(v, getValue())) {
                setValue(v);
            }
        });
        valueProperty().addListener((obs, o, v) -> {
            if (!Objects.equals(v, normalize(field.getText())))
                field.setText(v == null ? "": v);
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && field.getText() != null) {
                String transformed = onBlur.apply(field.getText());
                if (!Objects.equals(transformed, field.getText()))
                    field.setText(transformed);
            }
        });
    }

    public TextInput maxLength(int max) {
        this.maxLength = max;
        field.setTextFormatter(new TextFormatter<String>(change -> change
                .getControlNewText().length() <= maxLength ? change : null
        ));
        return this;
    }

    /** Transformation appliquée quand le champ perd le focus (ex. mise en majuscules). */
    public TextInput onBlur(UnaryOperator<String> transform) {
        this.onBlur = transform;
        return this;
    }

    public TextInput prompt(String text) {
        field.setPromptText(text);
        return this;
    }

    @Override
    protected Region buildEditor() {
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    @Override
    protected Node focusTarget() {
        return field;
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String v = text.strip();
        return v.isEmpty() ? null : v;
    }
}
