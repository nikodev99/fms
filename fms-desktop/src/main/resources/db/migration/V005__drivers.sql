-- Chauffeurs et affectations (instructions §4.2, accord de rémunération §4.7)
-- Seul l'ACCORD est stocké ici ; soldes (avances, manquants, dettes, caution versée) viendront des mouvements.
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.

CREATE TABLE driver (
                        id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                        version            INTEGER NOT NULL DEFAULT 0,
                        last_name          TEXT    NOT NULL,
                        first_name         TEXT    NOT NULL,
                        phone              TEXT    NOT NULL,
                        address            TEXT,
                        id_number          TEXT,
                        licence_number     TEXT    COLLATE NOCASE,
                        licence_expiry     TEXT,
                        hire_date          TEXT,
                        status             TEXT    NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LEFT')),
                        pay_scheme         TEXT    NOT NULL CHECK (pay_scheme IN ('FIXED', 'PERCENTAGE', 'FIXED_PLUS_COMMISSION')),
                        fixed_salary       INTEGER CHECK (fixed_salary IS NULL OR fixed_salary > 0),
                        commission_bp      INTEGER CHECK (commission_bp IS NULL OR commission_bp BETWEEN 1 AND 10000),
                        daily_target       INTEGER CHECK (daily_target IS NULL OR daily_target >= 0),
                        deposit_agreed     INTEGER CHECK (deposit_agreed IS NULL OR deposit_agreed >= 0),
                        cnss_subject       INTEGER NOT NULL DEFAULT 0 CHECK (cnss_subject IN (0, 1)),
                        cnss_number        TEXT,
                        marital_status     TEXT    CHECK (marital_status IS NULL OR marital_status IN ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED')),
                        dependent_children INTEGER CHECK (dependent_children IS NULL OR dependent_children BETWEEN 0 AND 30),
                        notes              TEXT,
                        created_at         TEXT    NOT NULL,
                        updated_at         TEXT    NOT NULL,
                        CHECK ((pay_scheme = 'FIXED' AND fixed_salary IS NOT NULL AND commission_bp IS NULL)
                            OR (pay_scheme = 'PERCENTAGE' AND commission_bp IS NOT NULL AND fixed_salary IS NULL)
                            OR (pay_scheme = 'FIXED_PLUS_COMMISSION' AND fixed_salary IS NOT NULL AND commission_bp IS NOT NULL))
);

CREATE UNIQUE INDEX ux_driver_licence ON driver (licence_number) WHERE licence_number IS NOT NULL;
CREATE INDEX idx_driver_status ON driver (status);

CREATE TRIGGER driver_no_delete
    BEFORE DELETE ON driver
BEGIN
SELECT RAISE(ABORT, 'Un chauffeur ne peut pas être supprimé : passer son statut à « Parti »');
END;

CREATE TABLE driver_assignment (
                                   id         INTEGER PRIMARY KEY AUTOINCREMENT,
                                   version    INTEGER NOT NULL DEFAULT 0,
                                   driver_id  INTEGER NOT NULL REFERENCES driver (id),
                                   vehicle_id INTEGER NOT NULL REFERENCES vehicle (id),
                                   role       TEXT    NOT NULL CHECK (role IN ('PRIMARY', 'REPLACEMENT', 'NIGHT')),
                                   start_date TEXT    NOT NULL,
                                   end_date   TEXT,
                                   notes      TEXT,
                                   created_at TEXT    NOT NULL,
                                   updated_at TEXT    NOT NULL,
                                   CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_assignment_vehicle ON driver_assignment (vehicle_id, start_date);
CREATE INDEX idx_assignment_driver ON driver_assignment (driver_id, start_date);

-- Filets de sécurité (la règle complète des chevauchements est vérifiée par le service) :
-- un seul principal SANS date de fin par véhicule, et par chauffeur.
CREATE UNIQUE INDEX ux_assignment_open_primary_vehicle ON driver_assignment (vehicle_id)
    WHERE role = 'PRIMARY' AND end_date IS NULL;
CREATE UNIQUE INDEX ux_assignment_open_primary_driver ON driver_assignment (driver_id)
    WHERE role = 'PRIMARY' AND end_date IS NULL;

CREATE TRIGGER driver_assignment_fixed_parties
    BEFORE UPDATE OF driver_id, vehicle_id, role ON driver_assignment
    WHEN NEW.driver_id <> OLD.driver_id OR NEW.vehicle_id <> OLD.vehicle_id OR NEW.role <> OLD.role
BEGIN
SELECT RAISE(ABORT, 'Chauffeur, véhicule et rôle d''une affectation ne peuvent pas être modifiés');
END;

CREATE TRIGGER driver_assignment_no_delete
    BEFORE DELETE ON driver_assignment
BEGIN
SELECT RAISE(ABORT, 'Une affectation ne peut pas être supprimée : la terminer par une date de fin');
END;
