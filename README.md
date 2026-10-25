# Habitable

Android app that helps Boston renters document heat and repair problems so they can get them fixed.

A Bluetooth LE thermometer (or the built-in simulator) logs the room temperature. The app compares every reading against the Massachusetts heat rules (68 °F from 7 AM to 11 PM, 64 °F overnight, September 15 to May 31, never above 78 °F), charts it against outdoor weather, and opens an issue automatically when the apartment has been too cold for two hours. Tenants then send a dated notice, request an inspection, and keep every step on one timeline.

**CS 501 E1 · Mobile Application Development · Boston University · Fall 2026**
Team: **Ankita Patra** (evidence, issues, notices, PDF) and **Priyanshu Singh** (sensing, rules, charts, backend)

> Habitable is a documentation tool, not legal advice. An inspector's measurement is the official one.

| Milestone | Date | Status |
| --- | --- | --- |
| Proposal | Oct 20 | Submitted — [docs/Project_Proposal_Oct20.pdf](docs/Project_Proposal_Oct20.pdf) |
| **Update 1: vertical slice** | **Nov 10** | **This release — see [docs/UPDATE1.md](docs/UPDATE1.md)** |
| Update 2: feature-complete beta | Nov 24 | Planned |
| Final submission and showcase | Dec 8 | Planned |

---

## What works in Update 1 (the vertical slice)

**Simulator → Room → rules → chart → automatic issue**, end to end, on a real device or emulator, with Open-Meteo weather on the same chart and readings syncing to the backend.

1. **Set up** your apartment (address, unit, landlord) — stored in Room, start destination decided from DataStore.
2. **Start monitoring** — a foreground service samples the (simulated or BLE) thermometer and writes each reading to Room through `ReadingsRepository.ingest()`, which flags implausible jumps and gaps, runs the 68/64/78 rules, tracks violation periods, and **opens a heat issue automatically** after two hours below the limit.
3. **Replay cold night** — one tap feeds the proposal's Nov 9–10 night through the exact same pipeline so the demo doesn't need a cold room.
4. **Home / Sensors** — Compose Canvas chart of indoor vs. the step-shaped legal limit with violation shading and the **Open-Meteo** outdoor line; loading / error / stale states; hours-below and lowest-reading stats; data-quality card.
5. **Issues → Issue detail** — timeline of sensor events, notes and actions; the **Escalate** button that always names its next step (notice → inspection → repair reminders) and moves the issue through the legal order.
6. **More** — sensor mode (simulated / BLE), sign-in to the backend, **Sync now** (also runs every 6 h via WorkManager), privacy statement, demo reset.

Screens and the data path are described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

![Home, Sensors and Issue timeline](docs/ui_mockups_1.png)

---

## Repository layout

```
android/      Android app — Kotlin, Jetpack Compose, Room, WorkManager, Retrofit
backend/      Node.js + Express API — JWT auth, reading sync, evidence anchoring, PostgreSQL or in-memory
docs/         Proposal, Update 1 notes, backlog, architecture, working agreement, AI-use statement
.github/      CI: backend tests, Android build + unit tests on every push and PR
```

## Tech stack

| Layer | Choice | Where |
| --- | --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3, type-safe Navigation Compose | `android/app/src/main/java/.../ui` |
| State | ViewModel + StateFlow, one `UiState` per screen, `collectAsStateWithLifecycle` | `ui/*/…ViewModel.kt` |
| Local data | Room (source of truth; readings insert-only), DataStore for settings | `data/local`, `data/settings` |
| Domain | Pure-Kotlin heat rules, violation detector, data-quality + weather cross-check, chart builder | `domain/` |
| Device | Bluetooth LE thermometer in a foreground service; simulated source for demos | `data/sensor`, `service/` |
| Background | WorkManager periodic + on-demand sync | `work/SyncWorker.kt` |
| Third-party API | **Open-Meteo** hourly temperature (no key), cached in Room | `data/remote/openmeteo` |
| Backend | Node 22, Express, JWT, bcrypt, PostgreSQL (`pg`) with an in-memory store for tests | `backend/src` |
| Charts | Compose `Canvas` (step limit + violation shading); see decision in UPDATE1.md | `ui/components/TemperatureChart.kt` |

---

## Build and run

### Requirements

- **Android Studio Ladybug (2024.2) or newer** with Android SDK Platform **35** and Build-Tools 35.
- **JDK 17** (bundled with Android Studio).
- An emulator or device running **Android 10 (API 29) or newer**. Tested on the Pixel 8 API 35 emulator.
- **Node.js 20+** for the backend (22 recommended).
- No API keys are required. Open-Meteo is keyless; the backend signs its own JWTs.

### 1. Android app

```bash
cd android
cp local.properties.example local.properties   # Android Studio will add sdk.dir for you
./gradlew assembleDebug                         # or open android/ in Android Studio and press Run
./gradlew installDebug                          # install on the connected emulator/device
```

`local.properties` keys (all optional):

| Key | Default | Meaning |
| --- | --- | --- |
| `habitable.backendUrl` | `http://10.0.2.2:3000/` | Backend base URL. `10.0.2.2` is the host machine from the emulator. For a physical phone use your laptop's LAN IP, or `adb reverse tcp:3000 tcp:3000` and `http://localhost:3000/`. |
| `habitable.simulatedSensor` | `true` | Start in simulated-sensor mode. Set `false` to use a BLE thermometer (enter its MAC address under More). |

