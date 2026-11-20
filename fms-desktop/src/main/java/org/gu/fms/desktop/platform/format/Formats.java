package org.gu.fms.desktop.platform.format;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.money.CurrencyUnit;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formats d'affichage et de saisie, selon les paramètres régionaux.
 * Un seul endroit pour les montants, nombres et dates : tous les écrans affichent pareil.
 * <p>
 * fr-FR : « 1 250 000 XAF », « 05/11/2026 ». Le séparateur de milliers est une espace insécable fine,
 * jamais coupée en fin de ligne.
 */
@Singleton
public final class Formats {

    private static final Pattern ALL_SPACES = Pattern.compile("[\\s\\u00A0\\u202F]");

    private final AppSettings settings;
    private final DateTimeFormatter date;
    private final DateTimeFormatter dateTime;

    @Inject
    public Formats(AppSettings settings) {
        this.settings = settings;
        this.date = DateTimeFormatter.ofPattern("dd/MM/uuuu", settings.locale()).withResolverStyle(ResolverStyle.STRICT);
        this.dateTime = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", settings.locale()).withZone(settings.zone());
    }

    public CurrencyUnit currency() {
        return settings.currency();
    }

    /** « 1 250 000 XAF » ; vide si null. */
    public String money(Money value) {
        return value == null ? "" : amount(value) + " " + value.currency().code();
    }

    /** « 1 250 000 » (sans devise) ; vide si null. */
    public String amount(Money value) {
        return value == null ? "" : decimal(value.currency().fractionDigits()).format(value.toMajor());
    }

    /** « 10,03 % » (espace insécable avant %) ; vide si null. */
    public String percent(Percent value) {
        if (value == null) {
            return "";
        }
        DecimalFormat f = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(settings.locale()));
        return f.format(value.value()) + "\u00A0%";
    }

    public String number(Long value) {
        return value == null ? "" : decimal(0).format(value);
    }

    public String date(LocalDate value) {
        return value == null ? "" : date.format(value);
    }

    public String dateTime(Instant value) {
        return value == null ? "" : dateTime.format(value);
    }

    public DateTimeFormatter dateFormatter() {
        return date;
    }

    /**
     * Lit un montant saisi : espaces ignorés, virgule ou point décimal admis selon la devise.
     * Vide → Optional.empty() ; texte invalide ou trop précis → {@link IllegalArgumentException}.
     */
    public Optional<Money> parseMoney(String text, CurrencyUnit currency) {
        String cleaned = text == null ? "" : ALL_SPACES.matcher(text).replaceAll("").replace(',', '.');
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        if (!cleaned.matches("-?\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("Montant invalide : " + text);
        }
        return Optional.of(Money.ofMajor(new BigDecimal(cleaned), currency));
    }

    /** Lit un entier saisi (« 125 000 » → 125000). */
    public Optional<Long> parseLong(String text) {
        String cleaned = text == null ? "" : ALL_SPACES.matcher(text).replaceAll("");
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        if (!cleaned.matches("-?\\d{1,18}")) {
            throw new IllegalArgumentException("Nombre invalide : " + text);
        }
        return Optional.of(Long.parseLong(cleaned));
    }

    public Optional<LocalDate> parseDate(String text) {
        String cleaned = text == null ? "" : text.strip();
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(cleaned, date));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date invalide : " + text, e);
        }
    }

    private DecimalFormat decimal(int fractionDigits) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(settings.locale()));
        f.setMinimumFractionDigits(fractionDigits);
        f.setMaximumFractionDigits(fractionDigits);
        return f;
    }
}
