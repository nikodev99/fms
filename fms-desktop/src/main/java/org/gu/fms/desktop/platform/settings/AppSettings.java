package org.gu.fms.desktop.platform.settings;

import org.gu.fms.domain.money.CurrencyUnit;

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

    public AppSettings {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(zone, "zone");
    }
}
