package org.gu.fms.desktop.platform.ui.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.i18n.Messages;

/**
 * Choix multiple (cases à cocher), ex. jours de repos, véhicules financés, modules autorisés.
 * La valeur est la liste des éléments cochés, dans l'ordre des options ; jamais nulle (liste vide).
 *
 * @param <T> type d'une option
 */
public final class MultiChoiceInput<T> extends FormField<List<T>, MultiChoiceInput<T>> {

    private final FlowPane pane = new FlowPane(16, 8);
    private final Map<T, CheckBox> boxes = new LinkedHashMap<>();
    private final Function<T, String> labeler;
    private boolean syncing;

    MultiChoiceInput(String name, String label, Messages messages, List<T> options, Function<T, String> labeler) {
        super(name, label, messages);
        this.labeler = labeler;
        pane.getStyleClass().add("multi-choice");
        setOptions(options);
        setValue(List.of());
        markClean();
        valueProperty().addListener((obs, o, v) -> sync(v));
    }

    /** Remplace les options ; les éléments cochés encore présents le restent. */
    public MultiChoiceInput<T> setOptions(List<T> options) {
        List<T> kept = getValue() == null ? List.of() : getValue();
        boxes.clear();
        pane.getChildren().clear();
        for (T option : options) {
            CheckBox box = new CheckBox(labeler.apply(option));
            box.selectedProperty().addListener((obs, o, selected) -> {
                if (!syncing) {
                    setValue(collect());
                }
            });
            boxes.put(option, box);
            pane.getChildren().add(box);
        }
        List<T> still = kept.stream().filter(boxes::containsKey).toList();
        if (getValue() != null) {
            setValue(still);
        }
        sync(still);
        return this;
    }

    private List<T> collect() {
        List<T> selected = new ArrayList<>();
        boxes.forEach((option, box) -> {
            if (box.isSelected()) {
                selected.add(option);
            }
        });
        return List.copyOf(selected);
    }

    private void sync(List<T> value) {
        syncing = true;
        try {
            boxes.forEach((option, box) -> box.setSelected(value != null && value.contains(option)));
        } finally {
            syncing = false;
        }
    }

    @Override
    public MultiChoiceInput<T> setValue(List<T> v) {
        // Toujours dans l'ordre des options : deux sélections identiques sont égales (formulaire « modifié »).
        List<T> ordered = v == null ? List.of() : boxes.keySet().stream().filter(v::contains).toList();
        return super.setValue(ordered);
    }

    @Override
    protected boolean isEmpty(List<T> v) {
        return v == null || v.isEmpty();
    }

    @Override
    protected Region buildEditor() {
        return pane;
    }

    @Override
    protected Node focusTarget() {
        return pane;
    }
}
