package org.gu.fms.desktop.platform.ui.form;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.i18n.Messages;

public final class CheckInput extends FormField<Boolean, CheckInput>{

    private final CheckBox box;

    CheckInput(String name, String label, Messages messages) {
        super(name, label, messages);
        box = new CheckBox(label);
        setValue(false);
        markClean();
        box.selectedProperty().addListener((obs, o, selected) -> setValue(selected));
        valueProperty().addListener((obs, o, v) -> box.setSelected(Boolean.TRUE.equals(v)));
    }

    @Override
    protected boolean isEmpty(Boolean v) {
        return false;
    }

    @Override
    protected boolean showsLabel() {
        return false;
    }

    @Override
    protected Region buildEditor() {
        return box;
    }

    @Override
    protected Node focusTarget() {
        return box;
    }
}
