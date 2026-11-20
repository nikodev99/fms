package org.gu.fms.desktop.platform.format;

import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatsTest {

    private final Formats formats = new Formats(AppSettings.CONGO);

    /** Retire les espaces (le séparateur de milliers fr-FR est U+202F selon la version du JDK). */
    private static String plain(String s) {
        return s.replaceAll("[\\s\\u00A0\\u202F]", "_");
    }

    @Test
    void formatsMoneyWithGroupingAndCurrency() {
        assertEquals("1_250_000_XAF", plain(formats.money(Money.of(1_250_000, CurrencyUnit.XAF))));
        assertEquals("0_XAF", plain(formats.money(Money.zero(CurrencyUnit.XAF))));
        assertEquals("", formats.money(null));
    }

    @Test
    void parsesMoneyTypedWithAnySpaces() {
        assertEquals(Optional.of(Money.of(1_250_000, CurrencyUnit.XAF)), formats.parseMoney("1 250 000", CurrencyUnit.XAF));
        assertEquals(Optional.empty(), formats.parseMoney("   ", CurrencyUnit.XAF));
        assertThrows(IllegalArgumentException.class, () -> formats.parseMoney("12,5", CurrencyUnit.XAF));
        assertThrows(IllegalArgumentException.class, () -> formats.parseMoney("12a", CurrencyUnit.XAF));
        assertEquals(Optional.of(Money.of(1250, new CurrencyUnit("EUR", 2))), formats.parseMoney("12,50", new CurrencyUnit("EUR", 2)));
    }

    @Test
    void datesUseDayMonthYearStrictly() {
        assertEquals("05/11/2026", formats.date(LocalDate.of(2026, 11, 5)));
        assertEquals(Optional.of(LocalDate.of(2026, 2, 28)), formats.parseDate("28/02/2026"));
        assertThrows(IllegalArgumentException.class, () -> formats.parseDate("30/02/2026"));
    }

    @Test
    void parsesLongs() {
        assertEquals(Optional.of(125_000L), formats.parseLong("125 000"));
        assertThrows(IllegalArgumentException.class, () -> formats.parseLong("12.5"));
    }
}
