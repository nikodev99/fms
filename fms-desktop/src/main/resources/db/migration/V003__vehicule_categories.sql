-- Catégories de véhicules définies par l'utilisateur (instructions §1 et §4.1)
-- Remplace l'énumération figée de V002. SQLite ne sait pas modifier une contrainte CHECK :
-- la table vehicle est reconstruite, avec reprise intégrale des données (mêmes identifiants).
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.

CREATE TABLE vehicle_category (
                                  id          INTEGER PRIMARY KEY AUTOINCREMENT,
                                  version     INTEGER NOT NULL DEFAULT 0,
                                  name        TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                                  sort_order  INTEGER NOT NULL DEFAULT 0 CHECK (sort_order BETWEEN 0 AND 999),
                                  active      INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                                  is_system   INTEGER NOT NULL DEFAULT 0 CHECK (is_system IN (0, 1)),
                                  legacy_code TEXT    UNIQUE,              -- ancienne valeur de l'énumération (reprise des données uniquement)
                                  created_at  TEXT    NOT NULL,
                                  updated_at  TEXT    NOT NULL
);

INSERT INTO vehicle_category (id, name, sort_order, is_system, legacy_code, created_at, updated_at) VALUES
    (1, 'Taxi',                10, 1, 'TAXI',         strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    (2, 'VTC',                 20, 1, 'PRIVATE_HIRE', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    (3, 'Location',            30, 1, 'RENTAL',       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    (4, 'Livraison',           40, 1, 'DELIVERY',     strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    (5, 'Véhicule de société', 50, 1, 'COMPANY',      strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    (6, 'Autre',               90, 1, 'OTHER',        strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

CREATE TABLE vehicle_new (
                             id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                             version            INTEGER NOT NULL DEFAULT 0,
                             registration       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                             make               TEXT    NOT NULL,
                             model              TEXT    NOT NULL,
                             category_id        INTEGER NOT NULL REFERENCES vehicle_category (id),
                             status             TEXT    NOT NULL
                                 CHECK (status IN ('ACTIVE', 'UNDER_MAINTENANCE', 'TEMPORARILY_UNAVAILABLE', 'IMMOBILISED', 'SOLD', 'INACTIVE')),
                             acquisition_date   TEXT,
                             acquisition_method TEXT
                                 CHECK (acquisition_method IS NULL OR acquisition_method IN ('CASH', 'LOAN', 'OTHER')),
                             purchase_price     INTEGER CHECK (purchase_price IS NULL OR purchase_price >= 0),
                             current_mileage    INTEGER CHECK (current_mileage IS NULL OR current_mileage >= 0),
                             repair_threshold   INTEGER CHECK (repair_threshold IS NULL OR repair_threshold >= 0),
                             notes              TEXT,
                             created_at         TEXT    NOT NULL,
                             updated_at         TEXT    NOT NULL
);

-- LEFT JOIN + category_id NOT NULL : une catégorie non reconnue ferait échouer toute la migration
-- (rien n'est perdu en silence ; la base reste en V002 et la sauvegarde automatique existe).
INSERT INTO vehicle_new (id, version, registration, make, model, category_id, status, acquisition_date,
                         acquisition_method, purchase_price, current_mileage, repair_threshold, notes,
                         created_at, updated_at)
SELECT v.id, v.version, v.registration, v.make, v.model, c.id, v.status, v.acquisition_date,
       v.acquisition_method, v.purchase_price, v.current_mileage, v.repair_threshold, v.notes,
       v.created_at, v.updated_at
FROM vehicle v
         LEFT JOIN vehicle_category c ON c.legacy_code = v.category;

DROP TABLE vehicle;
ALTER TABLE vehicle_new RENAME TO vehicle;

CREATE INDEX idx_vehicle_status ON vehicle (status);
CREATE INDEX idx_vehicle_category ON vehicle (category_id);
