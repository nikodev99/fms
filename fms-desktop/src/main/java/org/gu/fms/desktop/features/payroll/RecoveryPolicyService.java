package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.payroll.RecoveryPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Politique de retenues sur salaire proposées à la paie (paramètres, migration V007). */
@Singleton
public final class RecoveryPolicyService {

    static final String ADVANCES = "payroll.recoverAdvances";
    static final String SHORTAGES = "payroll.recoverShortages";
    static final String OTHER = "payroll.recoverOtherDebts";
    static final String CAP = "payroll.debtCapBp";

    private final SettingsStore store;

    @Inject
    public RecoveryPolicyService(SettingsStore store) {
        this.store = store;
    }

    public RecoveryPolicy load() {
        return new RecoveryPolicy(store.bool(ADVANCES, true), store.bool(SHORTAGES, true), store.bool(OTHER, true),
                store.percent(CAP, Percent.HUNDRED));
    }

    public RecoveryPolicy save(RecoveryPolicy policy) {
        if (policy.debtCap() == null) {
            throw ValidationException.of(FieldError.of("debtCap", "validation.required"));
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ADVANCES, Boolean.toString(policy.recoverAdvances()));
        values.put(SHORTAGES, Boolean.toString(policy.recoverShortages()));
        values.put(OTHER, Boolean.toString(policy.recoverOtherDebts()));
        values.put(CAP, SettingsStore.text(policy.debtCap()));
        store.putAll(values, null);
        return policy;
    }
}