First launch opens the **Set up your apartment** screen. Then go to **Sensors → Replay cold night** for the full demo, or **Start monitoring** for live (simulated) readings every 30 seconds.

### 2. Backend (optional for the app, needed for "Sync now")

```bash
cd backend
cp .env.example .env         # set JWT_SECRET; leave DATABASE_URL unset to use the in-memory store
npm install
npm run dev                  # http://localhost:3000
```

With a PostgreSQL URL in `.env` (Neon free tier works), run `npm run migrate` once to create the tables. Deploy target is Render's free tier; the `start` script is what Render runs.

Endpoints: `GET /health`, `POST /auth/register`, `POST /auth/login`, `POST /readings` (batch, idempotent), `GET /readings`, `POST /evidence/anchor`, `GET /evidence/anchor/:sha256`, `PUT /issues`, `GET /issues`, `POST /issues/notices`. All but `/health` and `/auth/*` need `Authorization: Bearer <jwt>`.

### 3. Tests

```bash
# Android unit tests (JVM) — rules engine, violation detector, data quality, weather cross-check, simulator, chart data, decoders
cd android && ./gradlew testDebugUnitTest

# Android instrumented tests — Room pipeline end to end + Compose UI test for the Home flow (needs an emulator/device)
cd android && ./gradlew connectedDebugAndroidTest

# Backend — 13 API tests with Node's built-in runner, no database needed
cd backend && npm test
```

CI runs the backend tests and the Android build + unit tests on every push (`.github/workflows/ci.yml`).

---

## Architecture in one picture

```
 ┌──────────────── Android app (offline-first) ────────────────┐
 │  Compose screens ──▶ ViewModel (StateFlow<UiState>)          │
 │        ▲                       │                             │
 │        │ Flow<…>               ▼                             │
 │  ┌──────────────────── Repositories ────────────────────┐    │
 │  │ Readings · Weather · Issues · Apartment · Sync       │    │
 │  └───────┬──────────────┬───────────────┬───────────────┘    │
 │          ▼              ▼               ▼                    │
 │   Room (source     Retrofit →      Retrofit →                │
 │   of truth)        Open-Meteo      Habitable backend          │
 │          ▲                                                   │
 │  SensorSource (Simulated | BLE) ◀── Foreground service       │
 │  WorkManager SyncWorker (6 h + on demand)                     │
 └──────────────────────────────────────────────────────────────┘
```

Full description, data model and the end-to-end data path: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Project documents

- [docs/UPDATE1.md](docs/UPDATE1.md) — Nov 10 update: demo script, what's done, backlog status, risks, blockers, decisions
- [docs/BACKLOG.md](docs/BACKLOG.md) — user stories, acceptance criteria, owners, sprint status
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — layers, data model, data flow, accessibility and privacy notes
- [docs/WORKING_AGREEMENT.md](docs/WORKING_AGREEMENT.md) — roles, scrum-master rotation, branching and review rules
- [docs/AI_USE.md](docs/AI_USE.md) — generative AI disclosure
- [docs/Project_Proposal_Oct20.pdf](docs/Project_Proposal_Oct20.pdf) — the graded proposal

## Privacy and security

- Photos and the apartment address never leave the phone. Only temperature readings (and, later, photo hashes) are uploaded, and only after the user signs in.
- Room's database and the evidence folder are excluded from Android Auto Backup (`res/xml/backup_rules.xml`).
- Secrets live in `android/local.properties` and `backend/.env`, both git-ignored; samples are provided. The only secret on the device is the backend's own short-lived JWT.
- Permissions are requested only when needed: `POST_NOTIFICATIONS` when monitoring starts, `BLUETOOTH_SCAN/CONNECT` only in BLE mode. Simulated mode asks for nothing.
- Every input is validated (title length, email format, reading ranges) on the phone and again on the server.

## Outside resources and licenses

| Resource | Use | License |
| --- | --- | --- |
| [Open-Meteo](https://open-meteo.com/) | Hourly outdoor temperature | CC BY 4.0 (non-commercial API, attribution in app) |
| AndroidX / Jetpack (Compose, Room, Navigation, WorkManager, DataStore, Lifecycle) | App framework | Apache 2.0 |
| Retrofit, OkHttp | Networking | Apache 2.0 |
| kotlinx.serialization, kotlinx.coroutines | JSON, async | Apache 2.0 |
| Express, helmet, cors, jsonwebtoken, bcryptjs, pg, dotenv, supertest | Backend | MIT |
| Truth, Turbine, JUnit | Tests | Apache 2.0 / EPL |
| [105 CMR 410.180](https://regulations.justia.com/states/massachusetts/105-cmr/title-105-cmr-410-000/section-410-180), [MGL c.111 §127L](https://malegislature.gov/Laws/GeneralLaws/Chapter111/Section127L), [MassLegalHelp](https://www.masslegalhelp.org/) | Heat rules and tenant process | Public documents |

Habitable's own code is released under the [MIT License](LICENSE).

## Generative AI use

Generative AI was used for guidance with Compose code organization, UI refinement, debugging, test-case review, and README wording. I reviewed, understood, tested, and adjusted the final code used in this application.


