-- Documents (et leurs fichiers), emprunts, utilisateurs, référentiels supprimables, jours de repos, indicateurs de
-- recouvrement, historique des rapports générés.
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.
--
-- Le grand livre (financial_transaction) est reconstruit pour accepter les origines 'LOAN' et 'LOAN_DISBURSEMENT'
-- (SQLite ne sait pas modifier une contrainte CHECK). Les tables qui le référencent (virements, clôtures, versements, bulletins) gardent leurs
-- liens : mêmes identifiants. La directive ci-dessous suspend les clés étrangères pendant le script ; le moteur de
-- migration les revérifie toutes (PRAGMA foreign_key_check) avant de valider.
-- fms:foreign-keys-off

-- =================================================================================================================
-- 1. Grand livre : origines LOAN (échéance payée, source_id = échéance) et LOAN_DISBURSEMENT (déblocage,
--    source_id = emprunt)
-- =================================================================================================================
CREATE TABLE financial_transaction_new (
                                           id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                           version        INTEGER NOT NULL DEFAULT 0,
                                           tx_date        TEXT    NOT NULL,
                                           recorded_at    TEXT    NOT NULL,
                                           direction      TEXT    NOT NULL CHECK (direction IN ('INFLOW', 'OUTFLOW')),
                                           category_id    INTEGER NOT NULL REFERENCES transaction_category (id),
                                           nature         TEXT    NOT NULL,
                                           account_id     INTEGER NOT NULL REFERENCES financial_account (id),
                                           amount         INTEGER NOT NULL CHECK (amount > 0),
                                           vehicle_id     INTEGER REFERENCES vehicle (id),
                                           driver_id      INTEGER REFERENCES driver (id),
                                           label          TEXT,
                                           reference      TEXT,
                                           source         TEXT    NOT NULL CHECK (source IN ('MANUAL', 'COLLECTION', 'TRANSFER', 'CASH_CLOSING',
                                                                                             'MAINTENANCE', 'PAYROLL', 'ADVANCE', 'LOAN', 'LOAN_DISBURSEMENT')),
                                           source_id      INTEGER,
                                           reversal_of_id INTEGER UNIQUE REFERENCES financial_transaction (id),
                                           reversed_by_id INTEGER UNIQUE REFERENCES financial_transaction (id),
                                           user_name      TEXT    NOT NULL,
                                           created_at     TEXT    NOT NULL,
                                           updated_at     TEXT    NOT NULL,
                                           CHECK (source = 'MANUAL' OR source_id IS NOT NULL),
                                           CHECK (reversal_of_id IS NULL OR reversed_by_id IS NULL)
);

INSERT INTO financial_transaction_new (id, version, tx_date, recorded_at, direction, category_id, nature, account_id,
                                       amount, vehicle_id, driver_id, label, reference, source, source_id,
                                       reversal_of_id, reversed_by_id, user_name, created_at, updated_at)
SELECT id, version, tx_date, recorded_at, direction, category_id, nature, account_id, amount, vehicle_id, driver_id,
       label, reference, source, source_id, reversal_of_id, reversed_by_id, user_name, created_at, updated_at
FROM financial_transaction;

DROP TABLE financial_transaction;
ALTER TABLE financial_transaction_new RENAME TO financial_transaction;

CREATE INDEX idx_tx_account_date ON financial_transaction (account_id, tx_date);
CREATE INDEX idx_tx_date ON financial_transaction (tx_date);
CREATE INDEX idx_tx_source ON financial_transaction (source, source_id);

CREATE TRIGGER financial_transaction_no_delete
    BEFORE DELETE ON financial_transaction
BEGIN
SELECT RAISE(ABORT, 'Une transaction ne peut pas être supprimée : la contre-passer');
END;

