package org.gu.fms.desktop.platform.ui.filter;

import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Choix d'un mois (filtre de période : transactions, versements, paie). Du plus récent au plus ancien. */
public final class MonthPicker extends ComboBox<YearMonth> {

    public MonthPicker(YearMonth current, int monthsBack, Locale locale) {
        List<YearMonth> months = new ArrayList<>();
        for (int i = 0; i <= monthsBack; i++) {
            months.add(current.minusMonths(i));
        }
        setItems(FXCollections.observableArrayList(months));
        setConverter(new StringConverter<>() {
            @Override
            public String toString(YearMonth m) {
                if (m == null) {
                    return "";
                }
                String name = m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale);
                return name.substring(0, 1).toUpperCase(locale) + name.substring(1) + " " + m.getYear();
            }

            @Override
            public YearMonth fromString(String text) {
                return text == null || text.isBlank() ? null : YearMonth.parse(text, DateTimeFormatter.ofPattern("yyyy-MM"));
            }
        });
        setValue(current);
        getStyleClass().add("filter-month");
    }

    public ObjectProperty<YearMonth> monthProperty() {
        return valueProperty();
    }
}
