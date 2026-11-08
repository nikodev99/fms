# FMS Server — SaaS API

**Multi-tenant backend of FMS SaaS**

`fms-server` is the REST API behind the FMS web and mobile apps. It hosts every customer organisation, enforces their subscription, runs all business rules and financial calculations, executes Mobile Money salary payouts, generates documents and keeps the audit trail.

The web and mobile apps never calculate financial figures themselves: balances, expected collections, payroll, provisions and reports all come from this API. The rules and calculations come from `fms-domain`, the same module the desktop edition uses, so a figure is identical in every FMS edition. See the [root README](../README.md) for the product family.

---

## What the API does

### Fleet and financial management

Everything the desktop edition does, for many organisations at once:

- **Vehicles**: register, statuses (Active, Under maintenance, Temporarily unavailable, Immobilised, Sold, Inactive), repair threshold and replacement assessment, history kept after sale or deactivation.
- **Drivers and assignments**: driver records, one active primary driver per vehicle plus replacement and night drivers, full assignment history, driver accounts (advances, shortages, debts, security deposits).
- **Daily collections**: multiple deposits per driver per day, expected amount, shortage or surplus, same-day corrections with audit, credit memos for previous days, receipts.
- **Transactions**: inflows and outflows with direction-filtered categories, operating vs financing distinction, linked reversals instead of edits.
- **Accounts**: cash, MTN Mobile Money, Airtel Money, bank and other accounts; balances recalculated from one ledger; consolidated cash position; internal transfers; cashier closing.
- **Allocations and provisions**: configurable rules and calculation methods, allocated vs actually spent.
- **Payroll**: fixed, percentage, fixed plus commission; advances and debt recovery; ITS, CNSS and other contributions, each switchable with effective-dated rates.
- **Loans**: schedules, instalment payments, principal and interest kept apart, outstanding balance.
- **Maintenance, mileage and documents**: date and mileage-based plans, expiry tracking and alerts for vehicle and driver documents.

### Organisations and tenancy

- Every organisation's data is isolated. Every query and every write is scoped to the caller's organisation in the persistence layer, not left to individual endpoints.
- Branches / operating locations (Premium).
- Country, currency, currency precision, time zone, tax rules, payroll contributions and document requirements configured per organisation.

### Users, roles and audit

- Owner, Cashier, Accountant and Fleet manager roles, with finer permissions on higher plans.
- Authorisation on every sensitive operation; immutable audit log (who, when, old value, new value, reason).
- No physical deletion of validated financial records.

### Subscriptions and entitlements

All plan restrictions go through one entitlement service. Checks are enforced on the server: hiding a button in the app is never the protection.

| | Basic | Standard | Premium |
|---|---|---|---|
| Core fleet, collections, transactions, accounts, cashier closing | ✓ | ✓ | ✓ |
| Basic provisions, profitability, payroll calculation, loan schedules | ✓ | ✓ | ✓ |
| Daily/monthly collection reports, PDF receipts, CSV | ✓ | ✓ | ✓ |
| Detailed provisions, driver comparisons, deposits, replacement assessment | | ✓ | ✓ |
| Mobile Money salary payouts (individual and batch) | | ✓ | ✓ |
| Payslips, payroll registers, Excel exports, SYSCOHADA journal export | | ✓ | ✓ |
| Accountant and Fleet manager roles, notifications, bulk import | | ✓ | ✓ |
| Scheduled payouts, maker-checker approvals, payment limits | | | ✓ |
| Forecasting, scenarios, fleet expansion simulation | | | ✓ |
| Custom templates, scheduled and bulk reports | | | ✓ |
| Multiple branches, API access, webhooks | | | ✓ |

- Features are identified by entitlement keys (`mobile_money_payouts`, `batch_salary_payments`, `scheduled_salary_payments`, `multi_level_payment_approval`, `advanced_reports`, `custom_document_templates`, `bulk_document_generation`, `scheduled_reports`, `syscohada_export`, `api_access`, `webhook_access`, `multi_branch`, `advanced_forecasting`).
- Plans, entitlements and limits (vehicles, users, locations, storage) are data, editable by platform administrators without a deployment.
- Usage is tracked and customers are warned before reaching a limit. Exceeding a limit never deletes data.
- When a subscription expires, data is preserved, access becomes restricted under documented rules, and export remains available. Security and financial integrity never depend on the plan.

### Mobile Money salary payouts

Available from Standard. Supported providers depend on API availability and commercial agreements; MTN Mobile Money and Airtel Money are the initial targets.

1. Payroll is calculated and validated (advances and deductions applied).
2. The authorised user selects the provider and submits the payment.
3. The instruction is sent with an idempotency key; duplicate submissions are rejected.
4. The provider's confirmation is processed and reconciled.
5. Only a confirmed success marks the salary as paid. Payslip and receipt follow.

