# FMS Desktop

**Standalone Windows edition of FMS**

FMS Desktop is a complete fleet business management application that runs entirely offline on a local SQLite database. It is sold as a lifetime licence: no subscription and no internet connection required for day-to-day use.

It manages vehicles, drivers, daily collections, cash accounts, transactions, maintenance, payroll, loans, provisions and profitability, using the same business rules as every other FMS edition. See the [root README](../README.md) for the product family.

---

## Features

### Vehicles

- Full vehicle register: registration number, make and model, category, commercial use, acquisition date and method, purchase price, financing institution, mileage, estimated resale value.
- Vehicle statuses: Active, Under maintenance, Temporarily unavailable, Immobilised, Sold, Inactive — changed only through an authorised action, with history kept.
- Sold or deactivated vehicles remain fully consultable.
- **Repair threshold** per vehicle: when accumulated repair and breakdown costs reach the limit, the vehicle is flagged for a replacement or resale assessment (an indicator for the owner, never an automatic decision).
- Vehicle assessment comparing repair costs, purchase price, resale value, revenue, operating costs and profitability.

### Drivers and assignments

- Driver records: identity, contact details, driving licence and expiry, contract type, remuneration arrangement, CNSS and ITS settings, employment status.
- Driver account showing advances, shortages, other debts and recoveries.
- Vehicle–driver assignments with full history: one active primary driver per vehicle, plus replacement and night drivers.
- Every collection is attributed to the driver who actually brought it in, never to the vehicle's primary driver by default.

### Daily collections and cashier

- Recording of driver deposits, including several deposits by the same driver on the same day.
- Payment methods: cash, MTN Mobile Money, Airtel Money, bank transfer and other configurable methods.
- Expected amount calculated from the vehicle and driver arrangement; shortage or surplus computed automatically.
- Daily reconciliation: shortages are posted to the driver's account, surpluses offset existing debts.
- Same-day corrections by authorised users, with old value, new value, reason, user and timestamp kept in the audit log.
- Previous-day collections are never edited or deleted: they are corrected with a linked credit memo.
- Collection receipts.

### Transactions (inflows and outflows)

- One register for all money movements. The user picks the direction first (inflow or outflow), then only the matching categories are shown.
- Configurable categories, each with a nature (operating, financing, payroll, etc.), so loan principal, vehicle purchases, advances and transfers are never counted as operating expenses.
- Repairs and maintenance linked to the vehicle concerned.
- Validated transactions are never edited: they are cancelled by a linked reversal.

### Accounts and cash management

- Separate balances for cash, MTN Mobile Money, Airtel Money, bank accounts and any other configured account, always recalculated from the ledger.
- Consolidated cash position across eligible accounts.
- Internal transfers between accounts (e.g. cash to bank) that move money without affecting the consolidated position or the business result.
- **Cashier closing**: opening balance, inflows, outflows, transfers, expected balance, counted balance, difference and explanation. A closed period cannot be posted to.

### Allocations and provisions

