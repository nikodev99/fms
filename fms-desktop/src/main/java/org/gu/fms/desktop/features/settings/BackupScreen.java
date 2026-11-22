package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.gu.fms.desktop.platform.backup.BackupFile;
import org.gu.fms.desktop.platform.backup.BackupService;
import org.gu.fms.desktop.platform.backup.InvalidBackupException;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Screen;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Paramètres › Données › Sauvegarde et restauration.
 * <p>
 * Restaurer remplace TOUTES les données par celles du fichier choisi : confirmation explicite, sauvegarde de
 * sécurité automatique, puis fermeture de l'application (au redémarrage, la base restaurée est mise à niveau).
 */
public final class BackupScreen implements Screen, Screen.Embeddable {

    private final CrudContext ui;
    private final Messages messages;
    private final BackupService backups;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final Label folder = new Label();
    private final Label database = new Label();
    private final Label lastBackup = new Label();
    private final CheckBox auto = new CheckBox();
    private final ComboBox<Integer> keep = new ComboBox<>();
    private DataTable<BackupFile> table;
    private Node view;
    private boolean loadingSettings;

    @Inject
    public BackupScreen(CrudContext ui, BackupService backups) {
        this.ui = ui;
        this.messages = ui.messages();
        this.backups = backups;
    }

    @Override
    public void embed() {
        // toujours affiché dans Paramètres
    }

    @Override
    public Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    @Override
    public void onShow() {
        refresh();
    }