CREATE TRIGGER financial_transaction_immutable
    BEFORE UPDATE ON financial_transaction
    WHEN NEW.tx_date <> OLD.tx_date OR NEW.direction <> OLD.direction OR NEW.category_id <> OLD.category_id
        OR NEW.nature <> OLD.nature OR NEW.source <> OLD.source OR NEW.source_id IS NOT OLD.source_id
  OR NEW.recorded_at <> OLD.recorded_at OR NEW.user_name <> OLD.user_name
  OR NEW.reversal_of_id IS NOT OLD.reversal_of_id
  OR (OLD.reversed_by_id IS NOT NULL AND NEW.reversed_by_id IS NOT OLD.reversed_by_id)
  OR ((NEW.amount <> OLD.amount OR NEW.account_id <> OLD.account_id OR NEW.vehicle_id IS NOT OLD.vehicle_id
       OR NEW.driver_id IS NOT OLD.driver_id OR NEW.label IS NOT OLD.label OR NEW.reference IS NOT OLD.reference)
      AND (OLD.source <> 'COLLECTION' OR OLD.reversed_by_id IS NOT NULL OR OLD.reversal_of_id IS NOT NULL))
BEGIN
SELECT RAISE(ABORT, 'Une transaction validée ne peut pas être modifiée : la contre-passer');
END;

CREATE TRIGGER financial_transaction_closed_insert
    BEFORE INSERT ON financial_transaction
    WHEN EXISTS (SELECT 1 FROM cash_closing c
                 WHERE c.account_id = NEW.account_id AND c.status = 'CLOSED' AND c.closing_date >= NEW.tx_date
                   AND NOT (NEW.source = 'CASH_CLOSING' AND NEW.source_id = c.id))
BEGIN
SELECT RAISE(ABORT, 'Journée clôturée pour ce compte : aucune transaction ne peut y être datée');
END;

CREATE TRIGGER financial_transaction_closed_update
    BEFORE UPDATE OF amount, account_id ON financial_transaction
    WHEN (NEW.amount <> OLD.amount OR NEW.account_id <> OLD.account_id)
    AND EXISTS (SELECT 1 FROM cash_closing c
    WHERE c.account_id IN (OLD.account_id, NEW.account_id) AND c.status = 'CLOSED'
    AND c.closing_date >= OLD.tx_date)
BEGIN
SELECT RAISE(ABORT, 'Journée clôturée pour ce compte : la transaction ne peut plus être modifiée');
END;

-- =================================================================================================================
-- 2. Référentiels : système = verrouillé ; le reste est modifiable et supprimable tant qu'il n'est pas utilisé
--    (les clés étrangères refusent la suppression d'une ligne utilisée).
-- =================================================================================================================

-- Catégories de transactions : les catégories d'emprunt deviennent des catégories système (alimentées par le
-- module Emprunts). Une catégorie renommée par l'utilisateur n'est pas retrouvée : une nouvelle est créée.
UPDATE transaction_category SET code = 'LOAN_DISBURSEMENT', system = 1, direct_entry = 0, active = 1
WHERE code IS NULL AND name = 'Emprunt reçu' AND direction = 'INFLOW' AND nature = 'FINANCING';
UPDATE transaction_category SET code = 'LOAN_PRINCIPAL', system = 1, direct_entry = 0, active = 1
WHERE code IS NULL AND name = 'Remboursement d''emprunt (capital)' AND direction = 'OUTFLOW' AND nature = 'FINANCING';
UPDATE transaction_category SET code = 'LOAN_INTEREST', system = 1, direct_entry = 0, active = 1
WHERE code IS NULL AND name = 'Intérêts d''emprunt' AND direction = 'OUTFLOW' AND nature = 'FINANCIAL_CHARGE';

