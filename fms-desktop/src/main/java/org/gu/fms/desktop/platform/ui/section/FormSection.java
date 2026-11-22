package org.gu.fms.desktop.platform.ui.section;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Screen;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Section de paramètres « un formulaire + Enregistrer » (entreprise, région, valeurs par défaut…).
 *
 * Chargement et enregistrement en tâche de fond ; erreurs du service affichées sur les champs ;
 * « Annuler » recharge les valeurs enregistrées ; quitter avec des modifications demande confirmation.
 *
 * @param <S> état chargé et enregistré (ex. {@code CompanyProfile})
 */
public abstract class FormSection<S> implements Screen, Screen.Embeddable {

    protected final CrudContext ui;
    protected final Messages messages;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private Form form;
    private Node view;
    private boolean loaded;

    protected FormSection(CrudContext ui) {
        this.ui = ui;
        this.messages = ui.messages();
    }

    protected abstract String title();

    /** Explication affichée sous le titre (facultative). */
    protected String description() {
        return null;
    }

    /** Crée les champs du formulaire (appelé une fois). */
    protected abstract Form buildForm();

    /** Lecture, hors du fil JavaFX. */
    protected abstract S load();

    /** Remplit les champs (fil JavaFX). */
    protected abstract void fill(S state);

    /** Construit l'état à enregistrer depuis les champs (fil JavaFX, après validation du formulaire). */
    protected abstract S collect();

    /** Enregistrement, hors du fil JavaFX. Peut lever {@link ValidationException}. */
    protected abstract void save(S state);

    protected abstract String savedMessage();

    /** Contenu ajouté sous le formulaire, dans le même panneau (facultatif). */
    protected Node extra() {
        return null;
    }

    /** Modifications non enregistrées hors formulaire (ex. logo choisi) ; à surcharger si besoin. */
    protected boolean extraDirty() {
        return false;
    }

    /** Appelé après un enregistrement réussi (fil JavaFX). */
    protected void afterSave(S saved) {
    }

    protected final Form form() {
        return form;
    }

    @Override
    public void embed() {
        // toujours affichée dans une section : rien à changer
    }

    @Override
    public final Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    @Override
    public void onShow() {
        if (!loaded || !isDirty()) {
            reload();
        }
    }

    @Override
    public CompletableFuture<Boolean> confirmLeave() {
        if (!isDirty()) {
            return CompletableFuture.completedFuture(true);
        }
        return ui.dialogs().confirm(messages.get("crud.discard.title"), messages.get("crud.discard.text"),
                messages.get("crud.discard.confirm"), Tone.WARNING).thenApply(discard -> {
            if (discard) {
                reload();
            }
            return discard;
        });
    }

    protected final boolean isDirty() {
        return form != null && (form.isDirty() || extraDirty());
    }

    protected final void reload() {
        ui.tasks().job(this::load).busy(busy).onSuccess(state -> {
            fill(state);
            form.markClean();
            loaded = true;
        }).start();
    }

    private Node build() {
        form = buildForm();

        Label title = new Label(title());
        title.getStyleClass().add("section-title");
        VBox header = new VBox(4, title);
        String text = description();
        if (text != null) {
            Label d = new Label(text);
            d.getStyleClass().add("section-description");
            d.setWrapText(true);
            d.setMinHeight(Region.USE_PREF_SIZE);
            header.getChildren().add(d);
        }

        VBox body = new VBox(18, header, form.view());
        Node more = extra();
        if (more != null) {
            body.getChildren().add(more);
        }
        body.getStyleClass().add("settings-body");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("inline-spinner");
        spinner.visibleProperty().bind(busy);
        Button cancel = new Button(messages.get("action.cancel"));
        cancel.getStyleClass().add("secondary");
        cancel.setOnAction(e -> reload());
        Button save = new Button(messages.get("action.save"), new FmsIcon(AppIcon.SAVE, 16));
        save.getStyleClass().add("primary");
        save.setDefaultButton(false);
        save.setOnAction(e -> submit());
        save.disableProperty().bind(busy);
        cancel.disableProperty().bind(busy);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox footer = new HBox(8, spinner, grow, cancel, save);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("settings-footer");

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox panel = new VBox(scroll, footer);
        panel.getStyleClass().addAll("panel", "settings-panel");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private void submit() {
        if (!form.validate()) {
            return;
        }
        S state = collect();
        ui.tasks().job(() -> {
            save(state);
            return state;
        }).busy(busy).onSuccess(saved -> {
            form.markClean();
            ui.toasts().success(savedMessage());
            afterSave(saved);
        }).onFailure(this::handleError).start();
    }

    private void handleError(Throwable error) {
        if (error instanceof ValidationException ve) {
            List<FieldError> unmatched = form.applyErrors(ve.errors());
            if (!unmatched.isEmpty()) {
                ui.dialogs().error(messages.get("crud.invalid.title"),
                        unmatched.stream().map(messages::error).collect(Collectors.joining("\n")));
            }
        } else {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Enregistrement impossible", error);
            ui.dialogs().error(messages.get("crud.failed.title"), messages.get("crud.failed.text"));
        }
    }
}
