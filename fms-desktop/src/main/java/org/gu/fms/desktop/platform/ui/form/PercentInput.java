package org.gu.fms.desktop.platform.ui.form;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.domain.common.Percent;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pourcentage exact ({@link Percent}, 0 à 100, deux décimales au plus). Virgule ou point acceptés ;
 * affichage « 10,03 » avec le suffixe « % ».
 */
public final class PercentInput extends FormField<Percent, PercentInput> {

    private static final Pattern ALLOWED = Pattern.compile("[0-9]{0,3}([.,][0-9]{0,2})?");

    private final TextField field = new TextField();
    private final DecimalFormat display;
    private boolean fromText;

    PercentInput(String name, String label, Messages messages) {
        super(name, label, messages);
        display = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(messages.bundle().getLocale()));
        field.setTextFormatter(new TextFormatter<String>(c -> ALLOWED.matcher(c.getControlNewText()).matches() ? c : null));
        field.setAlignment(Pos.CENTER_RIGHT);
        field.getStyleClass().add("numeric");
        field.textProperty().addListener((obs, o, text) -> parse(text));
        valueProperty().addListener((obs, o, v) -> {
            if (!fromText) {
                field.setText(v == null ? "" : display.format(v.value()));
            }
        });
    }

    private void parse(String text) {
        fromText = true;
        try {
            String cleaned = text == null ? "" : text.strip().replace(',', '.');
            Percent parsed = cleaned.isEmpty() || cleaned.equals(".") ? null : new Percent(new BigDecimal(cleaned));
            inputErrorProperty().set(null);
            if (!Objects.equals(parsed, getValue())) {
                setValue(parsed);
            }
        } catch (IllegalArgumentException e) {
            inputErrorProperty().set(messages.get("validation.invalidPercent"));
            setValue(null);
        } finally {
            fromText = false;
        }
    }

    @Override
    protected Region buildEditor() {
        Label suffix = new Label("%");
        return getRegion(suffix, field);
    }

    @Override
    protected Node focusTarget() {
        return field;
    }
}
