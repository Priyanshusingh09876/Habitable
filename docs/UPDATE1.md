# Project Update 1 — November 10, 2026

**Working vertical slice and first end-to-end data path.**
Team: Ankita Patra, Priyanshu Singh · Scrum master this week: Ankita · Repo: github.com/Priyanshusingh09876/Habitable

## 1. What we're demonstrating (≈ 4 minutes of working software)

The flow: **simulated thermometer → Room → heat rules → chart with Open-Meteo weather → automatic heat issue → Escalate → sync to backend.** Every arrow is real code in this repo; nothing on screen is hard-coded.

| Step | What the audience sees | What is happening underneath |
| --- | --- | --- |
| 1. Fresh install | *Set up your apartment* form | DataStore `onboarded=false` picks the start destination; validation runs in `SetupViewModel`; `ApartmentRepository.save()` writes `apartments` + a `sensors` row to Room. |
| 2. Home | Empty chart, "Sensor off", no issues | `HomeViewModel` combines five Room flows into one `HomeUiState`; empty state shown. |
| 3. Sensors → **Start monitoring** | Chip turns to "Simulated", a persistent notification appears, a new point lands every 30 s | `SensorForegroundService` collects `SimulatedSensorSource(Live)`; each sample goes through `ReadingsRepository.ingest()`. |
| 4. Rotate the phone | Chart and stats survive | ViewModel + `collectAsStateWithLifecycle`; nothing re-fetched. |
| 5. Sensors → **Replay cold night** | 97 readings appear at once; chart shows the 68→64→68 step limit, orange shading where the room was below it, the blue Open-Meteo line; "13 h below the limit"; Data checks card: *0 impossible jumps · 0 gaps · 0 weather mismatches · All clear* | Same `ingest()` path × 97. `ViolationDetector` opens/closes two violation runs (the limit drops to 64 °F at 11 PM, so 64.6 °F is briefly legal). After 2 h below the limit an **issue opens automatically** and the second run links to the same issue. |
| 6. Home | "61.4 °F · 6.6° below the 68 °F limit", "No heat · living room · Opened automatically" | Rules re-evaluated on the latest reading; open issues flow from Room. |
| 7. Tap the issue | Timeline: *Below 68 °F limit (opened automatically)* → *Temperature back above…* → *Below 64 °F limit again*; button says **Send notice** | `IssueDetailViewModel` combines issue + timeline + violations; `IssueStatus.nextAction` names the button. |
| 8. Tap **Send notice**, then **Escalate → Request inspection** | Status chip changes; timeline grows; button text changes each time | `IssuesRepository.advance()` moves one state along the legal order inside a Room transaction. |
| 9. Airplane mode, back to Sensors | Weather banner: *Outdoor weather from cache* with Retry; indoor chart still draws | `WeatherRepository.refresh()` fails → `WeatherStatus.Error(hasCache=true)`; the chart reads outdoor rows from Room, never from the response. |
| 10. More → Sign in → **Sync now** | "Uploaded 97 readings"; `GET /readings/count` on the laptop shows 97 | `SyncRepository` posts a batch to `POST /readings`; server dedupes by `(sensor, recordedAt)`; rows marked `synced`. |

**Who presents what:** Priyanshu drives steps 1–5 and 9 (sensing, chart, weather, architecture). Ankita drives steps 6–8 and 10 (issues, Escalate, sync) and the backlog/risks slides. Both answer questions.

**The tamper question, in one line:** "We can't stop someone putting the sensor in a fridge. We make sure the record shows that they did, and we never let anyone hide it." Readings are insert-only; impossible jumps, gaps and indoor-tracks-outdoor periods are flagged and printed; the inspector's thermometer stays the official proof.

Backup if the emulator misbehaves: screenshots in `docs/ui_mockups_*.png` and the unit/instrumented test output from CI.

## 2. Architecture as built

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full picture. The short version:

- **UI** — 7 Compose destinations under type-safe Navigation Compose: Setup, Home, Issues, Sensors, More (tabs), New issue, Issue detail. Stateless `*Content` composables take a `UiState` so they can be tested with fixed data.
- **State** — one ViewModel per screen, a single `StateFlow<UiState>` built with `combine` from Room flows. Forms (setup, new issue) keep their text in the ViewModel, so rotation never loses input.
- **Data** — Room is the source of truth (7 tables). Repositories are the only things that touch DAOs. `ReadingsRepository.ingest()` is the pipeline: quality flags → insert → violation step → auto-issue, in one transaction.
- **Domain** — pure Kotlin, no Android imports: `HeatRules`, `ViolationDetector`, `DataQualityChecker`, `WeatherCrossCheck` (indoor within 4 °F of a ≤50 °F outdoors for ≥1 h ⇒ suspicious span), `LimitSeries`, `BuildChartData`. 36 JVM unit tests cover their edges (10:59 vs 11:01 PM, Sept 14 vs 15, May 31 vs June 1, fridge jumps, gaps).
- **Device** — `SensorSource` interface with `SimulatedSensorSource` (live drift or cold-night replay) and `BleSensorSource` (scanning + Govee-style decoder, unverified on hardware yet). A foreground service keeps sampling with the screen off.
- **API** — Open-Meteo through Retrofit + kotlinx.serialization, cached in `outdoor_readings`; loading / error / stale states in the UI.
- **Backend** — Express + JWT + bcrypt; PostgreSQL via `pg`, or an in-memory store with the same interface for tests and laptop demos. 13 API tests pass (`npm test`).