- Configurable allocation rules (maintenance, major repairs, insurance and documents, payroll charges, taxes, loan repayments, reserves, owner's income) using different calculation methods: percentage of collections, fixed amount, and other documented bases.
- Provisions are tracked separately from actual spending: setting aside 15 % for maintenance never shows up as money already spent on repairs.
- Allocated versus actually spent, by provision.

### Payroll

- Remuneration models: fixed salary, percentage of collections, fixed plus commission, and other configurable arrangements.
- Automatic detection of outstanding salary advances, so a driver is never proposed a full salary that has already been partly advanced.
- Proposed recovery of shortages and other debts, within configurable limits.
- Payroll taxes and contributions (ITS, CNSS employee and employer shares, other charges): each one can be switched on or off, with editable rates, ceilings and effective dates, and applied only to drivers who are subject to them.
- Payroll validation, then manual registration of the payment (account, date, reference) once the salary has been paid outside the application.
- Payroll cost (gross pay plus employer charges) is counted in the operating result; net salaries paid are cash movements.

### Vehicle loans and simulations

- Loans attached to financed vehicles: lender, principal, rate, term, frequency, repayment schedule.
- Payment of instalments, principal and interest kept apart, outstanding balance always up to date. Only interest is counted as a cost; principal is a financing movement.
- Per-vehicle simulations: loan simulation and vehicle profitability simulation before buying or financing a vehicle.

### Maintenance and mileage

- Maintenance plans by date, by mileage or both (oil change, servicing, tyres, brakes, other configurable tasks).
- Mileage log, maintenance history and upcoming or overdue tasks.
- Every repair feeds the vehicle's accumulated repair cost and threshold.

### Documents and compliance

- Expiry tracking for vehicle documents (insurance, road tax, technical inspection, transport licence, others) and driver documents (licence, permits).
- Configurable alerts for upcoming and expired documents.
- Compliance status per vehicle and driver; expired mandatory documents raise warnings and can restrict the vehicle's operational status.

### Dashboards and reports

- Owner dashboard: collections, revenue, operating expenses, net operating result, provisions, balances by account, consolidated cash, driver debts, advances, loan balances, maintenance costs, vehicle profitability, driver performance.
- Cashier view of the current day: opening balance, inflows, outflows, deposits, expected and counted balance.
- Fleet view: vehicle statuses, upcoming maintenance, document expiries, repair thresholds, assignments.
- **Net result versus provisions**: actual result, expected allocations, amounts reserved, actual expenses, outstanding obligations and available cash side by side.
- Reports with date filters, monthly timelines and year-on-year comparisons: daily and monthly collections, transaction and account statements, cashier closings, driver statements, outstanding debts, payroll, vehicle profitability, maintenance and document registers.
- Printing, PDF generation, Excel and CSV exports. Generated documents carry the company name and logo, title, reference, period, currency and generation date.

### Users, security and audit

- User accounts with roles and permissions; sensitive operations require authorisation.
- Immutable audit log of every important change (who, when, old value, new value, reason).
- No physical deletion: records are deactivated, reversed or credited, never erased.
- Backup and restoration of the local database.

### Interface

- Professional, consistent interface across all screens, with light, dark and system themes.
- Fully resizable window: content fills and reorganises itself when the window is maximised or resized.
- Designed for users with no accounting or technical background; the cashier workflow is kept as short as possible.

---

## What FMS Desktop does not do

- **No Mobile Money payouts.** The application calculates salaries and records payments made outside it (cash, bank, or the MTN/Airtel apps). It never sends payment instructions to a Mobile Money provider. Direct salary disbursement is exclusive to FMS SaaS.
- No multi-branch consolidation, public API, webhooks, multi-vehicle scenario planning or fleet expansion simulation — these belong to FMS SaaS Premium.
- No internet connection is needed for normal use.

Tax and contribution rates shipped with the application are defaults to be checked against current legislation. They are editable and each carries its effective date and source; FMS does not provide tax or legal advice.

---

## Technology

| Area | Choice |
|---|---|
| Language | Java 17 |
| UI | JavaFX 21 (LTS line) |
| Dashboard tiles | TilesFX 17.1.31 |
| Icons | Ikonli 12.4.0 — Material Design Icons (`materialdesign2`) |
| Form validation | ValidatorFX 1.0.0 with an in-house form engine (typed fields, exact money input) |
| Dependency injection | Guice 7 |
| Database | SQLite via JDBC, generic `JdbcRepository<T>` |
| Migrations | In-house engine (`V00N__name.sql` scripts) |
| Business rules | `fms-domain` (shared with `fms-server`) |
| Packaging | jpackage with a jlink runtime (JDK + JavaFX) |

Money is never stored as a floating-point number: amounts are `long` minor units with their currency (`Money`), stored as `INTEGER`.

---

## Getting started

### Prerequisites

- **JDK 17** (Temurin or any OpenJDK 17 build). `JAVA_HOME` must point to it.
- **Maven 3.9+**
- Windows 10/11 for packaging. Running from source also works on macOS and Linux.

JavaFX does not need to be installed separately: it is pulled from Maven Central.

### Build

From the repository root:

```bash
mvn -pl fms-desktop -am clean install
```

This builds `fms-domain` and `fms-desktop` and runs their tests. Business-rule and calculation tests live in `fms-domain` and need no UI.

### Run from Maven

```bash
mvn -pl fms-desktop -am javafx:run
```

### Run from IntelliJ IDEA

1. Open the repository root (see the [root README](../README.md#opening-in-intellij-idea)).
2. Set the SDK to JDK 17.
3. Run the `Launcher` class in `fms-desktop`.

Always start the application through `Launcher`, not through the JavaFX `Application` class directly. The application runs on the classpath (no `module-info`), and starting the `Application` subclass directly fails with *"JavaFX runtime components are missing"*.

### First launch

On first launch, FMS creates its local SQLite database, applies every migration and opens the setup screen. No server, account or internet connection is needed.

To start again from an empty database, close the application and delete (or rename) the database file; it is recreated on the next launch.

---

## Building the Windows installer

The installer bundles its own Java runtime, so end users install nothing else.

1. On a Windows machine with the [WiX Toolset](https://wixtoolset.org/) installed, build the module: `mvn -pl fms-desktop -am clean package`.
2. Run the jpackage step for `fms-desktop` (jlink runtime with the JDK and JavaFX modules).

The output is a standard Windows installer (`.msi` / `.exe`) with Start-menu and desktop shortcuts. jpackage cannot produce a Windows installer from Linux or macOS.

Desktop releases are tagged (`desktop-vX.Y.Z`) so that a domain change made for the SaaS is shipped in a desktop version only when intended.

---

## Module structure

```
fms-desktop/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/…/
    │   │   ├── Launcher            Entry point
    │   │   ├── …                   Shared engines: forms, CRUD screens, Money input,
    │   │   │                       repositories, migrations, audit, themes
    │   │   └── features/<module>/  One folder per feature (vehicle, driver, collection, …)
    │   └── resources/
    │       ├── db/migration/       V001__…sql, V002__…sql, …
    │       └── messages.properties All user-facing text
    └── test/
```

`features/vehicle` is the reference implementation every management screen follows: domain → migration → repository → service → editor → CRUD screen.

The domain never produces display text: validation errors are message keys (`FieldError(field, messageKey, args)`) resolved from `messages.properties`, which keeps the application ready for other languages.

---

## Data, backups and upgrades

- All data stays on the user's computer in a single SQLite file, with foreign keys, constraints and indexes enforced.
- Every write and its audit entry are saved in the same database transaction. The audit table is protected by triggers and cannot be modified.
- Records carry a `version` column for optimistic locking and `created_at` / `updated_at` timestamps.
- **Migrations**: each script is checksummed (SHA-256). Before applying an upgrade, FMS backs up the database automatically. It refuses to open a database created by a newer version of the application, so data is never silently downgraded.
- Users can back up and restore their data from within the application at any time.

---

## Design principles

- **Financial history is immutable.** Corrections are made by reversals and credit memos linked to the original entry.
- **Cash, profit and provisions are kept apart.** A business can be profitable and short of cash, or hold cash that is already owed; FMS shows each figure separately and never mixes them.
- **One ledger.** All money movements go through a single transaction ledger; balances are always recalculated, never typed in.
- **Rules live in the domain.** No financial calculation sits in a JavaFX controller.

---

## Licence

Proprietary software. © 2026 Global Underscore SAS, Brazzaville. All rights reserved.
