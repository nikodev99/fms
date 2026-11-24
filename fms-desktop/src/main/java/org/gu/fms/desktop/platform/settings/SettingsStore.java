package org.gu.fms.desktop.platform.settings;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Paramètres simples de l'installation (table {@code app_setting}, clé → texte).
 *
 * Écriture groupée et tracée : {@link #putAll(Map, String)} enregistre plusieurs clés dans une seule
 * transaction avec une trace d'audit des valeurs modifiées (type « setting »). Une valeur {@code null}
 * supprime la clé (retour à « non renseigné »).
 */
@Singleton
public final class SettingsStore {

    static final String AUDIT_TYPE = "setting";

    private final Jdbc jdbc;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;

    @Inject
    public SettingsStore(Jdbc jdbc, TransactionManager tx, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    public Optional<String> get(String key) {
        return jdbc.queryOne("SELECT value FROM app_setting WHERE key = ?", row -> row.string("value"), key);
    }

    /** Toutes les clés qui commencent par {@code prefix} (ex. "company."). */
    public Map<String, String> byPrefix(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT key, value FROM app_setting WHERE key LIKE ? ESCAPE '\\' ORDER BY key",
                row -> result.put(row.string("key"), row.string("value")),
                prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return result;
    }

    public String string(String key, String fallback) {
        return get(key).orElse(fallback);
    }

    public long longValue(String key, long fallback) {
        return get(key).map(Long::parseLong).orElse(fallback);
    }

    public boolean bool(String key, boolean fallback) {
        return get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    public Money money(String key, CurrencyUnit currency) {
        return get(key).map(v -> Money.of(Long.parseLong(v), currency)).orElse(null);
    }

    public Percent percent(String key, Percent fallback) {
        return get(key).map(v -> Percent.ofBasisPoints(Long.parseLong(v))).orElse(fallback);
    }

    /** Écrit les valeurs (texte brut ; null = supprimer) et trace les différences. */
    public void putAll(Map<String, String> values, String reason) {
        tx.runInTransaction(c -> {
            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : values.entrySet()) {
                before.put(e.getKey(), get(e.getKey()).orElse(null));
                after.put(e.getKey(), e.getValue());
                if (e.getValue() == null) {
                    jdbc.update("DELETE FROM app_setting WHERE key = ?", e.getKey());
                } else {
                    jdbc.update("INSERT INTO app_setting (key, value, updated_at) VALUES (?, ?, ?)"
                                    + " ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
                            e.getKey(), e.getValue(), clock.instant());
                }
            }
            audit.updated(AUDIT_TYPE, 0, before, after, reason);
        });
    }

    /**
     * Écrit des valeurs SANS en tracer le contenu (empreintes de la clé de récupération) : seul le fait est tracé par
     * l'appelant.
     */
    public void putAllSilently(Map<String, String> values) {
        tx.runInTransaction(c -> {
            for (Map.Entry<String, String> e : values.entrySet()) {
                jdbc.update("INSERT INTO app_setting (key, value, updated_at) VALUES (?, ?, ?)"
                                + " ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
                        e.getKey(), e.getValue(), clock.instant());
            }
        });
    }

    /** Conversions vers le texte stocké. */
    public static String text(Money value) {
        return value == null ? null : Long.toString(value.minor());
    }

    public static String text(Percent value) {
        return value == null ? null : Long.toString(value.basisPoints());
    }
}