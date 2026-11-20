package org.gu.fms.desktop.platform.ui.form;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import net.synedra.validatorfx.Check;
import net.synedra.validatorfx.Validator;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.domain.common.FieldError;

import java.util.*;
import java.util.function.Function;

/**
 * Moteur de formulaires FMS.
 *
 * <pre>{@code
 * Form form = forms.create();
 * form.section("Identification");
 * TextInput registration = form.text("registration", "Immatriculation").required().maxLength(20);
 * MoneyInput price = form.money("purchasePrice", "Prix d’achat");
 * ...
 * if (form.validate()) { service.save(...); }          // erreurs affichées sous chaque champ
 * form.applyErrors(validationException.errors());     // erreurs du service, sur le bon champ
 * }</pre>
 *
 * Comportement de validation (ValidatorFX)
 * – aucune erreur affichée tant que l'utilisateur n'a rien faite
 * – un champ modifié est vérifié quand il perd le focus, puis à chaque frappe
 * – au premier enregistrement, tous les champs sont vérifiés et le premier champ en erreur reçoivent le focus.
 * Mise en page : deux colonnes si la largeur le permet, sinon une ; les sections gardent leur ordre.
 */
public final class Form {
    private static final double TWO_COLUMNS_MIN_WIDTH = 520;

    private final Messages messages;
    private final Formats formats;
    private final Validator validator = new Validator();
    private final List<Object> layout = new ArrayList<>();          // String (titre de section) ou FormField
    private final Map<String, FormField<?, ?>> fields = new LinkedHashMap<>();
    private final Map<FormField<?, ?>, Check> checks = new LinkedHashMap<>();
    private BooleanBinding dirty;
    private VBox view;

    Form(Messages messages, Formats formats) {
        this.messages = messages;
        this.formats = formats;
    }

    // ---- Construction ---------------------------------------------------------------------

    public Form section(String title) {
        layout.add(title);
        return this;
    }

    public TextInput text(String name, String label) {
        return add(new TextInput(name, label, messages));
    }

    public TextAreaInput textArea(String name, String label) {
        return add(new TextAreaInput(name, label, messages));
    }

    public MoneyInput money(String name, String label) {
        return add(new MoneyInput(name, label, messages, formats));
    }

    public NumberInput number(String name, String label) {
        return add(new NumberInput(name, label, messages, formats));
    }

    public PercentInput percent(String name, String label) {
        return add(new PercentInput(name, label, messages));
    }

    public DateInput date(String name, String label) {
        return add(new DateInput(name, label, messages, formats));
    }

    public <T> ChoiceInput<T> choice(String name, String label, List<T> options, Function<T, String> labeler) {
        return add(new ChoiceInput<>(name, label, messages, options, labeler));
    }

    /** Choix parmi les valeurs d'une énumération, libellés « enum.Type.VALEUR ». */
    public <E extends Enum<E>> ChoiceInput<E> choice(String name, String label, Class<E> type) {
        return choice(name, label, List.of(type.getEnumConstants()), messages::label);
    }

    public CheckInput check(String name, String label) {
        return add(new CheckInput(name, label, messages));
    }

