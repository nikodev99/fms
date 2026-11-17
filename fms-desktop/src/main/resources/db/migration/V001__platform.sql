-- FMS Desktop — socle commun
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.

-- Journal d'audit (instructions §15). Immuable : voir les déclencheurs ci-dessous.
CREATE TABLE audit_log (
                           id          INTEGER PRIMARY KEY AUTOINCREMENT,
                           occurred_at TEXT    NOT NULL,              -- instant UTC ISO-8601
                           user_name   TEXT    NOT NULL,
                           entity_type TEXT    NOT NULL,              -- ex. 'vehicle'
                           entity_id   INTEGER NOT NULL,
                           action      TEXT    NOT NULL,              -- CREATE, UPDATE…
                           changes     TEXT    NOT NULL,              -- JSON : état complet (CREATE) ou champs modifiés (UPDATE)
                           reason      TEXT                           -- motif saisi par l'utilisateur, si exigé
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE ON audit_log
BEGIN
SELECT RAISE(ABORT, 'Le journal d''audit ne peut pas être modifié');
END;

CREATE TRIGGER audit_log_no_delete
    BEFORE DELETE ON audit_log
BEGIN
SELECT RAISE(ABORT, 'Le journal d''audit ne peut pas être supprimé');
END;

-- Paramètres de l'installation (devise, fuseau, seuils…), clé → valeur texte.
CREATE TABLE app_setting (
                             key        TEXT PRIMARY KEY,
                             value      TEXT NOT NULL,
                             updated_at TEXT NOT NULL
);
