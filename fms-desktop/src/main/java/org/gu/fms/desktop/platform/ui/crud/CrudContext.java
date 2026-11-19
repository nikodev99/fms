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

@Singleton
public final class CrudContext {
    final Messages messages;
    final Formats formats;
    final Forms forms;
    final Tables tables;
    final Drawers drawers;
    final Dialogs dialogs;
    final Toast toast;
    final UiTasks tasks;

    @Inject
    public CrudContext(Messages messages, Formats formats, Forms forms, Tables tables, Drawers drawers, Dialogs dialogs, Toast toast, UiTasks tasks) {
        this.messages = messages;
        this.formats = formats;
        this.forms = forms;
        this.tables = tables;
        this.drawers = drawers;
        this.dialogs = dialogs;
        this.toast = toast;
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

    public Dialogs dialogs() {
        return dialogs;
    }

    public Toast toast() {
        return toast;
    }
}
