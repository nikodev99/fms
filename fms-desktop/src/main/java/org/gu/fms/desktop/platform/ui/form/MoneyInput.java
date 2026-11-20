package org.gu.fms.desktop.platform.ui.form;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Montant exact ({@link Money}, jamais de double). Saisie libre avec ou sans espaces ;
 * mise en forme « 1 250 000 » quand le champ perd le focus. La devise est affichée en suffixe.
 */
public final class MoneyInput extends FormField<Money, MoneyInput> {
    private static final Pattern ALLOWED = Pattern.compile("[0-9 \\u00A0\\u202F.,]*");

    private final Formats formats;
    private final CurrencyUnit currency;
    private final TextField field = new TextField();
    private boolean fromText;

    MoneyInput(String name, String label, Messages messages, Formats formats) {
        super(name, label, messages);
        this.formats = formats;
        this.currency = formats.currency();
        rule(Rules.notNegativeMoney());
        field.setTextFormatter(new TextFormatter<String>(c -> ALLOWED.matcher(c.getControlNewText()).matches() ? c : null));
        field.setAlignment(Pos.CENTER_RIGHT);
        field.getStyleClass().add("numeric");

        field.textProperty().addListener((obs, o, text) -> parse(text));
        valueProperty().addListener((obs, o, v) -> {
            if (!fromText) {
                field.setText(v == null ? "" : formats.amount(v));
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && inputErrorProperty().get() == null && getValue() != null) {
                String formatted = formats.amount(getValue());
                if (!formatted.equals(field.getText())) {
                    fromText = true;
                    try {
                        field.setText(formatted);
                    } finally {
                        fromText = false;
                    }
                }
            }
        });
    }

    public MoneyInput prompt(String text) {
        field.setPromptText(text);
        return this;
    }

    @Override
    protected Region buildEditor() {
        Label suffix = new Label(currency.code());
        return getRegion(suffix, field);
    }

    @Override
    protected Node focusTarget() {
        return field;
    }

    private void parse(String text) {
        fromText = true;
        try {
            Money parsed = formats.parseMoney(text, currency).orElse(null);
            inputErrorProperty().set(null);
            if (!Objects.equals(parsed, getValue())) {
                setValue(parsed);
            }
        } catch (IllegalArgumentException e) {
            inputErrorProperty().set(messages.get("validation.invalidAmount"));
            setValue(null);
        } finally {
            fromText = false;
        }
    }
}
