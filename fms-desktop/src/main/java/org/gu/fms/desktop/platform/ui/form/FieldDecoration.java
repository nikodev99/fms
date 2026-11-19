package org.gu.fms.desktop.platform.ui.form;

import javafx.scene.Node;
import net.synedra.validatorfx.Decoration;

public class FieldDecoration implements Decoration {
    private final FormField<?, ?> field;
    private final String message;

    FieldDecoration(FormField<?, ?> field, String message) {
        this.field = field;
        this.message = message;
    }

    @Override
    public void add(Node target) {
        field.showError(message);
    }

    @Override
    public void remove(Node target) {
        field.clearError();
    }
}
