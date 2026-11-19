package org.gu.fms.desktop.platform.ui.form;

import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.time.LocalDate;
import java.util.Objects;

/** Date (jj/mm/aaaa), saisie au clavier ou par le calendrier. */
public final class DateInput extends FormField<LocalDate, DateInput> {
    private final Formats formats;
    private final DatePicker picker = new DatePicker();

    DateInput(String name, String label, Messages messages, Formats formats) {
        super(name, label, messages);
        this.formats = formats;
        picker.setShowWeekNumbers(false);
        picker.setPromptText(messages.get("common.datePrompt"));
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return formats.date(date);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return formats.parseDate(text).orElse(null);
                } catch (IllegalArgumentException e) {
                    return null;   // l'erreur est signalée par le suivi du texte ci-dessous
                }
            }
        });
        picker.getEditor().textProperty().addListener((obs, o, text) -> parse(text));
        picker.valueProperty().addListener((obs, o, v) -> {
            if (!Objects.equals(v, getValue())) {
                setValue(v);
            }
        });
        valueProperty().addListener((obs, o, v) -> {
            if (!Objects.equals(v, picker.getValue())) {
                picker.setValue(v);
            }
        });
    }

    @Override
    protected Region buildEditor() {
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    @Override
    protected Node focusTarget() {
        return picker;
    }

    private void parse(String text) {
        try {
            LocalDate parsed = formats.parseDate(text).orElse(null);
            inputErrorProperty().set(null);
            if (!Objects.equals(parsed, getValue())) {
                setValue(parsed);
            }
        } catch (IllegalArgumentException e) {
            inputErrorProperty().set(messages.get("validation.invalidDate"));
        }
    }
}
