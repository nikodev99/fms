package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.money.CurrencyUnit;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pays, devise et fuseau horaire (instructions §19). Pris en compte au prochain démarrage.
 *
 * La devise ne peut plus changer dès qu'un montant est enregistré : les montants sont stockés en unités
 * mineures de la devise, et passer par exemple de XAF (0 décimale) à EUR (2 décimales) diviserait
 * silencieusement tous les montants par 100.
 */
@Singleton
public final class RegionalService {

    /** Devises proposées : franc CFA d'Afrique centrale et de l'Ouest, euro, dollar US. */
    public static final List<CurrencyUnit> CURRENCIES = List.of(
            CurrencyUnit.XAF,
            new CurrencyUnit("XOF", 0),
            new CurrencyUnit("EUR", 2),
            new CurrencyUnit("USD", 2)
    );

    /** Pays de la CEMAC (même devise, même cadre OHADA / SYSCOHADA). Code ISO 3166-1 alpha-2. */
    public static final List<String> COUNTRIES = List.of("CG", "CM", "GA", "TD", "CF", "GQ");

    private final SettingsStore store;
    private final Jdbc jdbc;
    private final AppSettings current;

    @Inject
    public RegionalService(SettingsStore store, Jdbc jdbc, AppSettings current) {
        this.store = store;
        this.jdbc = jdbc;
        this.current = current;
    }

    public Regional load() {
        AppSettings saved = AppSettings.load(store);
        return new Regional(store.string(AppSettings.KEY_COUNTRY, "CG"), saved.currency(), saved.zone());
    }

    /** Vrai si un montant existe déjà en base (la devise est alors figée). */
    public boolean currencyLocked() {
        return jdbc.exists("SELECT 1 FROM vehicle WHERE purchase_price IS NOT NULL OR repair_threshold IS NOT NULL"
                + " UNION ALL SELECT 1 FROM driver UNION ALL SELECT 1 FROM maintenance_event"
                + " UNION ALL SELECT 1 FROM allocation_rule WHERE amount IS NOT NULL LIMIT 1");
    }

    /** @return vrai si un redémarrage est nécessaire pour appliquer les changements */
    public boolean save(Regional r) {
        if (r.currency() == null || r.zone() == null || r.country() == null) {
            throw ValidationException.of(FieldError.of(null, "validation.required"));
        }
        Regional before = load();
        if (!r.currency().equals(before.currency()) && currencyLocked()) {
            throw ValidationException.of(FieldError.of("currency", "regional.currency.locked"));
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AppSettings.KEY_COUNTRY, r.country());
        values.put(AppSettings.KEY_CURRENCY, r.currency().code());
        values.put(AppSettings.KEY_CURRENCY_DIGITS, Integer.toString(r.currency().fractionDigits()));
        values.put(AppSettings.KEY_ZONE, r.zone().getId());
        store.putAll(values, null);
        return !r.currency().equals(current.currency()) || !r.zone().equals(current.zone());
    }

    public record Regional(String country, CurrencyUnit currency, ZoneId zone) {
    }
}
