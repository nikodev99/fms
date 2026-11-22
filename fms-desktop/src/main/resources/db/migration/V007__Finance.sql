-- Trésorerie, versements, comptes chauffeurs et paie (instructions §4.3 à §4.7)
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.
--
-- Principes (identiques à l'édition Excel) :
--  * un grand livre de trésorerie unique (financial_transaction) ; les soldes ne sont jamais stockés ;
--  * aucune donnée financière n'est supprimée ; une transaction validée ne se modifie pas : contre-passation liée ;
--  * seule exception : le versement du jour, avant point journalier et clôture (service Versements, audité) ;
--  * une clôture verrouille le compte jusqu'à sa date incluse.

-- ---- Chauffeurs et affectations : compléments ---------------------------------------------------------------
ALTER TABLE driver ADD COLUMN its_subject INTEGER NOT NULL DEFAULT 1 CHECK (its_subject IN (0, 1));

ALTER TABLE driver_assignment ADD COLUMN expected_daily INTEGER CHECK (expected_daily IS NULL OR expected_daily >= 0);

-- Le versement attendu passe sur l'affectation : on reprend l'objectif de chaque chauffeur.
UPDATE driver_assignment SET expected_daily = (SELECT d.daily_target FROM driver d WHERE d.id = driver_assignment.driver_id);

-- ---- Comptes de trésorerie ------------------------------------------------------------------------------------
CREATE TABLE financial_account (
                                   id                       INTEGER PRIMARY KEY AUTOINCREMENT,
                                   version                  INTEGER NOT NULL DEFAULT 0,
                                   name                     TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                                   type                     TEXT    NOT NULL CHECK (type IN ('CASH', 'MOBILE_MONEY', 'BANK', 'OTHER')),
                                   opening_balance          INTEGER NOT NULL,
                                   opening_date             TEXT    NOT NULL,
                                   included_in_consolidated INTEGER NOT NULL DEFAULT 1 CHECK (included_in_consolidated IN (0, 1)),
                                   overdraft_allowed        INTEGER NOT NULL DEFAULT 0 CHECK (overdraft_allowed IN (0, 1)),
                                   active                   INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                                   notes                    TEXT,
                                   created_at               TEXT    NOT NULL,
                                   updated_at               TEXT    NOT NULL,
                                   CHECK (overdraft_allowed = 1 OR opening_balance >= 0)
);

CREATE TRIGGER financial_account_no_delete
    BEFORE DELETE ON financial_account
BEGIN
SELECT RAISE(ABORT, 'Un compte ne peut pas être supprimé : le désactiver');
END;

INSERT INTO financial_account (name, type, opening_balance, opening_date, created_at, updated_at) VALUES
                                                                                                      ('Caisse',            'CASH',         0, date('now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                      ('MTN Mobile Money',  'MOBILE_MONEY', 0, date('now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                      ('Airtel Money',      'MOBILE_MONEY', 0, date('now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- ---- Catégories de transactions ---------------------------------------------------------------------------------
CREATE TABLE transaction_category (
                                      id               INTEGER PRIMARY KEY AUTOINCREMENT,
                                      version          INTEGER NOT NULL DEFAULT 0,
                                      code             TEXT    UNIQUE,                       -- catégories système seulement
                                      name             TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                                      direction        TEXT    NOT NULL CHECK (direction IN ('INFLOW', 'OUTFLOW')),
                                      nature           TEXT    NOT NULL CHECK (nature IN ('OPERATING', 'FINANCIAL_CHARGE', 'PAYROLL', 'DRIVER_ACCOUNT',
                                                                                          'FINANCING', 'INVESTMENT', 'TRANSFER')),
                                      direct_entry     INTEGER NOT NULL DEFAULT 1 CHECK (direct_entry IN (0, 1)),
                                      requires_vehicle INTEGER NOT NULL DEFAULT 0 CHECK (requires_vehicle IN (0, 1)),
                                      system           INTEGER NOT NULL DEFAULT 0 CHECK (system IN (0, 1)),
                                      active           INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                                      created_at       TEXT    NOT NULL,
                                      updated_at       TEXT    NOT NULL,
                                      CHECK (system = 0 OR code IS NOT NULL),
                                      CHECK (nature <> 'TRANSFER' OR system = 1)
);

CREATE TRIGGER transaction_category_no_delete
    BEFORE DELETE ON transaction_category
BEGIN
SELECT RAISE(ABORT, 'Une catégorie ne peut pas être supprimée : la désactiver');
END;

CREATE TRIGGER transaction_category_system_fixed
    BEFORE UPDATE ON transaction_category
    WHEN OLD.system = 1 AND (NEW.code IS NOT OLD.code OR NEW.direction <> OLD.direction OR NEW.nature <> OLD.nature
                         OR NEW.system <> OLD.system OR NEW.direct_entry <> OLD.direct_entry OR NEW.active = 0)
BEGIN
SELECT RAISE(ABORT, 'Une catégorie système ne change que de libellé');
END;

INSERT INTO transaction_category (code, name, direction, nature, direct_entry, requires_vehicle, system, created_at, updated_at) VALUES
                                                                                                                                     -- Système (alimentées par les modules)
                                                                                                                                     ('COLLECTION',        'Versements des chauffeurs',            'INFLOW',  'OPERATING',      0, 1, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('TRANSFER_IN',       'Virement interne reçu',                'INFLOW',  'TRANSFER',       0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('TRANSFER_OUT',      'Virement interne émis',                'OUTFLOW', 'TRANSFER',       0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('CASH_SURPLUS',      'Excédent de caisse',                   'INFLOW',  'OPERATING',      0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('CASH_SHORTAGE',     'Manquant de caisse',                   'OUTFLOW', 'OPERATING',      0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('MAINTENANCE',       'Entretien et réparations',             'OUTFLOW', 'OPERATING',      0, 1, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('NET_SALARY',        'Salaires nets versés',                 'OUTFLOW', 'PAYROLL',        0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('SALARY_ADVANCE',    'Avances sur salaire',                  'OUTFLOW', 'DRIVER_ACCOUNT', 0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('ADVANCE_REPAYMENT', 'Remboursements d''avances',            'INFLOW',  'DRIVER_ACCOUNT', 0, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     ('BANK_FEES',         'Frais bancaires et Mobile Money',      'OUTFLOW', 'OPERATING',      1, 0, 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     -- Modifiables (entrées)
                                                                                                                                     (NULL, 'Location de véhicule',                 'INFLOW',  'OPERATING',        1, 1, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Autres recettes d''exploitation',      'INFLOW',  'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Remboursement reçu',                   'INFLOW',  'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Apport du propriétaire',               'INFLOW',  'FINANCING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Emprunt reçu',                         'INFLOW',  'FINANCING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Vente de véhicule',                    'INFLOW',  'INVESTMENT',       1, 1, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     -- Modifiables (sorties)
                                                                                                                                     (NULL, 'Carburant',                            'OUTFLOW', 'OPERATING',        1, 1, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Assurance',                            'OUTFLOW', 'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Documents et taxes du véhicule',       'OUTFLOW', 'OPERATING',        1, 1, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Lavage',                               'OUTFLOW', 'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Impôts et taxes',                      'OUTFLOW', 'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Frais administratifs',                 'OUTFLOW', 'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Charges sociales et ITS versés',       'OUTFLOW', 'PAYROLL',          1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Intérêts d''emprunt',                  'OUTFLOW', 'FINANCIAL_CHARGE', 1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Remboursement d''emprunt (capital)',   'OUTFLOW', 'FINANCING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Prélèvement du propriétaire',          'OUTFLOW', 'FINANCING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Achat de véhicule',                    'OUTFLOW', 'INVESTMENT',       1, 1, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                                                     (NULL, 'Autres dépenses d''exploitation',      'OUTFLOW', 'OPERATING',        1, 0, 0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- ---- Grand livre de trésorerie ------------------------------------------------------------------------------------
CREATE TABLE financial_transaction (
                                       id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                       version        INTEGER NOT NULL DEFAULT 0,
                                       tx_date        TEXT    NOT NULL,                       -- date de valeur (jour comptable)
                                       recorded_at    TEXT    NOT NULL,                       -- instant de saisie UTC
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
                                                                                         'MAINTENANCE', 'PAYROLL', 'ADVANCE')),
                                       source_id      INTEGER,
                                       reversal_of_id INTEGER UNIQUE REFERENCES financial_transaction (id),
                                       reversed_by_id INTEGER UNIQUE REFERENCES financial_transaction (id),
                                       user_name      TEXT    NOT NULL,
                                       created_at     TEXT    NOT NULL,
                                       updated_at     TEXT    NOT NULL,
                                       CHECK (source = 'MANUAL' OR source_id IS NOT NULL),
                                       CHECK (reversal_of_id IS NULL OR reversed_by_id IS NULL)
);

CREATE INDEX idx_tx_account_date ON financial_transaction (account_id, tx_date);
CREATE INDEX idx_tx_date ON financial_transaction (tx_date);
CREATE INDEX idx_tx_source ON financial_transaction (source, source_id);

CREATE TRIGGER financial_transaction_no_delete
    BEFORE DELETE ON financial_transaction
BEGIN
SELECT RAISE(ABORT, 'Une transaction ne peut pas être supprimée : la contre-passer');
END;

-- Toujours figés : date, sens, catégorie, nature, origine, horodatage, auteur, lien d'annulation.
-- Contre-passation : reversed_by_id passe une seule fois de NULL à une valeur.
-- Montant, compte, véhicule, chauffeur, libellé : modifiables seulement pour un versement non annulé (versement du
-- jour, contrôlé par le service), et jamais sur une journée clôturée.
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

-- ---- Virements internes -------------------------------------------------------------------------------------------
CREATE TABLE account_transfer (
                                  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                                  version            INTEGER NOT NULL DEFAULT 0,
                                  transfer_date      TEXT    NOT NULL,
                                  from_account_id    INTEGER NOT NULL REFERENCES financial_account (id),
                                  to_account_id      INTEGER NOT NULL REFERENCES financial_account (id),
                                  amount             INTEGER NOT NULL CHECK (amount > 0),
                                  fee                INTEGER CHECK (fee IS NULL OR fee >= 0),
                                  label              TEXT,
                                  reference          TEXT,
                                  out_transaction_id INTEGER REFERENCES financial_transaction (id),
                                  in_transaction_id  INTEGER REFERENCES financial_transaction (id),
                                  fee_transaction_id INTEGER REFERENCES financial_transaction (id),
                                  status             TEXT    NOT NULL CHECK (status IN ('VALID', 'REVERSED')),
                                  reversal_reason    TEXT,
                                  user_name          TEXT    NOT NULL,
                                  created_at         TEXT    NOT NULL,
                                  updated_at         TEXT    NOT NULL,
                                  CHECK (from_account_id <> to_account_id),
                                  CHECK ((status = 'VALID' AND reversal_reason IS NULL) OR (status = 'REVERSED' AND reversal_reason IS NOT NULL))
);

CREATE TRIGGER account_transfer_no_delete
    BEFORE DELETE ON account_transfer
BEGIN
SELECT RAISE(ABORT, 'Un virement ne peut pas être supprimé : le contre-passer');
END;

CREATE TRIGGER account_transfer_reversed_is_final
    BEFORE UPDATE ON account_transfer
    WHEN OLD.status = 'REVERSED'
        OR NEW.amount <> OLD.amount OR NEW.from_account_id <> OLD.from_account_id OR NEW.to_account_id <> OLD.to_account_id
        OR NEW.transfer_date <> OLD.transfer_date OR NEW.fee IS NOT OLD.fee
BEGIN
SELECT RAISE(ABORT, 'Un virement enregistré ne se modifie pas : le contre-passer');
END;

-- ---- Clôtures de caisse -------------------------------------------------------------------------------------------
CREATE TABLE cash_closing (
                              id                        INTEGER PRIMARY KEY AUTOINCREMENT,
                              version                   INTEGER NOT NULL DEFAULT 0,
                              account_id                INTEGER NOT NULL REFERENCES financial_account (id),
                              closing_date              TEXT    NOT NULL,
                              opening_balance           INTEGER NOT NULL,
                              inflows                   INTEGER NOT NULL CHECK (inflows >= 0),
                              outflows                  INTEGER NOT NULL CHECK (outflows >= 0),
                              transfers_in              INTEGER NOT NULL CHECK (transfers_in >= 0),
                              transfers_out             INTEGER NOT NULL CHECK (transfers_out >= 0),
                              expected                  INTEGER NOT NULL,
                              actual                    INTEGER NOT NULL,
                              difference                INTEGER NOT NULL,
                              explanation               TEXT,
                              difference_transaction_id INTEGER REFERENCES financial_transaction (id),
                              cashier                   TEXT    NOT NULL,
                              closed_at                 TEXT    NOT NULL,
                              status                    TEXT    NOT NULL CHECK (status IN ('CLOSED', 'REOPENED')),
                              reopen_reason             TEXT,
                              reopened_at               TEXT,
                              created_at                TEXT    NOT NULL,
                              updated_at                TEXT    NOT NULL,
                              CHECK (expected = opening_balance + inflows - outflows + transfers_in - transfers_out),
                              CHECK (difference = actual - expected),
                              CHECK (difference = 0 OR explanation IS NOT NULL),
                              CHECK ((status = 'CLOSED' AND reopen_reason IS NULL) OR (status = 'REOPENED' AND reopen_reason IS NOT NULL))
);

CREATE UNIQUE INDEX ux_cash_closing_open ON cash_closing (account_id, closing_date) WHERE status = 'CLOSED';
CREATE INDEX idx_cash_closing_date ON cash_closing (closing_date);

CREATE TRIGGER cash_closing_no_delete
    BEFORE DELETE ON cash_closing
BEGIN
SELECT RAISE(ABORT, 'Une clôture ne peut pas être supprimée : la rouvrir avec un motif');
END;

-- Évolutions permises : lien vers la transaction d'écart (une fois), puis CLOSED → REOPENED (avec motif).
-- Les chiffres restent ceux de la clôture.
CREATE TRIGGER cash_closing_frozen
    BEFORE UPDATE ON cash_closing
    WHEN OLD.status = 'REOPENED'
        OR NEW.account_id <> OLD.account_id OR NEW.closing_date <> OLD.closing_date OR NEW.actual <> OLD.actual
        OR NEW.expected <> OLD.expected OR NEW.difference <> OLD.difference
        OR (OLD.difference_transaction_id IS NOT NULL
            AND NEW.difference_transaction_id IS NOT OLD.difference_transaction_id)
BEGIN
SELECT RAISE(ABORT, 'Une clôture ne se modifie pas ; seule la dernière peut être rouverte');
END;

-- Journée clôturée : aucune transaction ne peut y être datée (sauf l'écart de la clôture elle-même).
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

-- ---- Versements des chauffeurs ------------------------------------------------------------------------------------
CREATE TABLE collection_entry (
                                  id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                  version        INTEGER NOT NULL DEFAULT 0,
                                  entry_date     TEXT    NOT NULL,
                                  recorded_at    TEXT    NOT NULL,
                                  driver_id      INTEGER NOT NULL REFERENCES driver (id),
                                  vehicle_id     INTEGER NOT NULL REFERENCES vehicle (id),
                                  account_id     INTEGER NOT NULL REFERENCES financial_account (id),
                                  amount         INTEGER NOT NULL CHECK (amount > 0),
                                  kind           TEXT    NOT NULL CHECK (kind IN ('COLLECTION', 'CREDIT_NOTE')),
                                  status         TEXT    NOT NULL CHECK (status IN ('VALID', 'CREDITED')),
                                  origin_id      INTEGER UNIQUE REFERENCES collection_entry (id),
                                  transaction_id INTEGER REFERENCES financial_transaction (id),
                                  cashier        TEXT    NOT NULL,
                                  reason         TEXT,
                                  notes          TEXT,
                                  created_at     TEXT    NOT NULL,
                                  updated_at     TEXT    NOT NULL,
                                  CHECK ((kind = 'COLLECTION' AND origin_id IS NULL) OR (kind = 'CREDIT_NOTE' AND origin_id IS NOT NULL
                                      AND status = 'VALID' AND reason IS NOT NULL))
);

CREATE INDEX idx_collection_date ON collection_entry (entry_date, driver_id);
CREATE INDEX idx_collection_driver ON collection_entry (driver_id, entry_date);

CREATE TRIGGER collection_entry_no_delete
    BEFORE DELETE ON collection_entry
BEGIN
SELECT RAISE(ABORT, 'Un versement ne peut pas être supprimé : émettre un avoir');
END;

CREATE TRIGGER collection_entry_fixed
    BEFORE UPDATE ON collection_entry
    WHEN NEW.kind <> OLD.kind OR NEW.origin_id IS NOT OLD.origin_id OR NEW.entry_date <> OLD.entry_date
  OR NEW.recorded_at <> OLD.recorded_at OR OLD.status = 'CREDITED'
  OR (OLD.kind = 'CREDIT_NOTE' AND OLD.transaction_id IS NOT NULL)
  OR (OLD.transaction_id IS NOT NULL AND NEW.transaction_id IS NOT OLD.transaction_id)
BEGIN
SELECT RAISE(ABORT, 'Ce versement ne peut plus être modifié : émettre un avoir');
END;

-- ---- Points journaliers ---------------------------------------------------------------------------------------------
CREATE TABLE daily_point (
                             id           INTEGER PRIMARY KEY AUTOINCREMENT,
                             version      INTEGER NOT NULL DEFAULT 0,
                             point_date   TEXT    NOT NULL,
                             driver_id    INTEGER NOT NULL REFERENCES driver (id),
                             vehicle_id   INTEGER REFERENCES vehicle (id),
                             expected     INTEGER NOT NULL CHECK (expected >= 0),
                             received     INTEGER NOT NULL,
                             difference   INTEGER NOT NULL,
                             situation    TEXT    NOT NULL CHECK (situation IN ('NORMAL', 'EXEMPT')),
                             reason       TEXT,
                             validated_by TEXT    NOT NULL,
                             validated_at TEXT    NOT NULL,
                             created_at   TEXT    NOT NULL,
                             updated_at   TEXT    NOT NULL,
                             UNIQUE (point_date, driver_id),
                             CHECK (difference = received - expected),
                             CHECK (situation = 'NORMAL' OR (expected = 0 AND reason IS NOT NULL))
);

CREATE TRIGGER daily_point_no_update
    BEFORE UPDATE ON daily_point
BEGIN
SELECT RAISE(ABORT, 'Un point journalier validé ne se modifie pas : passer une régularisation');
END;

CREATE TRIGGER daily_point_no_delete
    BEFORE DELETE ON daily_point
BEGIN
SELECT RAISE(ABORT, 'Un point journalier ne peut pas être supprimé');
END;

-- ---- Comptes chauffeurs (ce que le chauffeur doit) ---------------------------------------------------------------
CREATE TABLE driver_ledger (
                               id          INTEGER PRIMARY KEY AUTOINCREMENT,
                               version     INTEGER NOT NULL DEFAULT 0,
                               entry_date  TEXT    NOT NULL,
                               recorded_at TEXT    NOT NULL,
                               driver_id   INTEGER NOT NULL REFERENCES driver (id),
                               rubric      TEXT    NOT NULL CHECK (rubric IN ('SHORTAGE', 'ADVANCE', 'OTHER_DEBT')),
                               amount      INTEGER NOT NULL CHECK (amount <> 0),
                               source      TEXT    NOT NULL CHECK (source IN ('DAILY_POINT', 'REGULARISATION', 'ADVANCE', 'ADVANCE_REPAYMENT',
                                                                              'PAYROLL', 'MANUAL')),
                               source_id   INTEGER,
                               label       TEXT    NOT NULL,
                               user_name   TEXT    NOT NULL,
                               created_at  TEXT    NOT NULL,
                               updated_at  TEXT    NOT NULL,
                               CHECK (source = 'MANUAL' OR source_id IS NOT NULL)
);

CREATE INDEX idx_driver_ledger_driver ON driver_ledger (driver_id, entry_date);
CREATE INDEX idx_driver_ledger_source ON driver_ledger (source, source_id);

CREATE TRIGGER driver_ledger_no_update
    BEFORE UPDATE ON driver_ledger
BEGIN
SELECT RAISE(ABORT, 'Un mouvement de compte chauffeur ne se modifie pas : passer un mouvement inverse');
END;

CREATE TRIGGER driver_ledger_no_delete
    BEFORE DELETE ON driver_ledger
BEGIN
SELECT RAISE(ABORT, 'Un mouvement de compte chauffeur ne peut pas être supprimé');
END;

-- ---- Paie -----------------------------------------------------------------------------------------------------------
CREATE TABLE payslip (
                         id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                         version                 INTEGER NOT NULL DEFAULT 0,
                         period                  TEXT    NOT NULL,                -- AAAA-MM
                         driver_id               INTEGER NOT NULL REFERENCES driver (id),
                         pay_scheme              TEXT    NOT NULL,
                         collections_base        INTEGER NOT NULL,
                         fixed_part              INTEGER NOT NULL CHECK (fixed_part >= 0),
                         commission              INTEGER NOT NULL CHECK (commission >= 0),
                         adjustment              INTEGER NOT NULL,
                         adjustment_reason       TEXT,
                         gross                   INTEGER NOT NULL CHECK (gross >= 0),
                         employee_contributions  INTEGER NOT NULL CHECK (employee_contributions >= 0),
                         its                     INTEGER NOT NULL CHECK (its >= 0),
                         its_parts_tenths        INTEGER NOT NULL CHECK (its_parts_tenths > 0),
                         net_before_recoveries   INTEGER NOT NULL CHECK (net_before_recoveries >= 0),
                         advance_recovered       INTEGER NOT NULL CHECK (advance_recovered >= 0),
                         shortage_recovered      INTEGER NOT NULL CHECK (shortage_recovered >= 0),
                         other_debt_recovered    INTEGER NOT NULL CHECK (other_debt_recovered >= 0),
                         recovery_note           TEXT,
                         net_payable             INTEGER NOT NULL CHECK (net_payable >= 0),
                         employer_charges        INTEGER NOT NULL CHECK (employer_charges >= 0),
                         employer_cost           INTEGER NOT NULL,
                         status                  TEXT    NOT NULL CHECK (status IN ('DRAFT', 'VALIDATED', 'PAID', 'CANCELLED')),
                         validated_by            TEXT,
                         validated_at            TEXT,
                         paid_date               TEXT,
                         account_id              INTEGER REFERENCES financial_account (id),
                         payment_reference       TEXT,
                         transaction_id          INTEGER REFERENCES financial_transaction (id),
                         cancel_reason           TEXT,
                         created_at              TEXT    NOT NULL,
                         updated_at              TEXT    NOT NULL,
                         CHECK (net_payable = net_before_recoveries - advance_recovered - shortage_recovered - other_debt_recovered),
                         CHECK (employer_cost = gross + employer_charges),
                         CHECK (adjustment = 0 OR adjustment_reason IS NOT NULL),
                         CHECK (status = 'DRAFT' OR status = 'CANCELLED' OR validated_at IS NOT NULL),
                         CHECK ((status = 'PAID' AND (transaction_id IS NOT NULL OR net_payable = 0))
                             OR (status <> 'PAID' AND transaction_id IS NULL)),
                         CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
);

CREATE UNIQUE INDEX ux_payslip_period_driver ON payslip (period, driver_id) WHERE status <> 'CANCELLED';

CREATE TRIGGER payslip_no_delete
    BEFORE DELETE ON payslip
BEGIN
SELECT RAISE(ABORT, 'Un bulletin ne peut pas être supprimé : l''annuler avec un motif');
END;

-- Brouillon : tout se recalcule. Après validation, les montants sont figés ; seuls le statut et le paiement évoluent.
CREATE TRIGGER payslip_frozen_after_validation
    BEFORE UPDATE ON payslip
    WHEN OLD.status = 'CANCELLED'
        OR (OLD.status <> 'DRAFT' AND (NEW.period <> OLD.period OR NEW.driver_id <> OLD.driver_id
            OR NEW.gross <> OLD.gross OR NEW.net_payable <> OLD.net_payable OR NEW.employer_cost <> OLD.employer_cost
            OR NEW.advance_recovered <> OLD.advance_recovered OR NEW.shortage_recovered <> OLD.shortage_recovered
            OR NEW.other_debt_recovered <> OLD.other_debt_recovered OR NEW.its <> OLD.its
            OR NEW.employee_contributions <> OLD.employee_contributions OR NEW.status = 'DRAFT'))
BEGIN
SELECT RAISE(ABORT, 'Bulletin validé : ses montants ne se modifient plus');
END;

CREATE TABLE payslip_line (
                              id         INTEGER PRIMARY KEY AUTOINCREMENT,
                              payslip_id INTEGER NOT NULL REFERENCES payslip (id),
                              position   INTEGER NOT NULL,
                              code       TEXT    NOT NULL,
                              name       TEXT    NOT NULL,
                              contribution_group TEXT NOT NULL,
                              payer      TEXT    NOT NULL CHECK (payer IN ('EMPLOYEE', 'EMPLOYER')),
                              base       INTEGER NOT NULL CHECK (base >= 0),
                              rate_bp    INTEGER NOT NULL CHECK (rate_bp BETWEEN 0 AND 10000),
                              amount     INTEGER NOT NULL CHECK (amount >= 0),
                              UNIQUE (payslip_id, position)
);

CREATE TRIGGER payslip_line_insert_draft_only
    BEFORE INSERT ON payslip_line
    WHEN (SELECT status FROM payslip WHERE id = NEW.payslip_id) <> 'DRAFT'
BEGIN
SELECT RAISE(ABORT, 'Les lignes d''un bulletin validé sont figées');
END;

CREATE TRIGGER payslip_line_no_update
    BEFORE UPDATE ON payslip_line
BEGIN
SELECT RAISE(ABORT, 'Les lignes d''un bulletin ne se modifient pas : recalculer le brouillon');
END;

CREATE TRIGGER payslip_line_delete_draft_only
    BEFORE DELETE ON payslip_line
    WHEN (SELECT status FROM payslip WHERE id = OLD.payslip_id) <> 'DRAFT'
BEGIN
SELECT RAISE(ABORT, 'Les lignes d''un bulletin validé sont figées');
END;

-- ---- Paramètres de paie (retenues proposées) -------------------------------------------------------------------
INSERT INTO app_setting (key, value, updated_at) VALUES
                                                     ('payroll.recoverAdvances',   'true',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('payroll.recoverShortages',  'true',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('payroll.recoverOtherDebts', 'true',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('payroll.debtCapBp',         '10000', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
