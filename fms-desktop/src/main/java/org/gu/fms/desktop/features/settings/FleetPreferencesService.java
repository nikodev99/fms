package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.fleet.FleetPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/** Valeurs par défaut et délais d'alerte de la flotte (Paramètres › Flotte). */
@Singleton
public final class FleetPreferencesService {

    static final String REPAIR_THRESHOLD = "fleet.defaultRepairThreshold";
    static final String WARNING = "fleet.repairWarningBp";
    static final String DOC_DAYS = "fleet.documentAlertDays";
    static final String MAINT_DAYS = "fleet.maintenanceAlertDays";
    static final String MAINT_KM = "fleet.maintenanceAlertKm";

    private final SettingsStore store;
    private final AppSettings settings;

    @Inject
    public FleetPreferencesService(SettingsStore store, AppSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    /** Valeurs livrées par la migration V004 ; repli identique si une clé a disparu. */
    public FleetPreferences load() {
        return new FleetPreferences(
                store.money(REPAIR_THRESHOLD, settings.currency()),
                store.percent(WARNING, Percent.of("80")),
                store.longValue(DOC_DAYS, 30),
                store.longValue(MAINT_DAYS, 15),
                store.longValue(MAINT_KM, 500));
    }

    public FleetPreferences save(FleetPreferences prefs) {
        ValidationException.throwIfAny(prefs.validate());
        Map<String, String> values = new LinkedHashMap<>();
        values.put(REPAIR_THRESHOLD, SettingsStore.text(prefs.defaultRepairThreshold()));
        values.put(WARNING, SettingsStore.text(prefs.repairWarningPercent()));
        values.put(DOC_DAYS, Long.toString(prefs.documentAlertDays()));
        values.put(MAINT_DAYS, Long.toString(prefs.maintenanceAlertDays()));
        values.put(MAINT_KM, Long.toString(prefs.maintenanceAlertKm()));
        store.putAll(values, null);
        return prefs;
    }
}
