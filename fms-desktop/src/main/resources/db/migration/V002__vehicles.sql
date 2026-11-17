-- Véhicules (instructions §4.1)
-- Montants en unités mineures (XAF : francs). Dates en texte ISO-8601.

CREATE TABLE vehicle (
                         id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                         version            INTEGER NOT NULL DEFAULT 0,
                         registration       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                         make               TEXT    NOT NULL,
                         model              TEXT    NOT NULL,
                         category           TEXT    NOT NULL
                             CHECK (category IN ('TAXI', 'PRIVATE_HIRE', 'RENTAL', 'DELIVERY', 'COMPANY', 'OTHER')),
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

CREATE INDEX idx_vehicle_status ON vehicle (status);