Payment statuses: Draft, Pending approval, Approved, Submitted, Pending provider confirmation, Successful, Failed, Reversed, Cancelled.

- Payroll calculation and payout execution are separate records.
- Provider fees are recorded separately from salaries.
- Failed payments are never treated as paid; retries are controlled.
- Customers' Mobile Money PINs are never collected or stored.

### Documents and exports

- Financial, payroll, vehicle and management documents in PDF, Excel and CSV, according to the plan.
- Every document carries the organisation's name and logo, title, reference, period, currency, generation date and responsible user.
- Document history per organisation; files are served only to authorised users, never through public or predictable URLs.
- SYSCOHADA-oriented journal exports from configurable category-to-account mappings. Every exported journal is balanced (total debits = total credits). Mappings must be checked by an accountant; FMS does not define account numbers.

### Notifications and integrations

- Email and in-app notifications: document expiries, maintenance due, payout results, subscription events.
- Public API and webhooks for Premium organisations.

---

## Technology

| Area | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1 |
| Security | Spring Security |
| Database | PostgreSQL |
| Migrations | Flyway (`src/main/resources/db/migration`, `V001__name.sql`) |
| Business rules | `fms-domain` (shared with `fms-desktop`) |
| API description | OpenAPI 3, served by the application; consumed by `apps/api-client` |
| Packaging | Docker image (Spring Boot buildpacks) |

**API conventions**

- JSON, versioned paths (`/api/v1/...`).
- Money is sent as an integer amount in minor units plus an ISO 4217 currency code — never as a decimal number.
- Dates in ISO 8601; business dates are interpreted in the organisation's time zone.
- Validation errors return message keys and field names, so clients display text in their own language.

---

## Getting started

### Prerequisites

- JDK 17 and Maven 3.9+
- Docker (for the local PostgreSQL database)

### 1. Start PostgreSQL

From the repository root:

```bash
docker compose up -d postgres
```

### 2. Configure

The defaults in the `dev` profile point to the local database. Override with environment variables when needed:

| Variable | Purpose | Dev default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` | `dev` |
| `FMS_DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/fms` |
| `FMS_DB_USERNAME` | Database user | `fms` |
| `FMS_DB_PASSWORD` | Database password | `fms` |
| `FMS_AUTH_SECRET` | Token signing secret | generated for dev only |
| `FMS_STORAGE_PATH` | Where generated documents are stored | `./data/documents` |
| `FMS_CORS_ORIGINS` | Allowed web origins | `http://localhost:5173` |
| `FMS_MOBILE_MONEY_ENABLED` | Enables payout providers | `false` |
| `FMS_MTN_MOMO_*` | MTN MoMo API credentials and environment | sandbox / unset |
| `FMS_AIRTEL_MONEY_*` | Airtel Money API credentials and environment | sandbox / unset |

Never commit real credentials. In production, secrets come from the hosting platform's secret store.

### 3. Run

```bash
mvn -pl fms-server -am spring-boot:run
```

Flyway applies all migrations at startup.

- API: http://localhost:8080/api/v1
- Interactive documentation: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

### 4. Test

```bash
mvn -pl fms-server -am verify
```

Financial rules are tested in `fms-domain`. Server tests cover tenancy isolation, entitlement enforcement, payout idempotency and duplicate prevention, reversals and API contracts.

---

## Module structure

Packages are organised by business area, each with its controller, application service and persistence:

```
fms-server/src/main/java/…/
├── organisation/    Organisations, branches, settings
├── identity/        Users, roles, permissions, authentication
├── subscription/    Plans, entitlements, usage, billing state
├── fleet/           Vehicles, drivers, assignments, maintenance, mileage, documents
├── collection/      Collections, credit memos, daily reconciliation
├── treasury/        Accounts, transactions, transfers, cashier closing, provisions
├── payroll/         Payroll periods, advances, deductions, contributions
├── payout/          Mobile Money providers, payment instructions, reconciliation
├── loan/            Loans and schedules
├── document/        Document generation and storage
├── accounting/      Account mappings, journal exports
├── notification/    Email and in-app notifications
└── audit/           Audit log
```

---

## Deployment

```bash
mvn -pl fms-server -am spring-boot:build-image
```

Run the image with the production environment variables and a managed PostgreSQL database. Migrations run automatically on startup.

- **Backward-compatible migrations.** During a deployment the old and new versions may run side by side: add columns and backfill in one release, remove old ones in a later release.
- **Backward-compatible API.** Users update mobile apps late. Breaking changes go into a new API version, or the server rejects app versions below a supported minimum with a clear upgrade message.
- **Payout callbacks** must be reachable by the providers over HTTPS.
- Database backups and restoration are tested regularly.

---

## Licence

Proprietary software. © 2026 Global Underscore SAS, Brazzaville. All rights reserved.
