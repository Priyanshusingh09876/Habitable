# Project Update 1 — November 10, 2026

**Working vertical slice and first end-to-end data path.**
Team: Ankita Patra, Priyanshu Singh · **Scrum master this week: Ankita Patra** · Repo: github.com/Priyanshusingh09876/Habitable
Slides: [Update1_Slides.pdf](Update1_Slides.pdf) · Tests: [TESTING.md](TESTING.md) · Backlog: [BACKLOG.md](BACKLOG.md)

## 0. Requirement checklist

| Ron's requirement | Where we show it |
| --- | --- |
| End-to-end user flow in a running app | Live demo, steps 1–10 below |
| Compose UI + navigation | 7 Compose destinations, type-safe Navigation Compose, bottom bar + detail screens (no XML views or fragments) |
| Architecture: ViewModel, repository, async/persistent state | §2 and the architecture slide; `HomeViewModel` combines Room flows into one `StateFlow<HomeUiState>` |
| API **and** persistence evidence | Open-Meteo (third-party API, cached in Room) · Room (7 tables) · DataStore (settings, onboarding) · our own Express API (sync) |
| Completed / in progress / remaining work | §3 |
| Prioritised backlog, scrum master, risks, blockers, scope changes | §3–§5, BACKLOG.md, GitHub Issues + Project board |
| Repository evidence | §6: commits, branches, pull requests, issues, project board, CI |
| Privacy, permissions, API, device, usability, schedule risks | §5 |
| Backup plan | §1, last paragraph |
| Both members substantive | "Who presents what" in §1; each of us has a PR merged for this update |

## 1. What we're demonstrating (≈ 5 minutes of working software)

The flow: **simulated thermometer → Room → heat rules → chart with Open-Meteo weather → automatic heat issue → notice email → Escalate → sync readings and issues to our backend.** Every arrow is real code in this repo; nothing on screen is hard-coded.

| Step | What the audience sees | What is happening underneath |
| --- | --- | --- |
| 1. Fresh install | *Set up your apartment* form with validation | DataStore `onboarded=false` picks the start destination; `SetupViewModel` validates; `ApartmentRepository.save()` writes `apartments` + a `sensors` row to Room. |
| 2. Sensors → **Start monitoring** | "Live" chip with a pulsing dot, a persistent notification, a new point every 30 s | `SensorForegroundService` collects `SimulatedSensorSource(Live)`; each sample goes through `ReadingsRepository.ingest()`. |
| 3. Rotate the phone | Chart and stats survive | ViewModel + `collectAsStateWithLifecycle`; nothing re-fetched. |
| 4. Sensors → **Replay cold night** | 97 readings; chart shows the 68 → 64 → 68 °F step limit, shading where the room was below it, the blue Open-Meteo line; "13 h below"; Evidence quality: *0 jumps · 0 gaps · 0 weather mismatches* | Same `ingest()` path × 97, in one Room transaction each. `ViolationDetector` opens/closes violation runs; after 2 h below the limit an **issue opens automatically**. |
| 5. Home | The hero card turns red: "61.4 °F · 6.6° below the 68 °F limit"; issue "No heat · living room · opened automatically" | `HeatRules.evaluate()` on the latest reading; open issues are a Room `Flow`. |
| 6. Tap the issue | Escalation tracker (Open → Notice → Inspection → Certified), hours below / lowest temperature, timeline | `IssueDetailViewModel` combines issue + timeline + violations + apartment. |
| 7. **Send notice** | The email app opens addressed to the landlord, with a dated letter citing 105 CMR 410.180 and the hours below the limit; status becomes *Notice sent* | `NoticeTemplate.build()` (pure Kotlin, unit-tested) → `ACTION_SENDTO` intent; `IssuesRepository.advance()` logs the step. |
| 8. **Request inspection** | Tracker moves to step 3; timeline grows | Same state machine, one Room transaction per step. |
| 9. Airplane mode → Sensors | *Outdoor weather from cache* banner with Retry; chart still draws | `WeatherRepository.refresh()` fails → `WeatherStatus.Error(hasCache=true)`; chart reads outdoor rows from Room. |
| 10. More → sign in → **Sync now** | "Uploaded 97 readings · 1 issue backed up"; a second tap: "Everything is already synced" | `SyncRepository` posts readings to `POST /readings` (server dedupes by sensor + time) and upserts issues with `PUT /issues`. Laptop terminal shows the server copy (`GET /readings/count`, `GET /issues`). |

**Who presents what**

