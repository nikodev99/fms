package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.payroll.ItsSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/** Paramètres de l'ITS hors barème. Valeurs livrées par la migration V004, avec leur source légale. */
@Singleton
public final class ItsSettingsService {

    static final String ENABLED = "its.enabled";
    static final String ABATEMENT = "its.abatementBp";
    static final String MINIMUM = "its.minimumTax";
    static final String SOURCE = "its.legalSource";

    private final SettingsStore store;
    private final AppSettings settings;

    @Inject
    public ItsSettingsService(SettingsStore store, AppSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    /** Pas de valeur de repli pour l'abattement et le minimum : absents = non configurés (rien d'inventé). */
    public ItsSettings load() {
        return new ItsSettings(store.bool(ENABLED, false), store.percent(ABATEMENT, null),
                store.money(MINIMUM, settings.currency()), store.string(SOURCE, null));
    }

    public ItsSettings save(ItsSettings its) {
        ValidationException.throwIfAny(its.validate());
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ENABLED, Boolean.toString(its.enabled()));
        values.put(ABATEMENT, SettingsStore.text(its.abatement()));
        values.put(MINIMUM, SettingsStore.text(its.minimumTax()));
        values.put(SOURCE, TextRules.normalize(its.legalSource()));
        store.putAll(values, null);
        return its;
    }
}
