package org.gu.fms.desktop.platform.ui.form;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Champ de formulaire typé : libellé, éditeur, message d'aide ou d'erreur, règles.
 *
 * Trois sources d'erreur, par priorité :
 *  1. saisie illisible (ex. « 12a » dans un montant) — détectée par l'éditeur ;
 *  2. obligation et règles du champ — vérifiées à l'écran ;
 *  3. erreur renvoyée par le service (ex. immatriculation déjà utilisée) — effacée dès que la valeur change.
 *
 * @param <T>    type de la valeur
 * @param <SELF> type concret, pour enchaîner les réglages : {@code form.text(…).required().maxLength(20)}
 */
public abstract class FormField<T, SELF extends FormField<T, SELF>> {
    protected final Messages messages;
    private final String name;
    private final String label;

    private final ObjectProperty<T> value = new SimpleObjectProperty<>();
    private final StringProperty inputError = new SimpleStringProperty();
    private final StringProperty externalError = new SimpleStringProperty();
    private final StringProperty shownError = new SimpleStringProperty();
    private final List<Rule<? super T>> rules = new ArrayList<>();
    private final ObjectProperty<T> baseline = new SimpleObjectProperty<>();

    private BooleanBinding dirty;
    private boolean required;
    private String help;
    private int span = 1;
    private Region editor;
    private VBox cell;

    protected FormField(String name, String label, Messages messages) {
        this.name = Objects.requireNonNull(name, "name");
        this.label = Objects.requireNonNull(label, "label");
        this.messages = messages;
        value.addListener((obs, o, n) -> externalError.set(null));
    }

    /** Construit l'éditeur (contrôle et éventuels suffixes). Appelé une seule fois. */
    protected abstract Region buildEditor();

    /** Contrôle qui reçoit le focus et la décoration d'erreur. */
    protected abstract Node focusTarget();

    protected boolean isEmpty(T v) {
        return v == null;
    }

    @SuppressWarnings("unchecked")
    protected final SELF self() {
        return (SELF) this;
    }

    // ---- Réglages -------------------------------------------------------------------------
    public SELF required() {
        this.required = true;
        return self();
    }

    public SELF help(String text) {
        this.help = text;
        return self();
    }

    /** Nombre de colonnes occupées (1 ou 2) quand le formulaire est sur deux colonnes. */
    public SELF span(int columns) {
        this.span = Math.max(1, columns);
        return self();
    }

    public SELF rule(Rule<? super T> rule) {
        rules.add(rule);
        return self();
    }

    // ---- Valeur -----------------------------------------------------------------------------

    public String name() {
        return name;
    }

    public String label() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public int span() {
        return span;
    }

    public T getValue() {
        return value.get();
    }

    public SELF setValue(T v) {
        value.set(v);
        return self();
    }

    public ObjectProperty<T> valueProperty() {
        return value;
    }

    protected StringProperty inputErrorProperty() {
        return inputError;
    }

    StringProperty externalErrorProperty() {
        return externalError;
    }

    /** Message d'erreur affiché, ou null. */
    public ReadOnlyStringProperty shownErrorProperty() {
        return shownError;
    }

    /** Le champ diffère-t-il de la dernière valeur chargée ou enregistrée ? */
    public boolean isDirty() {
        return dirtyBinding().get();
    }

    BooleanBinding dirtyBinding() {
        if (dirty == null) {
            dirty = Bindings.createBooleanBinding(
                    () -> !Objects.equals(baseline.get(), value.get()) || inputError.get() != null,
                    baseline, value, inputError);
        }
        return dirty;
    }

    /** La valeur actuelle devient la référence (après chargement ou enregistrement). */
    public void markClean() {
        baseline.set(value.get());
    }

    // ---- Validation -------------------------------------------------------------------------
    /** Message d'erreur du champ dans son état actuel, ou null s'il est valide. */
    public String validationMessage() {
        if (inputError.get() != null) {
            return inputError.get();
        }
        T v = value.get();
        if (isEmpty(v)) {
            return required ? messages.get("validation.required") : null;
        }
        for (Rule<? super T> rule : rules) {
            Rule.Issue issue = rule.check(v);
            if (issue != null) {
                return messages.get(issue.messageKey(), issue.args().toArray());
            }
        }
        return externalError.get();
    }

    void showError(String message) {
        shownError.set(message);
        for (Node n : decorated()) {
            if (!n.getStyleClass().contains("invalid")) {
                n.getStyleClass().add("invalid");
            }
        }
    }

    void clearError() {
        shownError.set(null);
        for (Node n : decorated()) {
            n.getStyleClass().remove("invalid");
        }
    }

    /** Le contrôle, et son groupe s'il a un suffixe (ex. « XAF »), portent la classe « invalid ». */
    private List<Node> decorated() {
        Node target = focusTarget();
        Region group = editor();
        return group == target ? List.of(target) : List.of(target, group);
    }

    // ---- Vue ------------------------------------------------------------------------------

    public Region editor() {
        if (editor == null) {
            editor = buildEditor();
        }
        return editor;
    }

    static Region getRegion(Label suffix, TextField field) {
        suffix.getStyleClass().add("field-suffix");
        HBox group = new HBox(field, suffix);
        group.getStyleClass().add("input-group");
        group.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        return group;
    }

    public void requestFocus() {
        focusTarget().requestFocus();
    }

    /** Bloc complet : libellé, éditeur, aide/erreur. */
    Region cell() {
        if (cell == null) {
            cell = new VBox(6);
            cell.getStyleClass().add("form-field");
            if (showsLabel()) {
                Label caption = new Label(label);
                caption.getStyleClass().add("field-label");
                HBox header = new HBox(3, caption);
                if (required) {
                    Label mark = new Label("*");
                    mark.getStyleClass().add("required-mark");
                    header.getChildren().add(mark);
                }
                caption.setLabelFor(focusTarget());
                cell.getChildren().add(header);
            }
            cell.getChildren().add(editor());

            Label message = new Label();
            message.setWrapText(true);
            message.getStyleClass().add("field-message");
            message.textProperty().bind(Bindings.createStringBinding(
                    () -> shownError.get() != null ? shownError.get() : (help == null ? "" : help), shownError));
            shownError.addListener((obs, o, n) -> {
                if (n != null) {
                    message.getStyleClass().add("error");
                } else {
                    message.getStyleClass().remove("error");
                }
            });
            message.managedProperty().bind(message.textProperty().isNotEmpty());
            message.visibleProperty().bind(message.managedProperty());
            cell.getChildren().add(message);
        }
        return cell;
    }

    /** Faux pour les champs qui portent leur libellé eux-mêmes (case à cocher). */
    protected boolean showsLabel() {
        return true;
    }
}
