package org.gu.fms.desktop.platform.ui.crud;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Screen;
import org.gu.fms.desktop.platform.ui.overlay.Drawer;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Écran générique « liste + création/modification » : la base de tous les référentiels
 * (véhicules, chauffeurs, comptes, catégories…).
 * <p>
 * Fournit : en-tête avec compteur et action principale, recherche, tableau, états vides, tiroir d'édition,
 * enregistrement en tâche de fond, erreurs de validation affichées sur les champs, refus des modifications
 * concurrentes, confirmation avant d'abandonner des modifications.
 * <p>
 * Une sous-classe décrit seulement : ses textes, ses colonnes et son éditeur.
 *
 * @param <T> entité du domaine
 */
public abstract class CrudScreen<T> implements Screen, Screen.Embeddable {
    protected final CrudContext ui;
    protected final Messages messages;
    private final CrudService<T> service;
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private DataTable<T> table;
    private Node view;
    private Drawer drawer;
    private EntityEditor<T> editor;
    private boolean embedded;

    protected CrudScreen(CrudContext ui, CrudService<T> service) {
        this.ui = ui;
        this.messages = ui.messages;
        this.service = service;
    }

    // ---- À fournir par l'écran ----------------------------------------------------------------

    protected abstract String title();

    /** Ex. « 12 véhicules » ; reçoit le nombre affiché et le total. */
    protected abstract String countText(int visible, int total);

    protected abstract String createLabel();

    protected abstract AppIcon icon();

    protected abstract String emptyTitle();

    protected abstract String emptyText();

    protected abstract void configureTable(DataTable<T> table);

    /** {@code existing} est null pour une création. */
    protected abstract EntityEditor<T> createEditor(T existing);

    /** Message après enregistrement. */
    protected abstract String savedMessage(T saved, boolean created);

    /** Identité métier, pour retrouver une ligne après rechargement (ex. id). */
    protected abstract Object identityOf(T entity);

    /** Filtres affichés à droite de la recherche (facultatif). */
    protected Node filters() {
        return null;
    }

    /**
     * Chargé HORS du fil JavaFX avant chaque rechargement de la liste : listes de référence nécessaires aux
     * colonnes et aux éditeurs (catégories, véhicules, chauffeurs…). Les valeurs lues ici sont visibles par le
     * fil JavaFX quand la liste s'affiche.
     */
    protected void prepare() {
    }

    /** Faux pour une liste en lecture seule (pas de bouton « Nouveau »). */
    protected boolean canCreate() {
        return true;
    }

    /** Faux si la ligne ne s'ouvre pas en édition (ex. ligne calculée). */
    protected boolean canOpen(T item) {
        return true;
    }

    /** Texte d'aide sous le titre (facultatif), ex. pour expliquer une règle de l'écran. */
    protected String hint() {
        return null;
    }

    @Override
    public void embed() {
        this.embedded = true;
    }

    // ---- Écran --------------------------------------------------------------------------------

    @Override
    public final Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    @Override
    public void onShow() {
        reload(null);
    }

    @Override
    public CompletableFuture<Boolean> confirmLeave() {
        return drawer != null && drawer.isOpen() ? drawer.requestClose() : CompletableFuture.completedFuture(true);
    }

    protected final DataTable<T> table() {
        return table;
    }