INSERT INTO transaction_category (code, name, direction, nature, direct_entry, requires_vehicle, system, created_at, updated_at)
SELECT 'LOAN_DISBURSEMENT', 'Emprunt reçu (module Emprunts)', 'INFLOW', 'FINANCING', 0, 0, 1,
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE NOT EXISTS (SELECT 1 FROM transaction_category WHERE code = 'LOAN_DISBURSEMENT');
INSERT INTO transaction_category (code, name, direction, nature, direct_entry, requires_vehicle, system, created_at, updated_at)
SELECT 'LOAN_PRINCIPAL', 'Remboursement d''emprunt — capital (module Emprunts)', 'OUTFLOW', 'FINANCING', 0, 0, 1,
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE NOT EXISTS (SELECT 1 FROM transaction_category WHERE code = 'LOAN_PRINCIPAL');
INSERT INTO transaction_category (code, name, direction, nature, direct_entry, requires_vehicle, system, created_at, updated_at)
SELECT 'LOAN_INTEREST', 'Intérêts d''emprunt (module Emprunts)', 'OUTFLOW', 'FINANCIAL_CHARGE', 0, 0, 1,
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE NOT EXISTS (SELECT 1 FROM transaction_category WHERE code = 'LOAN_INTEREST');
INSERT INTO transaction_category (code, name, direction, nature, direct_entry, requires_vehicle, system, created_at, updated_at)
SELECT 'LOAN_INSURANCE', 'Assurance emprunteur', 'OUTFLOW', 'FINANCIAL_CHARGE', 0, 0, 1,
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE NOT EXISTS (SELECT 1 FROM transaction_category WHERE code = 'LOAN_INSURANCE');

DROP TRIGGER transaction_category_no_delete;
DROP TRIGGER transaction_category_system_fixed;

CREATE TRIGGER transaction_category_system_locked
    BEFORE UPDATE ON transaction_category
    WHEN OLD.system = 1
BEGIN
SELECT RAISE(ABORT, 'Une catégorie système ne peut pas être modifiée');
END;

CREATE TRIGGER transaction_category_system_no_delete
    BEFORE DELETE ON transaction_category
    WHEN OLD.system = 1
BEGIN
SELECT RAISE(ABORT, 'Une catégorie système ne peut pas être supprimée');
END;

-- Catégories de véhicules : aucun traitement ne dépend d'elles ; elles ne sont plus « système ».
UPDATE vehicle_category SET is_system = 0;

-- Types de documents : certificat médical du chauffeur (obligatoire par défaut, réglable dans Paramètres).
INSERT INTO document_type (name, subject, mandatory, created_at, updated_at)
SELECT 'Certificat médical', 'DRIVER', 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE NOT EXISTS (SELECT 1 FROM document_type WHERE subject = 'DRIVER' AND name = 'Certificat médical');

-- =================================================================================================================
-- 3. Documents des véhicules et des chauffeurs
--    Un renouvellement crée un nouveau document (replaces_id = document remplacé) ; l'ancien passe superseded = 1 et
--    reste dans l'historique. Document en cours = superseded = 0 (un seul par titulaire et par type).
-- =================================================================================================================
CREATE TABLE fleet_document (
                                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                version        INTEGER NOT NULL DEFAULT 0,
                                type_id        INTEGER NOT NULL REFERENCES document_type (id),
                                subject        TEXT    NOT NULL CHECK (subject IN ('VEHICLE', 'DRIVER')),
                                vehicle_id     INTEGER REFERENCES vehicle (id),
                                driver_id      INTEGER REFERENCES driver (id),
                                number         TEXT,
                                issuer         TEXT,
                                issued_on      TEXT,
                                expires_on     TEXT,                          -- NULL : document sans échéance
                                replaces_id    INTEGER UNIQUE REFERENCES fleet_document (id),
                                superseded     INTEGER NOT NULL DEFAULT 0 CHECK (superseded IN (0, 1)),
                                notes          TEXT,
                                created_at     TEXT    NOT NULL,
                                updated_at     TEXT    NOT NULL,
                                CHECK ((subject = 'VEHICLE' AND vehicle_id IS NOT NULL AND driver_id IS NULL)
                                    OR (subject = 'DRIVER' AND driver_id IS NOT NULL AND vehicle_id IS NULL)),
                                CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on),
                                CHECK (replaces_id IS NULL OR replaces_id <> id)
);

