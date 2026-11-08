# FMS — Fleet Management System

FMS is a complete business management platform for vehicle owners and small-to-medium fleet operators: vehicles, drivers, daily collections, cash accounts, transactions, maintenance, payroll, loans, provisions and profitability.

It is built first for taxi operators in Brazzaville (Republic of the Congo) and also supports private hire, rental, delivery, company and other commercial vehicles. Country, currency, tax and payroll settings are configurable; XAF and the Africa/Brazzaville time zone are the defaults.

Every FMS edition is designed to help an owner answer five questions:

1. How much money has my fleet generated?
2. How much has it actually cost me to operate?
3. How much should I have set aside for future obligations?
4. How much money do I really have available?
5. Which vehicles and drivers are contributing positively to my business?

---

## Editions

| Edition | What it is | Licence model | In this repository |
|---|---|---|---|
| **FMS Desktop** | Offline Windows application (JavaFX + SQLite) | Lifetime licence | [`fms-desktop`](fms-desktop/README.md) |
| **FMS SaaS — API** | Multi-tenant backend (Spring Boot + PostgreSQL) | Subscription (Basic / Standard / Premium) | [`fms-server`](fms-server/README.md) |
| **FMS SaaS — Web** | Full management and reporting app in the browser | Subscription | [`apps/web`](apps/web/README.md) |
| **FMS SaaS — Mobile** | Android / iOS app for day-to-day operations | Subscription | [`apps/mobile`](apps/mobile/README.md) |
| FMS Excel | `.xlsm` workbook (VBA) | One-time purchase | Separate project |

All editions share the same business rules and financial definitions. A collection, a transaction, a provision or a payroll figure means the same thing everywhere; the SaaS adds capabilities (multi-user teams, Mobile Money salary payouts, advanced documents, integrations) on top of the same rules.

---

## Repository layout

```
fms/
├── pom.xml              Maven parent: fms-domain, fms-desktop, fms-server
├── fms-domain/          Shared business rules and financial calculations (pure Java 17)
├── fms-desktop/         Windows desktop application (JavaFX, SQLite)
├── fms-server/          SaaS REST API (Spring Boot, PostgreSQL)
├── apps/                npm workspace (TypeScript) — not part of the Maven build
│   ├── package.json
│   ├── api-client/      Typed API client generated from fms-server's OpenAPI spec
│   ├── web/             React web app (Vite)
│   └── mobile/          React Native app (Expo)
├── docker-compose.yml   Local PostgreSQL for development
└── docs/
    └── adr/             Architecture Decision Records
```

### What is shared, and what is not

- **`fms-domain`** holds the rules and calculations used by both Java editions. It contains no UI, no SQL, no framework code, no tenancy and nothing SaaS-specific, so it ships safely inside the desktop application.
- **Persistence is per edition.** The desktop uses SQLite with its own migration engine; the server uses PostgreSQL with Flyway.
- **Subscriptions, tenancy and Mobile Money payouts live only in `fms-server`.** The desktop never contains payout code.
- **The web and mobile apps never compute financial figures.** Balances, expected collections, payroll and reports come from the API. The TypeScript apps display and capture data.
- **Amounts** are exchanged as integer minor units with a currency code, never as decimals.

---

## Prerequisites

| Tool | Needed for |
|---|---|
| JDK 17 | `fms-domain`, `fms-desktop`, `fms-server` |
| Maven 3.9+ | Java build |
| Node.js (current LTS) with npm | `apps/*` |
| Docker | Local PostgreSQL (`fms-server`) |
| Android Studio and/or Xcode (macOS) | Running the mobile app on an emulator or simulator (optional with Expo Go) |
| WiX Toolset (Windows) | Building the desktop installer only |

---

## Quick start

```bash
git clone <repository-url> fms
cd fms

# Java: build and test everything
mvn clean install
```

**Desktop**

```bash
mvn -pl fms-desktop -am javafx:run
```

**API**

```bash
docker compose up -d postgres
mvn -pl fms-server -am spring-boot:run
```

API documentation: http://localhost:8080/swagger-ui.html

**Web and mobile** (with the API running)

```bash
cd apps
npm install
npm run generate:api-client
npm run dev -w web          # http://localhost:5173
npm run start -w mobile     # Expo: scan the QR code or open an emulator
```

Each component's README has the full setup, configuration and build instructions.

---

## Opening in IntelliJ IDEA

Open the repository root (the folder containing the parent `pom.xml`). IntelliJ imports the three Maven modules and detects `apps/web`, `apps/mobile` and `apps/api-client` from their `package.json` files, so everything appears in one project.

- Project SDK: JDK 17.
- `node_modules` must be excluded from indexing (IntelliJ normally does this automatically).
- npm scripts can be run from the **npm** tool window.
- JavaScript/TypeScript debugging and test runners require IntelliJ IDEA Ultimate. Without it, use the browser dev tools and run tests from the terminal.

---

## Building only what you ship

| To build | Command | Output |
|---|---|---|
| Desktop installer | `mvn -pl fms-desktop -am package`, then jpackage (Windows only) | `.msi` / `.exe` |
| API | `mvn -pl fms-server -am spring-boot:build-image` | Docker image |
| Web | `npm run build -w web` (in `apps/`) | Static files in `apps/web/dist` |
| Mobile | `eas build` (in `apps/mobile`) | Android / iOS store builds |

A change to `fms-domain` rebuilds and retests both the desktop and the server, so a rule cannot drift between editions unnoticed.

---

## Documentation

- [`docs/adr`](docs/adr) — architecture decisions (ADR-001: desktop architecture)
- Component READMEs: [desktop](fms-desktop/README.md) · [API](fms-server/README.md) · [web](apps/web/README.md) · [mobile](apps/mobile/README.md)

---

## Licence

Proprietary software. © 2026 Global Underscore SAS, Brazzaville. All rights reserved.

No part of this code may be copied, redistributed or used in a commercial product without written permission from Global Underscore.
