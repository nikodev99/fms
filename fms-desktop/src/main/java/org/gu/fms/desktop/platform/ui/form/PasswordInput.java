package org.gu.fms.desktop.platform.ui.form;

import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.i18n.Messages;

/** Mot de passe (saisie masquée, espaces conservés). Valeur : texte saisi, ou null si vide. */
public final class PasswordInput extends FormField<String, PasswordInput> {

    private final PasswordField field = new PasswordField();

    PasswordInput(String name, String label, Messages messages) {
        super(name, label, messages);
        field.textProperty().addListener((obs, o, text) -> setValue(text == null || text.isEmpty() ? null : text));
        valueProperty().addListener((obs, o, v) -> {
            if (v == null && !field.getText().isEmpty()) {
                field.clear();
            }
        });
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
}
