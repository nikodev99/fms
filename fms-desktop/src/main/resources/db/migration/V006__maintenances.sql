-- Entretien : plans périodiques et interventions (instructions §9)
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.

CREATE TABLE maintenance_plan (
                                  id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                  version        INTEGER NOT NULL DEFAULT 0,
                                  vehicle_id     INTEGER NOT NULL REFERENCES vehicle (id),
                                  type_id        INTEGER NOT NULL REFERENCES maintenance_type (id),
                                  interval_days  INTEGER CHECK (interval_days IS NULL OR interval_days > 0),
                                  interval_km    INTEGER CHECK (interval_km IS NULL OR interval_km > 0),
                                  last_done_date TEXT,                       -- référence saisie (ex. dernier entretien connu avant FMS)
                                  last_done_km   INTEGER CHECK (last_done_km IS NULL OR last_done_km >= 0),
                                  active         INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                                  notes          TEXT,
                                  created_at     TEXT    NOT NULL,
                                  updated_at     TEXT    NOT NULL,
                                  CHECK (interval_days IS NOT NULL OR interval_km IS NOT NULL)
);

CREATE UNIQUE INDEX ux_maintenance_plan_active ON maintenance_plan (vehicle_id, type_id) WHERE active = 1;

CREATE TABLE maintenance_event (
                                   id            INTEGER PRIMARY KEY AUTOINCREMENT,
                                   version       INTEGER NOT NULL DEFAULT 0,
                                   vehicle_id    INTEGER NOT NULL REFERENCES vehicle (id),
                                   type_id       INTEGER NOT NULL REFERENCES maintenance_type (id),
                                   kind          TEXT    NOT NULL CHECK (kind IN ('SERVICE', 'REPAIR')),
                                   event_date    TEXT    NOT NULL,
                                   mileage       INTEGER CHECK (mileage IS NULL OR mileage >= 0),
                                   description   TEXT,
                                   supplier      TEXT,
                                   cost          INTEGER NOT NULL CHECK (cost >= 0),
                                   status        TEXT    NOT NULL CHECK (status IN ('RECORDED', 'CANCELLED')),
                                   cancel_reason TEXT,
                                   notes         TEXT,
                                   created_at    TEXT    NOT NULL,
                                   updated_at    TEXT    NOT NULL,
                                   CHECK ((status = 'RECORDED' AND cancel_reason IS NULL) OR (status = 'CANCELLED' AND cancel_reason IS NOT NULL))
);

CREATE INDEX idx_maintenance_event_vehicle ON maintenance_event (vehicle_id, event_date);

CREATE TRIGGER maintenance_event_no_delete
    BEFORE DELETE ON maintenance_event
BEGIN
SELECT RAISE(ABORT, 'Une intervention ne peut pas être supprimée : l''annuler avec un motif');
END;

CREATE TRIGGER maintenance_event_cancelled_is_final
    BEFORE UPDATE ON maintenance_event
    WHEN OLD.status = 'CANCELLED'
BEGIN
SELECT RAISE(ABORT, 'Une intervention annulée ne peut plus être modifiée');
END;