    /** Ajoute un champ personnalisé (sous-classe de {@link FormField}). */
    public <F extends FormField<?, ?>> F add(F field) {
        if (fields.putIfAbsent(field.name(), field) != null) {
            throw new IllegalArgumentException("Champ en double : " + field.name());
        }
        layout.add(field);
        Check check = validator.createCheck()
                .dependsOn("value", field.valueProperty())
                .dependsOn("input", field.inputErrorProperty())
                .dependsOn("external", field.externalErrorProperty())
                .withMethod(ctx -> {
                    String message = field.validationMessage();
                    if (message != null) {
                        ctx.error(message);
                    }
                })
                .decorates(field.focusTarget())
                .decoratingWith(message -> new FieldDecoration(field, message.getText()));
        checks.put(field, check);
        field.focusTarget().focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && field.isDirty()) {
                check.immediate();       // champ touché : vérifié maintenant puis à chaque modification
            }
        });
        return field;
    }

    // ---- Utilisation ----------------------------------------------------------------------

    /** Vérifie tous les champs, affiche les erreurs, place le focus sur la première. Vrai si tout est valide. */
    public boolean validate() {
        boolean valid = validator.validate();
        checks.values().forEach(Check::immediate);
        if (!valid) {
            focusFirstError();
        }
        return valid;
    }

    /**
     * Affiche les erreurs renvoyées par le service sur les champs correspondants.
     *
     * @return erreurs sans champ correspondant (à afficher autrement, ex. dans un dialogue)
     */
    public List<FieldError> applyErrors(List<FieldError> errors) {
        List<FieldError> unmatched = new ArrayList<>();
        for (FieldError e : errors) {
            FormField<?, ?> field = e.field() == null ? null : fields.get(e.field());
            if (field == null) {
                unmatched.add(e);
                continue;
            }
            field.externalErrorProperty().set(messages.error(e));
            checks.get(field).immediate();
            checks.get(field).recheck();
        }
        focusFirstError();
        return unmatched;
    }

    public Optional<FormField<?, ?>> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public boolean isDirty() {
        return dirtyBinding().get();
    }

    /** Vrai dès qu'un champ diffère de son état chargé. */
    public BooleanBinding dirtyBinding() {
        if (dirty == null) {
            BooleanBinding any = Bindings.createBooleanBinding(() -> false);
            for (FormField<?, ?> f : fields.values()) {
                any = any.or(f.dirtyBinding());
            }
            dirty = any;
        }
        return dirty;
    }

    /** Les valeurs actuelles deviennent la référence (après chargement ou enregistrement). */
    public void markClean() {
        fields.values().forEach(FormField::markClean);
    }

    public void focusFirst() {
        fields.values().stream().findFirst().ifPresent(FormField::requestFocus);
    }

    private void focusFirstError() {
        fields.values().stream()
                .filter(f -> f.validationMessage() != null)
                .findFirst()
                .ifPresent(FormField::requestFocus);
    }

    // ---- Vue ------------------------------------------------------------------------------

    public Region view() {
        if (view == null) {
            view = new VBox(22);
            view.getStyleClass().add("form");
            List<FormField<?, ?>> group = new ArrayList<>();
            String title = null;
            for (Object item : layout) {
                if (item instanceof String sectionTitle) {
                    addSection(title, group);
                    title = sectionTitle;
                    group = new ArrayList<>();
                } else {
                    group.add((FormField<?, ?>) item);
                }
            }
            addSection(title, group);
        }
        return view;
    }

    private void addSection(String title, List<FormField<?, ?>> group) {
        if (title == null && group.isEmpty()) {
            return;
        }
        VBox section = new VBox(12);
        section.getStyleClass().add("form-section");
        if (title != null) {
            Label label = new Label(title);
            label.getStyleClass().add("form-section-title");
            section.getChildren().add(label);
        }
        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(16);
        grid.setVgap(14);
        section.getChildren().add(grid);
        List<FormField<?, ?>> fieldsOfSection = List.copyOf(group);
        section.widthProperty().addListener((obs, o, w) ->
                arrange(grid, fieldsOfSection, w.doubleValue() >= TWO_COLUMNS_MIN_WIDTH ? 2 : 1));
        arrange(grid, fieldsOfSection, 2);
        view.getChildren().add(section);
    }

    private static void arrange(GridPane grid, List<FormField<?, ?>> group, int columns) {
        if (grid.getColumnConstraints().size() == columns && !grid.getChildren().isEmpty()) {
            return;
        }
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columns);
            c.setHgrow(Priority.ALWAYS);
            c.setFillWidth(true);
            grid.getColumnConstraints().add(c);
        }
        int row = 0;
        int col = 0;
        for (FormField<?, ?> f : group) {
            int span = Math.min(f.span(), columns);
            if (col + span > columns) {
                row++;
                col = 0;
            }
            Node cell = f.cell();
            GridPane.setColumnSpan(cell, span);
            grid.add(cell, col, row);
            col += span;
            if (col >= columns) {
                row++;
                col = 0;
            }
        }
    }
}
