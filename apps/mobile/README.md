# FMS Mobile

**Android and iOS app of FMS SaaS**

FMS Mobile puts the day-to-day operations of the fleet in the owner's and cashier's pocket: recording collections, registering transactions, checking balances and alerts, and approving payroll. The full management and reporting experience is in [FMS Web](../web/README.md). Both apps use the same [FMS API](../../fms-server/README.md) and show the same figures.

---

## What users can do

- **Record collections**: select the driver and vehicle, enter the amount and payment method (cash, MTN MoMo, Airtel Money, bank, other), see the expected amount and shortage immediately, share the receipt.
- **Register transactions**: inflow or outflow, category, account, vehicle when relevant, supporting document reference.
- **Check balances**: each account and the consolidated cash position.
- **Driver information**: contact details, current vehicle, advances, shortages and debts.
- **Vehicle information**: status, assigned driver, mileage, maintenance due, repair costs against the threshold.
- **Alerts**: expiring and expired documents, maintenance due, payout results.
- **Reports**: daily and monthly collections, account statements and dashboards, with PDF sharing.
- **Payroll** (authorised users): review and validate payroll and, on Standard and Premium, approve Mobile Money salary payments and follow their status until the provider confirms them.

What the app shows depends on the user's role and the organisation's plan, both enforced by the server.

---

## Technology

| Area | Choice |
|---|---|
| Language | TypeScript (strict mode) |
| Framework | React Native with Expo |
| API access | `@fms/api-client`, generated from the server's OpenAPI spec |
| Builds and store submission | EAS Build / EAS Submit |

Rules for contributors:

- No financial calculation on the device. Figures come from the API.
- Amounts arrive as integer minor units plus currency, and are formatted for display only.
- Authentication tokens are kept in the device's secure storage, never in plain storage.
- Mobile Money PINs are never requested or stored: payouts go through the provider's own confirmation.

---

## Getting started

### Prerequisites

- Node.js (current LTS) with npm
- The [FMS API](../../fms-server/README.md#getting-started) running and reachable from the phone or emulator
- One of:
  - **Expo Go** on an Android or iOS phone (quickest), or
  - **Android Studio** with an emulator, and/or **Xcode** with a simulator (macOS only)

### 1. Install

From `apps/`:

```bash
npm install
npm run generate:api-client
```

### 2. Configure

Copy `.env.example` to `.env` in `apps/mobile`:

| Variable | Purpose | Dev value |
|---|---|---|
| `EXPO_PUBLIC_API_URL` | Base URL of the FMS API | see below |

`localhost` on a phone or emulator is the device itself, not your computer:

- Physical phone (same Wi-Fi): `http://<your-computer-LAN-IP>:8080/api/v1`
- Android emulator: `http://10.0.2.2:8080/api/v1`
- iOS simulator: `http://localhost:8080/api/v1`

Variables prefixed with `EXPO_PUBLIC_` are embedded in the app bundle. Never put secrets in them.

### 3. Run

From `apps/`:

```bash
npm run start -w mobile
```

Then:

- scan the QR code with Expo Go, or
- press `a` to open the Android emulator, or `i` for the iOS simulator.

If the app uses a native module that Expo Go does not include, build a development client once with `npx expo run:android` / `npx expo run:ios` (from `apps/mobile`) and use it instead of Expo Go.

### Other scripts

| Command (from `apps/`) | Purpose |
|---|---|
| `npm run lint -w mobile` | ESLint |
| `npm run typecheck -w mobile` | TypeScript check |
| `npm run test -w mobile` | Unit tests |

---

## Building and publishing

From `apps/mobile`, with an Expo account and the EAS CLI:

```bash
npx eas-cli build --platform android --profile production
npx eas-cli build --platform ios --profile production
npx eas-cli submit --platform android
npx eas-cli submit --platform ios
```

Build profiles (development, preview, production) and their API URLs are defined in `eas.json`.

### Compatibility with the API

Users update store apps late, so several app versions run at the same time against the same API:

- The app sends its version with every request.
- The server rejects versions below the supported minimum with an upgrade message, which the app displays.
- Over-the-air updates may change JavaScript code only; anything that needs a new API version or native code goes through a store release.

---

## Licence

Proprietary software. © 2026 Global Underscore SAS, Brazzaville. All rights reserved.
