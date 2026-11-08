# FMS Web

**Browser application of FMS SaaS**

FMS Web is the complete management and reporting interface of FMS SaaS. Owners, cashiers, fleet managers and accountants use it to run the whole fleet business from a browser. It talks only to the [FMS API](../../fms-server/README.md); every figure it shows is calculated by the server.

---

## What users can do

### Daily operations

- Record driver collections (several per driver per day, any payment method), see expected amounts and shortages immediately, print or download receipts.
- Correct same-day collections with a reason; issue credit memos for previous days.
- Record transactions: choose inflow or outflow first, then only the matching categories appear.
- Transfer money between accounts (cash, MTN MoMo, Airtel Money, bank).
- Close the cash register: expected vs counted balance, difference and explanation.

### Fleet

- Manage vehicles, statuses, mileage, maintenance plans and repairs.
- Assign drivers (primary, replacement, night) and consult assignment history.
- Track vehicle and driver documents, with alerts before and after expiry.
- See vehicles approaching their repair threshold and open a replacement assessment.

### Drivers and payroll

- Manage driver records, contracts and remuneration arrangements.
- Consult each driver's account: advances, shortages, debts, recoveries, security deposit.
- Prepare, review and validate payroll, with advances, deductions, ITS and CNSS applied.
- Record salaries paid outside the platform, or pay them directly through MTN Mobile Money or Airtel Money (Standard and Premium), and follow each payment until the provider confirms it.

### Finance and reports

- Balances by account and consolidated cash position.
- Provisions: allocated vs actually spent.
- Net result versus provisions: result, allocations, reserves, expenses, obligations and available cash side by side.
- Vehicle and driver profitability, loan balances and schedules.
- Reports with date filters, monthly timelines and year-on-year comparisons; PDF, Excel and CSV according to the plan; document history.
- SYSCOHADA-oriented journal exports (Standard and Premium).
- Forecasts, scenarios and fleet expansion simulation (Premium).

### Dashboards by role

| Role | Dashboard |
|---|---|
| Owner | Collections, revenue, expenses, net result, provisions, cash by account, driver debts, loans, vehicle and driver performance |
| Cashier | Current session: opening balance, inflows, outflows, deposits, expected and counted balance |
| Fleet manager | Vehicle statuses, upcoming maintenance, document expiries, repair thresholds, assignments |
| Accountant | Payroll periods, transactions, unreconciled accounts, driver liabilities, export status |

### Organisation and subscription

- Users, roles and permissions; audit history.
- Organisation settings: currency, time zone, tax and contribution rates, categories, allocation rules, document templates (Premium).
- Subscription page: current plan, status, renewal date, usage (vehicles, users, storage) and available upgrades.
- Features not included in the plan stay visible with the plan that unlocks them. Upgrade prompts never interrupt a financial operation in progress.

---

## Technology

| Area | Choice |
|---|---|
| Language | TypeScript (strict mode, no implicit `any`) |
| UI | React |
| Build | Vite |
| API access | `@fms/api-client`, generated from the server's OpenAPI spec |

Rules for contributors:

- No financial calculation in the browser. If a figure is needed, it comes from the API.
- Amounts arrive as integer minor units plus currency, and are formatted for display only.
- Text comes from translation files, not hard-coded strings.

---

## Getting started

### Prerequisites

- Node.js (current LTS) with npm
- The [FMS API](../../fms-server/README.md#getting-started) running locally

### 1. Install

From `apps/`:

```bash
npm install
npm run generate:api-client
```

Regenerate the client whenever the API changes.

### 2. Configure

Copy `.env.example` to `.env.local` in `apps/web`:

| Variable | Purpose | Dev value |
|---|---|---|
| `VITE_API_URL` | Base URL of the FMS API | `http://localhost:8080/api/v1` |

Only variables prefixed with `VITE_` reach the browser. Never put secrets in them.

### 3. Run

From `apps/`:

```bash
npm run dev -w web
```

Open http://localhost:5173.

### Other scripts

| Command (from `apps/`) | Purpose |
|---|---|
| `npm run lint -w web` | ESLint |
| `npm run typecheck -w web` | TypeScript check |
| `npm run test -w web` | Unit tests |
| `npm run build -w web` | Production build in `apps/web/dist` |
| `npm run preview -w web` | Serve the production build locally |

---

## Deployment

`npm run build -w web` produces static files in `apps/web/dist`. Serve them from any static host, CDN or reverse proxy:

- Route every unknown path to `index.html` (single-page application).
- Set `VITE_API_URL` at build time to the production API.
- Serve over HTTPS; the API's `FMS_CORS_ORIGINS` must include the web app's origin.

No Node.js server is needed in production.

---

## Licence

Proprietary software. © 2026 Global Underscore SAS, Brazzaville. All rights reserved.
