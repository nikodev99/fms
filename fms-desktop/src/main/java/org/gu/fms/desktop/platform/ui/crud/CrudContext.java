package org.gu.fms.desktop.platform.ui.crud;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.overlay.Drawers;
import org.gu.fms.desktop.platform.ui.table.Tables;
import org.gu.fms.desktop.platform.ui.form.Forms;
import org.gu.fms.desktop.platform.ui.overlay.Dialogs;
import org.gu.fms.desktop.platform.ui.overlay.Toast;
import org.gu.fms.desktop.platform.ui.task.UiTasks;

/**
 * Services d'interface regroupés pour les écrans de gestion : un seul paramètre de constructeur
 * au lieu de huit, et l'ajout d'un service ne touche pas chaque écran.
 */
@Singleton
public final class CrudContext {

    final Messages messages;
    final Formats formats;
    final Forms forms;
    final Tables tables;
    final Drawers drawers;
    final Dialogs dialogs;
    final Toast toasts;
    final UiTasks tasks;

    @Inject
    public CrudContext(Messages messages, Formats formats, Forms forms, Tables tables, Drawers drawers,
                       Dialogs dialogs, Toast toasts, UiTasks tasks) {
        this.messages = messages;
        this.formats = formats;
        this.forms = forms;
        this.tables = tables;
        this.drawers = drawers;
        this.dialogs = dialogs;
        this.toasts = toasts;
        this.tasks = tasks;
    }

    public Messages messages() {
        return messages;
    }

    public Formats formats() {
        return formats;
    }

    public Forms forms() {
        return forms;
    }

    public Tables tables() {
        return tables;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public Toast toasts() {
        return toasts;
    }

    public UiTasks tasks() {
        return tasks;
    }

    /** Tiroir de saisie générique (formulaire → opération en tâche de fond). */
    public <R> FormDrawer<R> formDrawer(String title) {
        return new FormDrawer<>(this, title);
    }

    /**
     * Action sans formulaire (avoir, contre-passation, validation…) : exécutée hors du fil JavaFX ; succès → message
     * puis {@code then} ; échec → erreurs métier dans un dialogue.
     */
    public <R> void run(java.util.concurrent.Callable<R> work, java.util.function.Function<R, String> success,
                        java.util.function.Consumer<R> then) {
        tasks.job(work)
                .onSuccess(result -> {
                    String text = success == null ? null : success.apply(result);
                    if (text != null) {
                        toasts.success(text);
                    }
                    if (then != null) {
                        then.accept(result);
                    }
                })
                .onFailure(error -> ErrorReporter.report(this, error, null, "action.failed.title"))
                .start();
    }
}