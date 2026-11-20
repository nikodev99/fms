package org.gu.fms.desktop.platform.settings;

import org.gu.fms.domain.money.CurrencyUnit;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Paramètres régionaux de l'installation (instructions §19).
 * <p>
 * Valeurs par défaut : République du Congo. Elles deviendront modifiables dans l'écran Paramètres
 * (table app_setting) ; les autres modules lisent toujours ces valeurs ici, jamais en dur.
 */
public record AppSettings(
        CurrencyUnit currency,
        Locale locale,
        ZoneId zone
) {
    public static final AppSettings CONGO = new AppSettings(CurrencyUnit.XAF, Locale.FRANCE, ZoneId.of("Africa/Brazzaville"));

    public static final String KEY_COUNTRY = "regional.country";
    public static final String KEY_CURRENCY = "regional.currency";
    public static final String KEY_CURRENCY_DIGITS = "regional.currencyDigits";
    public static final String KEY_ZONE = "regional.zone";

    public AppSettings {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(zone, "zone");
    }

    /**
     * Lecture depuis la base ; valeurs du Congo pour toute clé absente ou illisible (une valeur corrompue
     * ne doit pas empêcher l'application de démarrer). Langue : français, seule langue livrée.
     */
    public static AppSettings load(SettingsStore store) {
        CurrencyUnit currency = CONGO.currency();
        ZoneId zone = CONGO.zone();
        try {
            currency = new CurrencyUnit(store.string(KEY_CURRENCY, CONGO.currency().code()),
                    (int) store.longValue(KEY_CURRENCY_DIGITS, CONGO.currency().fractionDigits()));
        } catch (IllegalArgumentException e) {
            System.getLogger(AppSettings.class.getName()).log(System.Logger.Level.WARNING, "Devise illisible", e);
        }
        try {
            zone = ZoneId.of(store.string(KEY_ZONE, CONGO.zone().getId()));
        } catch (DateTimeException e) {
            System.getLogger(AppSettings.class.getName()).log(System.Logger.Level.WARNING, "Fuseau illisible", e);
        }
        return new AppSettings(currency, Locale.FRANCE, zone);
    }
}
