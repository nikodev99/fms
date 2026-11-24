package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.common.ValidationException;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Jours de repos, objectif de recouvrement et seuil rouge (Paramètres › Versements et recouvrement). */
@Singleton
public final class CollectionPolicyService {

    static final String REST_DAYS = "fleet.restDays";
    static final String OBJECTIVE = "collection.objectiveBp";
    static final String RED = "collection.redThresholdBp";

    private final SettingsStore store;

    @Inject
    public CollectionPolicyService(SettingsStore store) {
        this.store = store;
    }

    public CollectionPolicy load() {
        return new CollectionPolicy(parseDays(store.string(REST_DAYS, "7")),
                store.percent(OBJECTIVE, CollectionPolicy.DEFAULT.objective()),
                store.percent(RED, CollectionPolicy.DEFAULT.redThreshold()));
    }

    public CollectionPolicy save(CollectionPolicy policy) {
        ValidationException.throwIfAny(policy.validate());
        Map<String, String> values = new LinkedHashMap<>();
        values.put(REST_DAYS, policy.restDays().stream().sorted().map(d -> Integer.toString(d.getValue()))
                .collect(Collectors.joining(",")));
        values.put(OBJECTIVE, SettingsStore.text(policy.objective()));
        values.put(RED, SettingsStore.text(policy.redThreshold()));
        store.putAll(values, null);
        return policy;
    }

    static Set<DayOfWeek> parseDays(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (text == null || text.isBlank()) {
            return days;
        }
        Arrays.stream(text.split(",")).map(String::strip).filter(s -> s.matches("[1-7]"))
                .forEach(s -> days.add(DayOfWeek.of(Integer.parseInt(s))));
        return days;
    }
}
