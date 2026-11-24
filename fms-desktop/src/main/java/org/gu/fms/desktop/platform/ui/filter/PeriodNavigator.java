package org.gu.fms.desktop.platform.ui.filter;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

/**
 * Navigation par mois : ‹ Octobre 2026 › , saisie directe d'une année et d'un mois, retour au mois en cours, et en
 * option « Depuis le début » (tout l'historique). La valeur est le mois affiché ; {@code allTime} vrai = tout.
 */
public final class PeriodNavigator extends HBox {

    private final ObjectProperty<YearMonth> month = new SimpleObjectProperty<>(this, "month");
    private final ObjectProperty<Boolean> allTime = new SimpleObjectProperty<>(this, "allTime", false);
    private final Locale locale;
    private final Label label = new Label();
    private final TextField year = new TextField();
    private final ComboBox<Month> monthBox = new ComboBox<>();
    private final YearMonth current;
    private boolean syncing;

    public PeriodNavigator(Messages messages, YearMonth current, boolean withAllTime) {
        this.locale = messages.locale();
        this.current = current;
        getStyleClass().add("period-nav");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);

        Button previous = iconButton(AppIcon.CHEVRON_LEFT, messages.get("period.previous"));
        Button next = iconButton(AppIcon.CHEVRON_RIGHT, messages.get("period.next"));
        label.getStyleClass().add("period-label");
        label.setMinWidth(130);
        label.setAlignment(Pos.CENTER);
        previous.setOnAction(e -> shift(-1));
        next.setOnAction(e -> shift(1));

        year.setPromptText(messages.get("period.year"));
        year.setPrefColumnCount(4);
        year.getStyleClass().add("period-year");
        monthBox.getItems().addAll(Month.values());
        monthBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Month m) {
                return m == null ? "" : capitalize(m.getDisplayName(TextStyle.FULL_STANDALONE, locale));
            }

            @Override
            public Month fromString(String s) {
                return null;
            }
        });
        monthBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Month m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? messages.get("period.month") : monthBox.getConverter().toString(m));
            }
        });
        Button go = new Button(messages.get("period.go"));
        go.getStyleClass().add("secondary");
        go.setOnAction(e -> jump());
        year.setOnAction(e -> jump());
        Button today = new Button(messages.get("period.current"));
        today.getStyleClass().add("secondary");
        today.setOnAction(e -> {
            allTime.set(false);
            month.set(current);
        });

        getChildren().addAll(previous, label, next, new Label(" "), year, monthBox, go, today);
        if (withAllTime) {
            ToggleButton all = new ToggleButton(messages.get("period.allTime"));
            all.getStyleClass().add("period-all");
            all.selectedProperty().addListener((obs, o, sel) -> allTime.set(sel));
            allTime.addListener((obs, o, v) -> {
                all.setSelected(Boolean.TRUE.equals(v));
                refresh();
            });
            getChildren().add(all);
        }
        month.addListener((obs, o, m) -> refresh());
        month.set(current);
    }

    private Button iconButton(AppIcon icon, String tooltip) {
        Button b = new Button(null, new FmsIcon(icon, 18));
        b.getStyleClass().addAll("icon-button", "period-step");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    private void shift(int months) {
        allTime.set(false);
        month.set(month.get().plusMonths(months));
    }

    private void jump() {
        Integer y = parseYear();
        Month m = monthBox.getValue();
        YearMonth base = month.get() == null ? current : month.get();
        allTime.set(false);
        month.set(YearMonth.of(y == null ? base.getYear() : y, m == null ? base.getMonth() : m));
    }

    private Integer parseYear() {
        String text = year.getText() == null ? "" : year.getText().strip();
        if (!text.matches("\\d{4}")) {
            return null;
        }
        int y = Integer.parseInt(text);
        return y < 2000 || y > 2100 ? null : y;
    }

    private void refresh() {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            YearMonth m = month.get();
            label.setText(Boolean.TRUE.equals(allTime.get()) ? "—" : format(m));
        } finally {
            syncing = false;
        }
    }

    public String format(YearMonth m) {
        return m == null ? "" : capitalize(m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale)) + " " + m.getYear();
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(locale) + s.substring(1);
    }

    public ObjectProperty<YearMonth> monthProperty() {
        return month;
    }

    public ObjectProperty<Boolean> allTimeProperty() {
        return allTime;
    }
}
