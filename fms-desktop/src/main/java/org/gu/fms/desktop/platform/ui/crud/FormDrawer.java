package org.gu.fms.desktop.platform.ui.crud;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.overlay.Drawer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tiroir « formulaire → opération en tâche de fond » : le moteur commun de toutes les saisies (création,
 * modification, paiement d'un bulletin, clôture d'un compte…).
 *
 * <pre>{@code
 * ui.formDrawer(messages.get("closing.title"))
 *         .form(form)
 *         .saveLabel(messages.get("closing.confirm"), AppIcon.SAVE)
 *         .onSave(() -> { Money actual = field.getValue(); return () -> service.close(account, day, actual); })
 *         .onSuccess(closing -> reload())
 *         .open();
 * }</pre>
 * Le fournisseur passé à {@link #onSave(Supplier)} est appelé sur le fil JavaFX après validation du formulaire
 * (lecture des champs) ; le travail qu'il renvoie s'exécute hors du fil JavaFX. Les erreurs métier reviennent sur
 * les champs ; abandonner un formulaire modifié demande confirmation.
 *
 * @param <R> résultat de l'opération
 */
public final class FormDrawer<R> {

    private final CrudContext ui;
    private final String title;
    private String subtitle;
    private Form form;
    private final List<Node> before = new ArrayList<>();
    private final List<Node> leftActions = new ArrayList<>();
    private String saveLabel;
    private AppIcon saveIcon = AppIcon.SAVE;
    private Tone tone = Tone.INFO;
    private boolean readOnly;
    private Supplier<Callable<R>> onSave;
    private Supplier<CompletableFuture<Boolean>> beforeSave = () -> CompletableFuture.completedFuture(true);
    private Consumer<R> onSuccess = r -> { };
    private Drawer drawer;

    FormDrawer(CrudContext ui, String title) {
        this.ui = ui;
        this.title = title;
        this.saveLabel = ui.messages.get("action.save");
    }

    public FormDrawer<R> subtitle(String text) {
        this.subtitle = text;
        return this;
    }

    public FormDrawer<R> form(Form value) {
        this.form = value;
        return this;
    }

    /** Contenu affiché au-dessus du formulaire (résumé, chiffres, avertissement). */
    public FormDrawer<R> header(Node node) {
        before.add(node);
        return this;
    }

    /** Boutons secondaires à gauche du pied (ex. « Émettre un avoir »). */
    public FormDrawer<R> leftAction(Node node) {
        leftActions.add(node);
        return this;
    }

    public FormDrawer<R> saveLabel(String label, AppIcon icon) {
        this.saveLabel = label;
        this.saveIcon = icon;
        return this;
    }

    /** DANGER : bouton rouge (opération lourde de conséquences). */
    public FormDrawer<R> tone(Tone value) {
        this.tone = value;
        return this;
    }

    /** Consultation seule : pas de bouton d'enregistrement, « Fermer » à la place d'« Annuler ». */
    public FormDrawer<R> readOnly() {
        this.readOnly = true;
        return this;
    }

    public FormDrawer<R> onSave(Supplier<Callable<R>> work) {
        this.onSave = work;
        return this;
    }

    /** Confirmation éventuelle après validation du formulaire et avant l'opération (ex. doublon probable). */
    public FormDrawer<R> beforeSave(Supplier<CompletableFuture<Boolean>> check) {
        this.beforeSave = check;
        return this;
    }

    public FormDrawer<R> onSuccess(Consumer<R> handler) {
        this.onSuccess = handler;
        return this;
    }

    public Drawer open() {
        Button cancel = new Button(ui.messages.get(readOnly ? "action.close" : "action.cancel"));
        cancel.getStyleClass().add("secondary");
        Button save = new Button(saveLabel, new FmsIcon(saveIcon, 16));
        save.getStyleClass().add(tone == Tone.DANGER ? "danger" : "primary");
        save.setDefaultButton(true);

        VBox content = new VBox(18);
        content.getChildren().addAll(before);
        if (form != null) {
            content.getChildren().add(form.view());
            form.markClean();
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox main = new HBox(8);
        main.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        // plusieurs actions secondaires : sur leur propre ligne, pour que tous les libellés restent entiers
        boolean ownRow = leftActions.size() > 1;
        if (!ownRow) {
            main.getChildren().addAll(leftActions);
        }
        main.getChildren().add(spacer);
        main.getChildren().add(cancel);
        if (!readOnly) {
            main.getChildren().add(save);
        }
        main.getChildren().forEach(n -> {
            if (n instanceof Region r && r != spacer) {
                r.setMinWidth(Region.USE_PREF_SIZE);
            }
        });
        VBox footer = new VBox(10);
        if (ownRow) {
            HBox secondary = new HBox(8);
            secondary.getChildren().addAll(leftActions);
            leftActions.forEach(n -> {
                if (n instanceof Region r) {
                    r.setMinWidth(Region.USE_PREF_SIZE);
                }
            });
            footer.getChildren().add(secondary);
        }
        footer.getChildren().add(main);
        HBox.setHgrow(footer, Priority.ALWAYS);
        footer.setMaxWidth(Double.MAX_VALUE);

        Drawer d = ui.drawers.create(title)
                .subtitle(subtitle)
                .content(content)
                .actions(footer)
                .beforeClose(this::confirmDiscard);
        save.disableProperty().bind(d.busyProperty());
        cancel.disableProperty().bind(d.busyProperty());
        leftActions.forEach(n -> n.disableProperty().bind(d.busyProperty()));
        cancel.setOnAction(e -> d.requestClose());
        save.setOnAction(e -> save(d));
        this.drawer = d;
        d.open();
        if (form != null && !readOnly) {
            form.focusFirst();
        }
        return d;
    }

    /** Ferme le tiroir sans confirmation (ex. avant d'enchaîner sur une autre action). */
    public void close() {
        if (drawer != null) {
            drawer.close();
        }
    }

    public CompletableFuture<Boolean> requestClose() {
        return drawer == null ? CompletableFuture.completedFuture(true) : drawer.requestClose();
    }

    public boolean isOpen() {
        return drawer != null && drawer.isOpen();
    }

    private CompletableFuture<Boolean> confirmDiscard() {
        if (readOnly || form == null || !form.isDirty()) {
            return CompletableFuture.completedFuture(true);
        }
        return ui.dialogs.confirm(ui.messages.get("crud.discard.title"), ui.messages.get("crud.discard.text"),
                ui.messages.get("crud.discard.confirm"), Tone.WARNING);
    }

    private void save(Drawer d) {
        if (onSave == null || (form != null && !form.validate())) {
            return;
        }
        beforeSave.get().thenAccept(ok -> {
            if (ok) {
                execute(d);
            }
        });
    }

    private void execute(Drawer d) {
        Callable<R> work = onSave.get();
        if (work == null) {
            return;
        }
        ui.tasks.job(work)
                .busy(d.busyProperty())
                .onSuccess(result -> {
                    if (form != null) {
                        form.markClean();
                    }
                    d.close();
                    onSuccess.accept(result);
                })
                .onFailure(error -> ErrorReporter.report(ui, error, form, "crud.failed.title"))
                .start();
    }
}