## 3. Backlog status

| ID | Story | Status | Owner |
| --- | --- | --- | --- |
| US-1 | Account + apartment setup | **Done** (local). Backend register/login done; app sign-in wired on the More screen. | Both |
| US-2 | Pair BLE thermometer + placement photo | **Partial**: scanning + decoder written; placement photo not started (needs CameraX, US-4). | Priyanshu |
| US-3 | Chart vs. legal limit (sprint 1) | **Done**: step limit, violation shading, outdoor line, four ranges. | Priyanshu |
| US-4 | Photos hashed at capture | Not started (sprint 2). | Ankita |
| US-5 | Issues + automatic heat issue | **Done**: manual report, auto-open after 2 h, timeline, Escalate state machine. | Ankita |
| US-6 | Notice + reminders | **Partial**: state transitions and timeline events done; letter template, share sheet and WorkManager deadline reminders are sprint 2. | Ankita |
| US-7 | PDF packet | Not started (sprint 2–3). | Ankita |
| US-8 | Simulated sensor for demos | **Done**: live mode + cold-night replay through the real pipeline. | Priyanshu |
| US-9 | Backend deployed | **Done locally** with tests; Render + Neon deployment this week. | Priyanshu |
| US-10 | Open-Meteo outdoor overlay | **Done**: cached, offline-safe, retry UI. | Priyanshu |

Full acceptance criteria: [BACKLOG.md](BACKLOG.md).

## 4. Decisions and scope changes since the proposal

1. **Chart drawn with Compose Canvas instead of Vico.** The two things that make the chart useful as evidence — a limit line that *steps* at 7 AM / 11:01 PM with no slope, and shading only where indoor < limit — are not something Vico's line layer does out of the box, and the same drawing code can later render into the PDF's `Canvas`. Vico remains the fallback if the custom chart becomes a maintenance burden. (Scope-neutral; asking for instructor sign-off.)
2. **One cold night = one issue.** A violation that pauses at 11 PM (when the limit drops) and resumes later attaches to the open automatic heat issue rather than opening a second one. Proposal said "a heat issue opens automatically"; this is the same promise with less noise.
3. **Live simulator samples every 30 s, replay uses the real 10-minute cadence.** Purely so a live demo shows motion; the production interval constant is unchanged.
4. **Geocoding deferred.** Setup stores Boston's coordinates for Open-Meteo; a real address → lat/long lookup is a small sprint-2 task.

## 5. Risks and blockers

| Risk / blocker | Status | Mitigation |
| --- | --- | --- |
| Android kills BLE sampling in the background | Open | Foreground service implemented; gaps are flagged in the data; simulator is the demo path. Will test overnight on two phone brands in sprint 2. |
| **BLE thermometer not yet in hand** (blocker for US-2 hardware verification) | **Blocker** | Ordered; decoder written against the published Govee format and unit-tested on synthetic payloads. Simulated mode is first-class so nothing else waits on it. |
| Open-Meteo down during a demo | Mitigated | Cached rows + stale banner + retry; demo step 9 shows it. |
| Chart work slipping | Closed | Done in sprint 1 as requested on Oct 6. |
| Legal copy read as advice | Mitigated | "Documentation only, not legal advice" on Setup, Sensors, Issue detail, More; §127L steps described as "may apply". |
| Two-person scope | Watching | Sprint 2 is CameraX + hashes + notice letter + PDF skeleton; stretch items untouched until the beta works. |
| Instrumented tests need an emulator in CI | Open | CI runs unit tests + build; instrumented tests run locally before each PR. Evaluating `reactivecircus/android-emulator-runner` for Update 2. |

## 6. Teamwork evidence

- **Repository**: one repo, protected `main`, feature branches (`feat/sensing-*`, `feat/evidence-*`), every PR reviewed by the other person. Commit history shows both of us across the slice.
- **Scrum master**: Priyanshu (weeks of Oct 20, Nov 3), Ankita (weeks of Oct 27, Nov 10).
- **Cadence**: daily WhatsApp check-in, two 30-minute calls per week (Tue/Fri), GitHub Issues + Project board as the backlog.
- **Working agreement**: [WORKING_AGREEMENT.md](WORKING_AGREEMENT.md).

## 7. Plan to Update 2 (Nov 24, feature-complete beta)

**Sprint 2 (Nov 10–17):** CameraX capture with SHA-256 at capture (US-4) · placement photo at sensor setup (US-2) · notice letter template + share sheet + `/issues/notices` sync (US-6) · geocode the address · deploy backend to Render/Neon · overnight BLE test on real hardware.
**Sprint 3 (Nov 17–24):** on-device PDF packet with the chart drawn from the same `TemperatureChart` code (US-7) · WorkManager deadline reminders · empty/error states audit · TalkBack pass · tablet/landscape layout check · instrumented tests in CI.
