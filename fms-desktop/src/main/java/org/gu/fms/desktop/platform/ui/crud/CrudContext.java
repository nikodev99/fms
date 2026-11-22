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
}