    private Node build() {
        table = ui.tables.create();
        configureTable(table);
        table.onOpen(this::openEditor);

        Label titleLabel = new Label(title());
        titleLabel.getStyleClass().add("page-title");
        Label count = new Label();
        count.getStyleClass().add("page-subtitle");
        count.textProperty().bind(Bindings.createStringBinding(
                () -> countText(table.visibleCountProperty().get(), table.totalCountProperty().get()),
                table.visibleCountProperty(), table.totalCountProperty()));
        VBox titles = new VBox(4, titleLabel, count);
        if (embedded) {
            titles.getChildren().remove(titleLabel);
        }
        String hintText = hint();
        if (hintText != null) {
            Label hintLabel = new Label(hintText);
            hintLabel.getStyleClass().add("page-hint");
            hintLabel.setWrapText(true);
            hintLabel.setMinHeight(Region.USE_PREF_SIZE);
            titles.getChildren().add(hintLabel);
        }
        HBox.setHgrow(titles, Priority.SOMETIMES);
        titles.setAlignment(Pos.CENTER_LEFT);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("inline-spinner");
        spinner.visibleProperty().bind(loading);

        Button create = new Button(createLabel(), new FmsIcon(AppIcon.ADD, 16));
        create.getStyleClass().add("primary");
        create.setOnAction(e -> openEditor(null));
        create.setVisible(canCreate());
        create.setManaged(canCreate());
        create.setMinWidth(Region.USE_PREF_SIZE);          // le bouton garde son libellé complet ; le texte d'aide se replie

        HBox header = new HBox(12, titles, grow(), spinner, create);
        header.getStyleClass().add("page-header");
        header.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText(messages.get("common.search"));
        search.textProperty().bindBidirectional(table.searchTextProperty());
        HBox searchBox = new HBox(8, new FmsIcon(AppIcon.SEARCH, 16), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        HBox toolbar = new HBox(12, searchBox);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Node extra = filters();
        if (extra != null) {
            toolbar.getChildren().add(extra);
        }

        VBox emptyAll = emptyState(icon(), emptyTitle(), emptyText(), canCreate());
        VBox emptySearch = emptyState(AppIcon.SEARCH, messages.get("common.noResultTitle"),
                messages.get("common.noResultText"), false);
        table.totalCountProperty().addListener((obs, o, total) ->
                table.placeholder(total.intValue() == 0 ? emptyAll : emptySearch));
        table.placeholder(emptyAll);

        VBox panel = new VBox(table);
        panel.getStyleClass().addAll("panel", "table-panel");
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(panel, Priority.ALWAYS);

        VBox page = new VBox(16, header, toolbar, panel);
        if (embedded) {
            page.getStyleClass().add("section-page");
        } else {
            page.getStyleClass().addAll("page", "content");
        }
        return page;
    }

    private VBox emptyState(AppIcon icon, String title, String text, boolean withAction) {
        FmsIcon graphic = new FmsIcon(icon, 30);
        graphic.getStyleClass().add("empty-icon");
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        Label d = new Label(text);
        d.getStyleClass().add("empty-text");
        d.setWrapText(true);
        d.setMaxWidth(420);
        VBox box = new VBox(8, graphic, t, d);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        if (withAction) {
            Button create = new Button(createLabel(), new FmsIcon(AppIcon.ADD, 16));
            create.getStyleClass().add("primary");
            create.setOnAction(e -> openEditor(null));
            VBox.setMargin(create, new javafx.geometry.Insets(8, 0, 0, 0));
            box.getChildren().add(create);
        }
        return box;
    }

    // ---- Chargement ---------------------------------------------------------------------------

    /** Recharge la liste ; si {@code select} est fourni, sélectionne la ligne correspondante. */
    protected final void reload(Predicate<T> select) {
        ui.tasks.job(() -> {
                    prepare();
                    return service.findAll();
                })
                .busy(loading)
                .onSuccess(items -> {
                    table.setItems(items);
                    if (select != null) {
                        table.selectFirst(select);
                    }
                })
                .start();
    }

    // ---- Édition ------------------------------------------------------------------------------

    protected final void openEditor(T existing) {
        if (drawer != null && drawer.isOpen()) {
            return;
        }
        if (existing == null ? !canCreate() : !canOpen(existing)) {
            return;
        }
        EntityEditor<T> current = createEditor(existing);
        if (current == null) {
            return;
        }
        current.form().markClean();
        this.editor = current;

        Button cancel = new Button(messages.get("action.cancel"));
        cancel.getStyleClass().add("secondary");
        Button save = new Button(messages.get("action.save"), new FmsIcon(AppIcon.SAVE, 16));
        save.getStyleClass().add("primary");
        save.setDefaultButton(true);

        Drawer d = ui.drawers.create(current.title())
                .subtitle(current.subtitle())
                .content(current.form().view())
                .actions(cancel, save)
                .beforeClose(() -> confirmDiscard(current));
        save.disableProperty().bind(d.busyProperty());
        cancel.disableProperty().bind(d.busyProperty());
        cancel.setOnAction(e -> d.requestClose());
        save.setOnAction(e -> save(current, d, existing == null));
        this.drawer = d;
        d.open();
        current.form().focusFirst();
    }

    private CompletableFuture<Boolean> confirmDiscard(EntityEditor<T> current) {
        if (!current.form().isDirty()) {
            return CompletableFuture.completedFuture(true);
        }
        return ui.dialogs.confirm(messages.get("crud.discard.title"), messages.get("crud.discard.text"),
                messages.get("crud.discard.confirm"), Tone.WARNING);
    }

    private void save(EntityEditor<T> current, Drawer d, boolean creating) {
        if (!current.form().validate()) {
            return;
        }
        T entity = current.toEntity();
        ui.tasks.job(() -> service.save(entity))
                .busy(d.busyProperty())
                .onSuccess(saved -> {
                    current.form().markClean();
                    d.close();
                    ui.toast.success(savedMessage(saved, creating));
                    Object id = identityOf(saved);
                    reload(item -> identityOf(item).equals(id));
                })
                .onFailure(error -> handleSaveError(current, error))
                .start();
    }

    private void handleSaveError(EntityEditor<T> current, Throwable error) {
        if (error instanceof ValidationException ve) {
            List<FieldError> unmatched = current.form().applyErrors(ve.errors());
            if (!unmatched.isEmpty()) {
                ui.dialogs.error(messages.get("crud.invalid.title"),
                        unmatched.stream().map(messages::error).collect(Collectors.joining("\n")));
            }
        } else if (error instanceof StaleDataException) {
            ui.dialogs.error(messages.get("crud.stale.title"), messages.get("crud.stale.text"));
        } else {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Enregistrement impossible", error);
            ui.dialogs.error(messages.get("crud.failed.title"), messages.get("crud.failed.text"));
        }
    }

    /** Éditeur ouvert (pour les tests et les sous-classes). */
    protected final EntityEditor<T> currentEditor() {
        return editor;
    }

    private static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