| | Priyanshu | Ankita |
| --- | --- | --- |
| Demo | Steps 1–4 and 9 (sensing, Room pipeline, rules, chart, Open-Meteo) | Steps 5–8 and 10 (issues, notice email, escalation, issue sync) |
| Slides | Architecture, persistence + API evidence, risks | Backlog, scrum-master report, repository evidence, plan to Update 2 |
| Code questions | `domain/`, `data/sensor`, `ReadingsRepository`, `WeatherRepository`, readings sync, backend | `ui/issues`, `ui/issuedetail`, `IssuesRepository`, `NoticeTemplate`, `SyncRepository.syncIssues`, `IssueSearch`, `TimelineExport`, `IssueClock` |

**The tamper question, in one line:** "We can't stop someone putting the sensor in a fridge. We make sure the record shows it, and we never let anyone hide it." Readings are insert-only; impossible jumps, gaps and indoor-tracks-outdoor periods are flagged; the inspector's thermometer stays the official proof.

**Backup plan:** a 2-minute screen recording of steps 1–10 (`docs/demo_backup.mp4`, kept locally if over GitHub's size limit), screenshots in `docs/screenshots/`, and the HTML test reports. If the emulator stalls we switch to the recording and keep talking.

## 2. Architecture as built

See [ARCHITECTURE.md](ARCHITECTURE.md). The short version:

- **UI** — Jetpack Compose + Material 3 only. 7 destinations under type-safe Navigation Compose (`@Serializable` routes): Setup, Home, Issues, Sensors, More (tabs), New issue, Issue detail. Stateless `*Content` composables take a `UiState`, so they can be UI-tested with fixed data.
- **State** — one ViewModel per screen exposing a single `StateFlow<UiState>` built with `combine` from Room flows and `stateIn(WhileSubscribed)`. Forms keep their text in the ViewModel, so rotation never loses input.
- **Data** — Room is the source of truth (7 tables). Repositories are the only classes that touch DAOs. `ReadingsRepository.ingest()` is the pipeline: quality flags → insert → violation step → auto-issue, in one transaction. DataStore holds settings and the sign-in token.
- **Domain** — pure Kotlin, no Android imports: `HeatRules`, `ViolationDetector`, `DataQualityChecker`, `WeatherCrossCheck`, `LimitSeries`, `BuildChartData`, `NoticeTemplate`. Covered by JVM unit tests at the legal boundaries (6:59 vs 7:00 AM, 11:00 vs 11:01 PM, Sept 14 vs 15, May 31 vs June 1).
- **Device** — `SensorSource` interface with `SimulatedSensorSource` and `BleSensorSource` (scan + Govee-format decoder, not yet verified on hardware). A foreground service (`connectedDevice` type) keeps sampling with the screen off.
- **APIs** — Open-Meteo via Retrofit + kotlinx.serialization, cached in `outdoor_readings` with loading / error / stale states. Our backend: Express + JWT + bcrypt, PostgreSQL or an in-memory store with the same interface.

## 3. Work status

**Completed**
- Setup with validation and persistence (US-1, local); sign-in on the More screen.
- Simulated sensor with live and replay modes through the real pipeline (US-8).
- Chart against the step limit with shading and outdoor weather, four ranges (US-3).
- Open-Meteo integration with caching and offline states (US-10).
- Issues: manual report, automatic heat issue after 2 h, timeline, escalation state machine (US-5).
- Notice letter template + email intent with share-sheet fallback (part of US-6).
- Backend API with 13 tests, readings sync and issue backup (US-9, local).
- Redesigned UI: theme, status-coloured hero card, escalation tracker, evidence-quality checks.
- Issue tools (Ankita): search the issue list, share an issue's timeline as text, and a clock showing days open and days since the first notice.

**In progress**
- BLE thermometer on real hardware (US-2): decoder done and tested; device ordered.
- Backend deployment to Render + Neon (US-9).
- Notices synced as their own records (`POST /issues/notices`) (US-6).

**Remaining for Update 2 (Nov 24, feature-complete beta)**
- CameraX photos with SHA-256 at capture + placement photo (US-4, US-2).
- On-device PDF evidence packet (US-7).
- WorkManager reminders after certification (US-6).
- Address geocoding; accessibility pass (TalkBack, 200 % font, contrast); instrumented tests in CI.

How this supports the approved scope: the slice is the core of the proposal — *record → detect → notify* — built end to end before any of the evidence extras, as Ron asked on October 6.

## 4. Decisions and scope changes since the proposal

1. **Chart drawn with Compose Canvas instead of Vico.** The limit must *step* at 7 AM / 11:01 PM with no slope, and shading must cover only indoor < limit; the same drawing code will render into the PDF's `Canvas`. Scope-neutral; we'd like Ron's sign-off.
2. **One cold night = one issue.** A violation that pauses at 11 PM (when the limit drops) and resumes later attaches to the open automatic heat issue instead of opening a second one.
3. **Notice sent by the tenant's own email app** (`ACTION_SENDTO`) rather than from our server, so the tenant reviews and sends it and we never hold their email credentials.
4. **Live simulator samples every 30 s; replay uses the real 10-minute cadence** so the live demo shows motion. The production interval is unchanged.
5. **Geocoding deferred** to sprint 2: setup stores Boston's coordinates for Open-Meteo.

No features were added beyond the approved scope; none were dropped.

## 5. Risks and blockers

| Type | Risk / blocker | Status | Mitigation |
| --- | --- | --- | --- |
| Device | Android may stop BLE sampling in the background | Open | Foreground service; gaps are flagged, not hidden; simulator is the demo path. Overnight test on two phone brands in sprint 2. |
| Device | **BLE thermometer not yet in hand** | **Blocker** for US-2 hardware check | Decoder written against the published Govee format and unit-tested on synthetic payloads; simulated mode means nothing else waits on it. |
| Permissions | Notification, Bluetooth and (later) camera permissions denied | Open | Asked only when needed (`POST_NOTIFICATIONS` when monitoring starts; `BLUETOOTH_SCAN` with `neverForLocation`, BLE mode only). The app still records if notifications are denied. |
| Privacy | Address, photos and landlord details are sensitive | Mitigated | They stay on the phone; only readings and an issue summary (category, title, status, dates — never the description) sync, and only after sign-in. Room and the evidence folder are excluded from Android backup. No real data in the repo. |
| API | Open-Meteo down or rate-limited during the demo | Mitigated | Cached rows, stale banner and Retry (demo step 9). |
| API | Our backend runs on a laptop; data is lost when the in-memory store restarts | Open | Deploy to Render + Neon PostgreSQL in sprint 2; the app is offline-first, so nothing on the phone is lost. |
| Usability | Legal wording read as legal advice | Mitigated | "Documentation only, not legal advice" on Setup, Sensors, Issue detail and in every letter; steps phrased as "may apply". |
| Usability | Charts and colours not readable for everyone | Open | Accessibility pass in sprint 3; status always shown as text as well as colour. |
| Schedule | Two-person team, PDF and camera both in sprint 2–3 | Watching | Sprint 2 = camera + hashes; sprint 3 = PDF + reminders. Stretch items untouched until the beta works. |
| Process | Early commits went straight to `main` from one machine | Fixed Nov 8 | Branches + pull requests linked to issues, `main` protected (§6). |

## 6. Repository and teamwork evidence

- **Backlog on GitHub**: every story in BACKLOG.md is a GitHub Issue with an owner, size label and milestone (Update 1 / Update 2 / Final); the **Habitable board** (GitHub Projects) has To do / In progress / Done columns.
- **Branches and pull requests**: since November 8 every change is a branch and a pull request linked to its issue. Priyanshu: `docs/update1` (#19) and `fix/ci-android-sdk` (#20, a CI failure found and fixed). Ankita: `feat/issues-sync-search-share-clock`, four commits: issue backup to the server (closes #7), issue search, sharing an issue's timeline as text, and the days-open / days-since-notice clock, with 11 unit tests; reviewed by Priyanshu. Earlier work (Oct 20 – Oct 24) was committed directly to `main` from Priyanshu's machine while we were setting up.
- **CI**: GitHub Actions runs the backend tests and the Android build + unit tests on every push (`.github/workflows/ci.yml`).
- **Scrum master**: Priyanshu (weeks of Oct 20, Nov 3), Ankita (weeks of Oct 27, Nov 10). This week Ankita runs the board.
- **Cadence**: daily WhatsApp check-in; Tuesday planning and Friday review calls.
- **Working agreement**: [WORKING_AGREEMENT.md](WORKING_AGREEMENT.md). AI use disclosed in [AI_USE.md](AI_USE.md).

## 7. Plan to Update 2 (Nov 24, feature-complete beta)

**Sprint 2 (Nov 10–17):** CameraX capture with SHA-256 at capture (US-4) · placement photo (US-2) · notices synced as records (US-6) · geocode the address · deploy backend to Render/Neon · overnight BLE test on real hardware.
**Sprint 3 (Nov 17–24):** on-device PDF packet drawn with the same chart code (US-7) · WorkManager reminders · empty/error-state audit · TalkBack and font-scale pass · instrumented tests in CI.