    private Node build() {
        Label title = new Label(messages.get("settings.backup"));
        title.getStyleClass().add("section-title");
        Label description = new Label(messages.get("backup.description"));
        description.getStyleClass().add("section-description");
        description.setWrapText(true);
        description.setMinHeight(Region.USE_PREF_SIZE);

        GridPane info = new GridPane();
        info.getStyleClass().add("info-grid");
        info.setHgap(16);
        info.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        info.getColumnConstraints().addAll(c1, c2);
        addInfo(info, 0, "backup.folder", folder);
        addInfo(info, 1, "backup.database", database);
        addInfo(info, 2, "backup.last", lastBackup);

        Button now = new Button(messages.get("backup.now"), new FmsIcon(AppIcon.BACKUP, 16));
        now.getStyleClass().add("primary");
        now.setOnAction(e -> backupNow());
        Button open = new Button(messages.get("backup.openFolder"), new FmsIcon(AppIcon.FOLDER_OPEN, 16));
        open.getStyleClass().add("secondary");
        open.setOnAction(e -> openFolder());
        Button restoreFile = new Button(messages.get("backup.restoreFile"), new FmsIcon(AppIcon.RESTORE, 16));
        restoreFile.getStyleClass().add("secondary");
        restoreFile.setOnAction(e -> chooseAndRestore(restoreFile));
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("inline-spinner");
        spinner.visibleProperty().bind(busy);
        now.disableProperty().bind(busy);
        restoreFile.disableProperty().bind(busy);
        HBox actions = new HBox(8, now, open, restoreFile, spinner);
        actions.setAlignment(Pos.CENTER_LEFT);

        auto.setText(messages.get("backup.auto"));
        keep.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(BackupService.KEEP_MIN,
                BackupService.KEEP_MAX).boxed().toList()));
        keep.getStyleClass().add("filter-combo");
        Label keepLabel = new Label(messages.get("backup.keep"));
        HBox autoRow = new HBox(12, auto, keepLabel, keep);
        autoRow.setAlignment(Pos.CENTER_LEFT);
        Label autoHelp = new Label(messages.get("backup.auto.help"));
        autoHelp.getStyleClass().add("field-message");
        autoHelp.setWrapText(true);
        autoHelp.setMinHeight(Region.USE_PREF_SIZE);
        auto.selectedProperty().addListener((obs, o, v) -> saveAuto());
        keep.valueProperty().addListener((obs, o, v) -> saveAuto());
        keep.disableProperty().bind(auto.selectedProperty().not());

        table = ui.tables().create();
        table.textColumn(messages.get("backup.column.name"), BackupFile::fileName, 320).setMinWidth(290);
        table.badgeColumn(messages.get("backup.column.kind"), b -> new Badge(messages.label(b.kind()),
                switch (b.kind()) {
                    case MANUAL -> Tone.INFO;
                    case AUTOMATIC, OTHER -> Tone.NEUTRAL;
                    case BEFORE_UPGRADE, BEFORE_RESTORE -> Tone.WARNING;
                }), 170);
        table.textColumn(messages.get("backup.column.date"), b -> ui.formats().dateTime(b.createdAt()), 150);
        table.textColumn(messages.get("backup.column.size"), b -> size(b.sizeBytes()), 100)
                .getStyleClass().add("numeric");
        table.searchBy(BackupFile::fileName);
        table.onOpen(b -> confirmRestore(b.path()));
        Label tableHint = new Label(messages.get("backup.table.hint"));
        tableHint.getStyleClass().add("field-message");
        VBox tablePanel = new VBox(table);
        tablePanel.getStyleClass().addAll("panel", "table-panel");
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tablePanel, Priority.ALWAYS);

        VBox top = new VBox(14, new VBox(4, title, description), info, actions, new VBox(4, autoRow, autoHelp));
        top.getStyleClass().addAll("panel", "settings-body");
        VBox page = new VBox(16, top, tableHint, tablePanel);
        page.getStyleClass().add("section-page");
        return page;
    }

    private void addInfo(GridPane grid, int row, String key, Label value) {
        Label label = new Label(messages.get(key));
        label.getStyleClass().add("field-label");
        value.getStyleClass().add("info-value");
        value.setWrapText(true);
        grid.add(label, 0, row);
        grid.add(value, 1, row);
    }

    private record Snapshot(List<BackupFile> files, long dbSize, boolean auto, int keep) {
    }

    private void refresh() {
        ui.tasks().job(() -> new Snapshot(backups.list(), backups.databaseSize(), backups.autoEnabled(),
                backups.autoKeep())).busy(busy).onSuccess(s -> {
            folder.setText(backups.directory().toString());
            database.setText(messages.get("backup.database.value", backups.databaseFile().getFileName(),
                    size(s.dbSize())));
            lastBackup.setText(s.files().isEmpty() ? messages.get("backup.last.none")
                    : ui.formats().dateTime(s.files().get(0).createdAt()) + " — " + messages.label(s.files().get(0).kind()));
            loadingSettings = true;
            auto.setSelected(s.auto());
            keep.setValue(s.keep());
            loadingSettings = false;
            table.setItems(s.files());
        }).start();
    }

    private void saveAuto() {
        if (loadingSettings || keep.getValue() == null) {
            return;
        }
        boolean enabled = auto.isSelected();
        int count = keep.getValue();
        ui.tasks().job(() -> {
            backups.configureAuto(enabled, count);
            return true;
        }).onSuccess(ok -> ui.toasts().success(messages.get("backup.auto.saved"))).start();
    }

    private void backupNow() {
        ui.tasks().job(backups::createManual).busy(busy).onSuccess(b -> {
            ui.toasts().success(messages.get("backup.created", b.fileName()));
            refresh();
        }).start();
    }

    private void openFolder() {
        ui.tasks().job(() -> {
            File dir = backups.directory().toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                return false;
            }
            Desktop.getDesktop().open(dir);
            return true;
        }).onSuccess(opened -> {
            if (!opened) {
                ui.dialogs().alert(Tone.INFO, messages.get("backup.folder"), backups.directory().toString());
            }
        }).start();
    }

    private void chooseAndRestore(Node owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("backup.restoreFile"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("backup.filter"), "*.db"));
        File dir = backups.directory().toFile();
        if (dir.isDirectory()) {
            chooser.setInitialDirectory(dir);
        }
        File file = chooser.showOpenDialog(owner.getScene().getWindow());
        if (file != null) {
            confirmRestore(file.toPath());
        }
    }

    private void confirmRestore(Path file) {
        ui.tasks().job(() -> backups.verify(file)).busy(busy).onSuccess(version ->
                        ui.dialogs().confirm(messages.get("backup.restore.title"),
                                messages.get("backup.restore.text", file.getFileName(), version),
                                messages.get("backup.restore.confirm"), Tone.DANGER).thenAccept(ok -> {
                            if (ok) {
                                restore(file);
                            }
                        }))
                .onFailure(this::showError).start();
    }

    private void restore(Path file) {
        ui.tasks().job(() -> backups.restore(file)).busy(busy).onSuccess(safety ->
                        ui.dialogs().alert(Tone.SUCCESS, messages.get("backup.restored.title"),
                                messages.get("backup.restored.text", safety.fileName())).thenAccept(x -> Platform.exit()))
                .onFailure(this::showError).start();
    }

    private void showError(Throwable error) {
        if (error instanceof InvalidBackupException invalid) {
            ui.dialogs().error(messages.get("backup.invalid.title"), messages.get(invalid.messageKey()));
        } else {
            System.getLogger(BackupScreen.class.getName()).log(System.Logger.Level.ERROR, "Sauvegarde", error);
            ui.dialogs().error(messages.get("backup.failed.title"), String.valueOf(error.getMessage()));
        }
    }

    private String size(long bytes) {
        if (bytes < 0) {
            return "—";
        }
        if (bytes < 1024 * 1024) {
            return messages.get("unit.kb", Math.max(1, Math.round(bytes / 1024.0)));
        }
        return messages.get("unit.mb", Math.round(bytes / (1024.0 * 102.4)) / 10.0);
    }
}