CREATE UNIQUE INDEX ux_fleet_document_current_vehicle ON fleet_document (type_id, vehicle_id)
    WHERE superseded = 0 AND vehicle_id IS NOT NULL;
CREATE UNIQUE INDEX ux_fleet_document_current_driver ON fleet_document (type_id, driver_id)
    WHERE superseded = 0 AND driver_id IS NOT NULL;
CREATE INDEX idx_fleet_document_expiry ON fleet_document (expires_on);

CREATE TRIGGER fleet_document_subject_matches_type
    BEFORE INSERT ON fleet_document
    WHEN (SELECT t.subject FROM document_type t WHERE t.id = NEW.type_id) IS NOT NEW.subject
BEGIN
SELECT RAISE(ABORT, 'Le type de document ne correspond pas à un véhicule / chauffeur');
END;

CREATE TRIGGER fleet_document_fixed_owner
    BEFORE UPDATE OF type_id, subject, vehicle_id, driver_id ON fleet_document
    WHEN NEW.type_id <> OLD.type_id OR NEW.subject <> OLD.subject
        OR NEW.vehicle_id IS NOT OLD.vehicle_id OR NEW.driver_id IS NOT OLD.driver_id
BEGIN
SELECT RAISE(ABORT, 'Le type et le titulaire d''un document ne peuvent pas être modifiés');
END;

-- Reprise des permis saisis sur la fiche chauffeur (les colonnes d'origine ne sont plus utilisées).
INSERT INTO fleet_document (type_id, subject, driver_id, number, expires_on, created_at, updated_at)
SELECT t.id, 'DRIVER', d.id, d.licence_number, d.licence_expiry,
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM driver d
         JOIN document_type t ON t.subject = 'DRIVER' AND t.name = 'Permis de conduire'
WHERE d.licence_number IS NOT NULL OR d.licence_expiry IS NOT NULL;

-- =================================================================================================================
-- 4. Emprunts
-- =================================================================================================================
CREATE TABLE loan (
                      id                          INTEGER PRIMARY KEY AUTOINCREMENT,
                      version                     INTEGER NOT NULL DEFAULT 0,
                      lender                      TEXT    NOT NULL,
                      reference                   TEXT,
                      purpose                     TEXT,
                      principal                   INTEGER NOT NULL CHECK (principal > 0),
                      annual_rate_bp              INTEGER NOT NULL CHECK (annual_rate_bp BETWEEN 0 AND 10000),
                      term_count                  INTEGER NOT NULL CHECK (term_count BETWEEN 1 AND 600),
                      frequency                   TEXT    NOT NULL CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
                      first_due_date              TEXT    NOT NULL,
                      insurance_per_instalment    INTEGER NOT NULL DEFAULT 0 CHECK (insurance_per_instalment >= 0),
                      bank_instalment             INTEGER CHECK (bank_instalment IS NULL OR bank_instalment > 0),  -- assurance comprise
                      disbursement_date           TEXT,
                      disbursement_account_id     INTEGER REFERENCES financial_account (id),
                      disbursement_transaction_id INTEGER REFERENCES financial_transaction (id),
                      status                      TEXT    NOT NULL CHECK (status IN ('ACTIVE', 'REPAID', 'CANCELLED')),
                      cancel_reason               TEXT,
                      notes                       TEXT,
                      created_at                  TEXT    NOT NULL,
                      updated_at                  TEXT    NOT NULL,
                      CHECK ((disbursement_account_id IS NULL) = (disbursement_date IS NULL)),
                      CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL))
);

CREATE TRIGGER loan_no_delete
    BEFORE DELETE ON loan
BEGIN
SELECT RAISE(ABORT, 'Un emprunt ne peut pas être supprimé : l''annuler avec un motif');
END;

-- Véhicules financés : facultatif ; un emprunt peut financer un, plusieurs ou aucun véhicule précis.
CREATE TABLE loan_vehicle (
                              loan_id    INTEGER NOT NULL REFERENCES loan (id),
                              vehicle_id INTEGER NOT NULL REFERENCES vehicle (id),
                              PRIMARY KEY (loan_id, vehicle_id)
);

