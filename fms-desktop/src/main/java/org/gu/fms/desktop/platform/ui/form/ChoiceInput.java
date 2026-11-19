package org.gu.fms.desktop.platform.ui.form;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Choix dans une liste fermée (énumération, référentiel). Les libellés passent par {@code labeler},
 * jamais par {@code toString()}.
 */
public final class ChoiceInput<T> extends FormField<T, ChoiceInput<T>> {

    private final ComboBox<T> combo = new ComboBox<>();
    private final Function<T, String> labeler;
    private final List<T> options;

    ChoiceInput(String name, String label, Messages messages, List<T> options, Function<T, String> labeler) {
        super(name, label, messages);
        this.labeler = labeler;
        this.options = List.copyOf(options);
        combo.setItems(FXCollections.observableArrayList(this.options));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : labeler.apply(item);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
        combo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? messages.get("common.none") : labeler.apply(item));
            }
        });
        combo.valueProperty().bindBidirectional(valueProperty());
    }

    /** Ajoute une entrée « — » en tête pour pouvoir vider un champ facultatif. */
    public ChoiceInput<T> allowEmpty() {
        List<T> withEmpty = new ArrayList<>();
        withEmpty.add(null);
        withEmpty.addAll(options);
        T current = getValue();
        combo.setItems(FXCollections.observableArrayList(withEmpty));
        combo.setValue(current);
        combo.setPromptText(messages.get("common.notSpecified"));
        return this;
    }

    public ChoiceInput<T> prompt(String text) {
        combo.setPromptText(text);
        return this;
    }

    String labelOf(T item) {
        return labeler.apply(item);
    }

    @Override
    protected Region buildEditor() {
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    @Override
    protected Node focusTarget() {
        return combo;
    }
}
