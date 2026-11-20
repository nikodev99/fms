-- Référentiels et paramètres : types de documents, types d'entretien, cotisations, barème ITS, provisions.
-- Taux en points de base (1 % = 100), montants en unités mineures (XAF : francs).
-- Les valeurs réglementaires portent leur source ; verified = 1 seulement si contrôlées sur un texte officiel.
-- Ne jamais modifier ce fichier une fois livré : ajouter une nouvelle migration.

-- ---- Types de documents à échéance (instructions §8) ------------------------------------------
CREATE TABLE document_type (
                               id         INTEGER PRIMARY KEY AUTOINCREMENT,
                               version    INTEGER NOT NULL DEFAULT 0,
                               name       TEXT    NOT NULL COLLATE NOCASE,
                               subject    TEXT    NOT NULL CHECK (subject IN ('VEHICLE', 'DRIVER')),
                               mandatory  INTEGER NOT NULL DEFAULT 1 CHECK (mandatory IN (0, 1)),
                               active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                               created_at TEXT    NOT NULL,
                               updated_at TEXT    NOT NULL,
                               UNIQUE (subject, name)
);

INSERT INTO document_type (name, subject, mandatory, created_at, updated_at) VALUES
                                                                                 ('Assurance',                  'VEHICLE', 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                 ('Vignette',                   'VEHICLE', 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                 ('Contrôle technique',         'VEHICLE', 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                 ('Licence de transport / taxi','VEHICLE', 1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                 ('Permis de conduire',         'DRIVER',  1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- ---- Types d'entretien (instructions §9). Périodicités laissées vides : à définir par le propriétaire.
CREATE TABLE maintenance_type (
                                  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                                  version               INTEGER NOT NULL DEFAULT 0,
                                  name                  TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                                  kind                  TEXT    NOT NULL CHECK (kind IN ('SERVICE', 'REPAIR')),
                                  default_interval_days INTEGER CHECK (default_interval_days IS NULL OR default_interval_days > 0),
                                  default_interval_km   INTEGER CHECK (default_interval_km IS NULL OR default_interval_km > 0),
                                  active                INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                                  created_at            TEXT    NOT NULL,
                                  updated_at            TEXT    NOT NULL
);

INSERT INTO maintenance_type (name, kind, created_at, updated_at) VALUES
                                                                      ('Vidange',              'SERVICE', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Révision générale',    'SERVICE', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Pneumatiques',         'SERVICE', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Freins',               'SERVICE', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Réparation mécanique', 'REPAIR',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Panne / dépannage',    'REPAIR',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Carrosserie',          'REPAIR',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                      ('Électricité',          'REPAIR',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- ---- Cotisations et taxes sur salaires (instructions §4.7, §19) -------------------------------------
-- Datées : un nouveau taux = une nouvelle ligne (même code, date d'effet plus récente).
CREATE TABLE payroll_contribution (
                                      id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                                      version            INTEGER NOT NULL DEFAULT 0,
                                      code               TEXT    NOT NULL,
                                      name               TEXT    NOT NULL,
                                      contribution_group TEXT    NOT NULL CHECK (contribution_group IN ('CNSS', 'CAMU', 'PAYROLL_TAX', 'OTHER')),
                                      payer              TEXT    NOT NULL CHECK (payer IN ('EMPLOYEE', 'EMPLOYER')),
                                      rate_bp            INTEGER NOT NULL CHECK (rate_bp BETWEEN 0 AND 10000),
                                      monthly_ceiling    INTEGER CHECK (monthly_ceiling IS NULL OR monthly_ceiling > 0),
                                      monthly_floor      INTEGER CHECK (monthly_floor IS NULL OR monthly_floor >= 0),
                                      enabled            INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
                                      effective_from     TEXT    NOT NULL,
                                      legal_source       TEXT    NOT NULL,
                                      verified           INTEGER NOT NULL DEFAULT 0 CHECK (verified IN (0, 1)),
                                      created_at         TEXT    NOT NULL,
                                      updated_at         TEXT    NOT NULL,
                                      UNIQUE (code, effective_from),
                                      CHECK (monthly_ceiling IS NULL OR monthly_floor IS NULL OR monthly_floor < monthly_ceiling)
);

-- CNSS : taux et plafonds publiés par le CLEISS (taux au 01/01/2023), repris par PwC Tax Summaries
-- (revu le 10/12/2025). Non contrôlés sur un texte officiel congolais : verified = 0.
-- TUS (7,5 %) et CAMU : désactivés par défaut, absents de l'édition Excel (mêmes résultats par défaut).
INSERT INTO payroll_contribution (code, name, contribution_group, payer, rate_bp, monthly_ceiling, monthly_floor,
                                  enabled, effective_from, legal_source, verified, created_at, updated_at) VALUES
                                                                                                               ('CNSS_PENSION_EMPLOYEE', 'CNSS — Pension vieillesse (part salariale)', 'CNSS', 'EMPLOYEE', 400, 1200000, NULL, 1,
                                                                                                                '2023-01-01', 'CLEISS, taux au 1er janvier 2023 ; repris par PwC Tax Summaries (revu le 10/12/2025). À confirmer auprès de la CNSS.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                               ('CNSS_PENSION_EMPLOYER', 'CNSS — Pension vieillesse (part patronale)', 'CNSS', 'EMPLOYER', 800, 1200000, NULL, 1,
                                                                                                                '2023-01-01', 'CLEISS, taux au 1er janvier 2023. À confirmer auprès de la CNSS.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                               ('CNSS_FAMILY', 'CNSS — Prestations familiales', 'CNSS', 'EMPLOYER', 1003, 600000, NULL, 1,
                                                                                                                '2023-01-01', 'CLEISS, taux au 1er janvier 2023. À confirmer auprès de la CNSS.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                               ('CNSS_WORK_ACCIDENT', 'CNSS — Accidents du travail', 'CNSS', 'EMPLOYER', 225, 600000, NULL, 1,
                                                                                                                '2023-01-01', 'CLEISS, taux au 1er janvier 2023. À confirmer auprès de la CNSS.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                               ('TUS', 'Taxe unique sur les salaires (TUS)', 'PAYROLL_TAX', 'EMPLOYER', 750, NULL, NULL, 0,
                                                                                                                '2025-01-01', 'PwC Tax Summaries (revu le 10/12/2025) : 7,5 % du salaire brut. La LF 2026 (art. 23) modifie seulement sa répartition. À confirmer auprès de la DGID.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                               ('CAMU_HEALTH_EMPLOYEE', 'CAMU — Assurance maladie (part salariale)', 'CAMU', 'EMPLOYEE', 227, 600000, NULL, 0,
                                                                                                                '2023-01-01', 'Loi de finances 2023 (JO édition spéciale n° 6-2023), article neuvième : 2,27 % salarié ; plafond CLEISS. Mise en œuvre à confirmer.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                               ('CAMU_HEALTH_EMPLOYER', 'CAMU — Assurance maladie (part patronale)', 'CAMU', 'EMPLOYER', 455, 600000, NULL, 0,
                                                                                                                '2023-01-01', 'Loi de finances 2023 (JO édition spéciale n° 6-2023), article neuvième : 4,55 % employeur ; plafond CLEISS. Mise en œuvre à confirmer.', 0,
                                                                                                                strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- ---- Barème ITS par part (CGI art. 116 G, 1 — loi n° 42-2025, LF 2026) : texte officiel contrôlé ---------
CREATE TABLE its_band (
                          id             INTEGER PRIMARY KEY AUTOINCREMENT,
                          version        INTEGER NOT NULL DEFAULT 0,
                          effective_from TEXT    NOT NULL,
                          up_to          INTEGER CHECK (up_to IS NULL OR up_to > 0),
                          rate_bp        INTEGER CHECK (rate_bp IS NULL OR rate_bp BETWEEN 0 AND 10000),
                          fixed_amount   INTEGER CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
                          created_at     TEXT    NOT NULL,
                          updated_at     TEXT    NOT NULL,
                          UNIQUE (effective_from, up_to),
                          CHECK ((rate_bp IS NULL) <> (fixed_amount IS NULL))
);

INSERT INTO its_band (effective_from, up_to, rate_bp, fixed_amount, created_at, updated_at) VALUES
                                                                                                ('2026-01-01',  615000, NULL, 1200, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                ('2026-01-01', 1500000, 1000, NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                ('2026-01-01', 3500000, 1500, NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                ('2026-01-01', 5000000, 2000, NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                                                                ('2026-01-01',    NULL, 3000, NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- ---- Règles de provisions (instructions §4.6), communes à la flotte. Aucune règle imposée. ---------------
CREATE TABLE allocation_rule (
                                 id         INTEGER PRIMARY KEY AUTOINCREMENT,
                                 version    INTEGER NOT NULL DEFAULT 0,
                                 name       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                                 purpose    TEXT    NOT NULL CHECK (purpose IN ('MAINTENANCE', 'MAJOR_REPAIRS', 'INSURANCE_DOCUMENTS', 'DRIVER_PAY',
                                                                                'PAYROLL_CHARGES', 'TAXES', 'LOAN_REPAYMENT', 'RESERVE',
                                                                                'OWNER_INCOME', 'OTHER')),
                                 basis      TEXT    NOT NULL CHECK (basis IN ('COLLECTIONS_PERCENT', 'NET_COLLECTIONS_PERCENT', 'FIXED_MONTHLY',
                                                                              'FIXED_MONTHLY_PER_VEHICLE', 'PER_KM')),
                                 rate_bp    INTEGER CHECK (rate_bp IS NULL OR rate_bp BETWEEN 1 AND 10000),
                                 amount     INTEGER CHECK (amount IS NULL OR amount > 0),
                                 active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                                 method     TEXT    NOT NULL,
                                 created_at TEXT    NOT NULL,
                                 updated_at TEXT    NOT NULL,
                                 CHECK ((basis IN ('COLLECTIONS_PERCENT', 'NET_COLLECTIONS_PERCENT') AND rate_bp IS NOT NULL AND amount IS NULL)
                                     OR (basis NOT IN ('COLLECTIONS_PERCENT', 'NET_COLLECTIONS_PERCENT') AND amount IS NOT NULL AND rate_bp IS NULL))
);

-- ---- Paramètres simples (table app_setting de V001) ----------------------------------------------------
INSERT INTO app_setting (key, value, updated_at) VALUES
                                                     ('regional.country',            'CG',                 strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('regional.currency',           'XAF',                strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('regional.currencyDigits',     '0',                  strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('regional.zone',               'Africa/Brazzaville', strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('fleet.repairWarningBp',       '8000',               strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('fleet.documentAlertDays',     '30',                 strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('fleet.maintenanceAlertDays',  '15',                 strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('fleet.maintenanceAlertKm',    '500',                strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('its.enabled',                 'false',              strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('its.abatementBp',             '2000',               strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('its.minimumTax',              '1200',               strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('its.legalSource',             'CGI art. 116 et 116 A à 116 G — loi n° 42-2025 du 31/12/2025 (loi de finances 2026), Journal officiel édition spéciale n° 3-2026 du 05/01/2026.',
                                                      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('backup.auto.enabled',         'true',               strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
                                                     ('backup.auto.keep',            '10',                 strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