CREATE TABLE loan_instalment (
                                 id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                                 version            INTEGER NOT NULL DEFAULT 0,
                                 loan_id            INTEGER NOT NULL REFERENCES loan (id),
                                 seq                INTEGER NOT NULL CHECK (seq >= 1),
                                 due_date           TEXT    NOT NULL,
                                 principal          INTEGER NOT NULL CHECK (principal >= 0),
                                 interest           INTEGER NOT NULL CHECK (interest >= 0),
                                 insurance          INTEGER NOT NULL CHECK (insurance >= 0),
                                 total              INTEGER NOT NULL,
                                 balance_after      INTEGER NOT NULL CHECK (balance_after >= 0),
                                 status             TEXT    NOT NULL CHECK (status IN ('DUE', 'PAID', 'CANCELLED')),
                                 paid_on            TEXT,
                                 account_id         INTEGER REFERENCES financial_account (id),
                                 principal_tx_id    INTEGER REFERENCES financial_transaction (id),
                                 interest_tx_id     INTEGER REFERENCES financial_transaction (id),
                                 insurance_tx_id    INTEGER REFERENCES financial_transaction (id),
                                 created_at         TEXT    NOT NULL,
                                 updated_at         TEXT    NOT NULL,
                                 UNIQUE (loan_id, seq),
                                 CHECK (total = principal + interest + insurance),
                                 CHECK ((status = 'PAID') = (paid_on IS NOT NULL AND account_id IS NOT NULL))
);

CREATE INDEX idx_loan_instalment_due ON loan_instalment (status, due_date);

-- Une échéance payée ne se modifie que pour être contre-passée (retour à 'DUE', liens effacés).
CREATE TRIGGER loan_instalment_paid_frozen
    BEFORE UPDATE ON loan_instalment
    WHEN OLD.status = 'PAID' AND NEW.status = 'PAID'
BEGIN
SELECT RAISE(ABORT, 'Une échéance payée ne peut pas être modifiée : contre-passer le paiement');
END;

CREATE TRIGGER loan_instalment_paid_no_delete
    BEFORE DELETE ON loan_instalment
    WHEN OLD.status = 'PAID'
BEGIN
SELECT RAISE(ABORT, 'Une échéance payée ne peut pas être supprimée');
END;

-- =================================================================================================================
-- 5. Utilisateurs et droits
--    Mots de passe : PBKDF2-HMAC-SHA256, sel aléatoire de 16 octets, nombre d'itérations conservé par utilisateur.
-- =================================================================================================================
CREATE TABLE app_user (
                          id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                          version              INTEGER NOT NULL DEFAULT 0,
                          username             TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                          display_name         TEXT    NOT NULL,
                          role                 TEXT    NOT NULL CHECK (role IN ('OWNER', 'USER')),
                          password_hash        TEXT    NOT NULL,
                          password_salt        TEXT    NOT NULL,
                          password_iterations  INTEGER NOT NULL CHECK (password_iterations >= 100000),
                          must_change_password INTEGER NOT NULL DEFAULT 0 CHECK (must_change_password IN (0, 1)),
                          active               INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                          failed_attempts      INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
                          locked_until         TEXT,
                          last_login_at        TEXT,
                          created_at           TEXT    NOT NULL,
                          updated_at           TEXT    NOT NULL
);

CREATE TRIGGER app_user_no_delete
    BEFORE DELETE ON app_user
BEGIN
SELECT RAISE(ABORT, 'Un utilisateur ne peut pas être supprimé : le désactiver');
END;

-- Il reste toujours au moins un propriétaire actif.
CREATE TRIGGER app_user_keep_one_owner
    BEFORE UPDATE OF role, active ON app_user
    WHEN OLD.role = 'OWNER' AND OLD.active = 1 AND (NEW.role <> 'OWNER' OR NEW.active = 0)
        AND (SELECT COUNT(*) FROM app_user WHERE role = 'OWNER' AND active = 1 AND id <> OLD.id) = 0
