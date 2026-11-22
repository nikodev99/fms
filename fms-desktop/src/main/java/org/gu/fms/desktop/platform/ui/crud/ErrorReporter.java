package org.gu.fms.desktop.platform.ui.crud;

import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Présentation homogène des échecs d'une opération : règle métier (sur les champs du formulaire si possible,
 * sinon dans un dialogue), modification concurrente, erreur technique (journalisée, message générique).
 */
final class ErrorReporter {

    private static final System.Logger LOG = System.getLogger(ErrorReporter.class.getName());

    private ErrorReporter() {
    }

    /** @param form formulaire ouvert, ou null pour une action sans formulaire */
    static void report(CrudContext ui, Throwable error, Form form, String failedTitleKey) {
        if (error instanceof ValidationException ve) {
            List<FieldError> unmatched = form == null ? ve.errors() : form.applyErrors(ve.errors());
            if (!unmatched.isEmpty()) {
                ui.dialogs.error(ui.messages.get("crud.invalid.title"),
                        unmatched.stream().map(ui.messages::error).collect(Collectors.joining("\n")));
            }
        } else if (error instanceof StaleDataException) {
            ui.dialogs.error(ui.messages.get("crud.stale.title"), ui.messages.get("crud.stale.text"));
        } else {
            LOG.log(System.Logger.Level.ERROR, "Opération impossible", error);
            ui.dialogs.error(ui.messages.get(failedTitleKey), ui.messages.get("crud.failed.text"));
        }
    }
}
