package org.gu.fms.desktop.platform.audit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.session.UserSession;

import java.time.Clock;
import java.util.*;

/**
 * Journal d'audit (instructions §15). Les lignes sont immuables : des déclencheurs SQL refusent
 * toute modification ou suppression de la table {@code audit_log}.
 * <p>
 * À appeler dans la même transaction que l'écriture tracée : si l'écriture échoue,
 * la trace disparaît avec elle, et inversement.
 */
@Singleton
public final class AuditLog {

    private final Jdbc jdbc;
    private final Clock clock;
    private final UserSession session;

    @Inject
    public AuditLog(Jdbc jdbc, Clock clock, UserSession session) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.session = session;
    }

    /** Création : enregistre l'état complet. */
    public void created(String entityType, long entityId, Map<String, Object> snapshot) {
        write(entityType, entityId, AuditAction.CREATE, Json.write(snapshot), null);
    }

    /**
     * Modification : enregistre seulement les champs modifiés, sous la forme {"champ":{"from":…,"to":…}}.
     * Rien n'est écrit si aucun champ n'a changé.
     */
    public void updated(String entityType, long entityId, Map<String, Object> before, Map<String, Object> after,
                        String reason) {
        Map<String, Object> diff = diff(before, after);
        if (!diff.isEmpty()) {
            write(entityType, entityId, AuditAction.UPDATE, Json.write(diff), reason);
        }
    }

    /** Opération métier (contre-passation, clôture, validation…) avec ses éléments et le motif éventuel. */
    public void event(String entityType, long entityId, AuditAction action, Map<String, Object> details,
                      String reason) {
        write(entityType, entityId, action, Json.write(details), reason);
    }

    static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String key : keys) {
            Object from = before.get(key);
            Object to = after.get(key);
            if (!Objects.equals(from, to)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", from);
                change.put("to", to);
                diff.put(key, change);
            }
        }
        return diff;
    }

    private void write(String entityType, long entityId, AuditAction action, String changes, String reason) {
        jdbc.update("INSERT INTO audit_log (occurred_at, user_name, entity_type, entity_id, action, changes, reason)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                clock.instant(), session.currentUser().displayName(), entityType, entityId, action, changes, reason);
    }
}