BEGIN
SELECT RAISE(ABORT, 'Il faut au moins un propriétaire actif');
END;

-- Modules accessibles à un utilisateur (identifiants de menu). Le propriétaire a accès à tout.
CREATE TABLE user_permission (
                                 user_id INTEGER NOT NULL REFERENCES app_user (id),
                                 module  TEXT    NOT NULL,
                                 PRIMARY KEY (user_id, module)
);

-- =================================================================================================================
-- 6. Paramètres : jours de repos, indicateurs de recouvrement, alertes d'emprunt
-- =================================================================================================================
INSERT INTO app_setting (key, value, updated_at) VALUES
                                                     ('fleet.restDays',               '7',    strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),  -- ISO : 1 = lundi … 7 = dimanche
                                                     ('collection.objectiveBp',       '9500', strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('collection.redThresholdBp',    '5000', strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('loan.alertDays',               '7',    strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- =================================================================================================================
-- 7. Fichiers enregistrés dans la base : pièces jointes des documents, rapports générés
--    Dans la base plutôt que dans un dossier : ils suivent les sauvegardes et la restauration, et ne sont lisibles
--    qu'à travers l'application (connexion, droits). Un fichier enregistré ne se modifie jamais.
-- =================================================================================================================
CREATE TABLE stored_file (
                             id          INTEGER PRIMARY KEY AUTOINCREMENT,
                             file_name   TEXT    NOT NULL CHECK (length(file_name) BETWEEN 1 AND 200),
                             media_type  TEXT    NOT NULL CHECK (media_type IN ('application/pdf', 'image/png', 'image/jpeg')),
                             size_bytes  INTEGER NOT NULL CHECK (size_bytes > 0),
                             sha256      TEXT    NOT NULL CHECK (length(sha256) = 64),
                             content     BLOB    NOT NULL,
                             created_at  TEXT    NOT NULL,
                             created_by  TEXT    NOT NULL
);

CREATE TRIGGER stored_file_immutable
    BEFORE UPDATE ON stored_file
BEGIN
SELECT RAISE(ABORT, 'Un fichier enregistré ne se modifie pas');
END;

-- Pièces jointes d'un document (scan, photo recto/verso…). Retirer une pièce jointe supprime le lien et le fichier.
CREATE TABLE document_file (
                               id           INTEGER PRIMARY KEY AUTOINCREMENT,
                               document_id  INTEGER NOT NULL REFERENCES fleet_document (id),
                               file_id      INTEGER NOT NULL UNIQUE REFERENCES stored_file (id),
                               created_at   TEXT    NOT NULL
);

CREATE INDEX idx_document_file_document ON document_file (document_id);

-- Historique des rapports générés : le fichier tel qu'il a été produit, jamais régénéré ni supprimé.
CREATE TABLE generated_report (
                                  id            INTEGER PRIMARY KEY AUTOINCREMENT,
                                  kind          TEXT    NOT NULL CHECK (kind IN ('ACCOUNT_STATEMENT', 'VEHICLE_SHEET', 'MONTHLY_CLOSINGS',
                                                                                 'MONTHLY_COLLECTIONS')),
                                  reference     TEXT    NOT NULL UNIQUE,
                                  subject       TEXT    NOT NULL,                -- objet : compte et période, véhicule, mois
                                  file_id       INTEGER NOT NULL UNIQUE REFERENCES stored_file (id),
                                  generated_at  TEXT    NOT NULL,
                                  generated_by  TEXT    NOT NULL
);

CREATE INDEX idx_generated_report_date ON generated_report (generated_at);

CREATE TRIGGER generated_report_immutable
    BEFORE UPDATE ON generated_report
BEGIN
SELECT RAISE(ABORT, 'Un rapport généré ne se modifie pas');
END;

CREATE TRIGGER generated_report_no_delete
    BEFORE DELETE ON generated_report
BEGIN
SELECT RAISE(ABORT, 'Un rapport généré ne se supprime pas');
END;
