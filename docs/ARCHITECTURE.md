# Architecture

Habitable is an offline-first Android app with a thin backend. Room on the phone is the source of truth; the network only *adds* rows (weather cache) or marks rows as synced.

## Layers

```
┌───────────────────────────────── UI (Jetpack Compose) ──────────────────────────────────┐
│ SetupScreen  HomeScreen  SensorsScreen  IssuesScreen  NewIssueScreen  IssueDetailScreen │
│ MoreScreen   TemperatureChart (Canvas)  Common components  HabitableNavHost (type-safe) │
└──────────────────────────────────────────┬──────────────────────────────────────────────┘
                                           │ collectAsStateWithLifecycle
┌──────────────────────────────────────────▼──────────────────────────────────────────────┐
│ ViewModels — one StateFlow<UiState> each, built with combine() from repository Flows    │
│ Home · Sensors · Issues · NewIssue · IssueDetail · Setup · More                          │
└──────────────────────────────────────────┬──────────────────────────────────────────────┘
                                           │ suspend / Flow
┌──────────────────────────────────────────▼──────────────────────────────────────────────┐
│ Repositories (data/repository)                                                          │
│ ApartmentRepository · ReadingsRepository · WeatherRepository · IssuesRepository · Sync   │
└───────┬──────────────────────┬────────────────────────┬─────────────────────────────────┘
        │                      │                        │
┌───────▼────────┐   ┌─────────▼──────────┐   ┌─────────▼───────────────┐   ┌─────────────────┐
│ Room           │   │ Retrofit           │   │ Retrofit                │   │ DataStore       │
│ HabitableDb    │   │ OpenMeteoApi       │   │ HabitableApi (backend)  │   │ Settings        │
│ 7 tables       │   │ hourly temperature │   │ auth · readings · anchor│   │ mode, token…    │
└───────▲────────┘   └────────────────────┘   └─────────────────────────┘   └─────────────────┘
        │
┌───────┴────────────────────────────────────────────────────────────────────────────────┐
│ Domain (pure Kotlin, no Android imports)                                               │
│ HeatRules · ViolationDetector · DataQualityChecker · WeatherCrossCheck · LimitSeries   │
└────────────────────────────────────────────────────────────────────────────────────────┘
        ▲
┌───────┴────────────────────────────────┐   ┌────────────────────────────────────────────┐
│ SensorSource                           │   │ Background                                 │
│ SimulatedSensorSource (live / replay)  │   │ SensorForegroundService (collects source)  │
│ BleSensorSource (scan + Govee decoder) │   │ SyncWorker via WorkManager (6 h + manual)  │
└────────────────────────────────────────┘   └────────────────────────────────────────────┘
```

Dependency wiring is a hand-written container (`di/AppContainer.kt`) exposed by `HabitableApp`; ViewModels receive repositories from `HabitableViewModelFactory`. No DI framework: for two people it is clearer, builds faster, and every boundary is still explicit.

## The end-to-end data path (Update 1)

```
SensorSample ──▶ ReadingsRepository.ingest(sensorId, sample)      [one Room transaction]
                   1. DataQualityChecker.flagsFor(previous, candidate)   → IMPLAUSIBLE_JUMP / GAP_BEFORE / SIMULATED
                   2. readingDao.insert(…)                               → temperature_readings
                   3. ViolationDetector.step(openViolation, reading)     → open / continue / close in heat_violations
                   4. if violation ≥ 2 h and no issue yet:
                        link to the open automatic HEAT issue, or
                        create issues row + SENSOR_VIOLATION_STARTED timeline event
                 ◀── IngestResult(reading, openViolation, issueOpened)

Room Flows ──▶ HomeViewModel.combine(apartment, latest, readings, outdoor, violations)
                   HeatRules.evaluate(latest)        → status chip
                   BuildChartData(readings, outdoor) → ChartData (indoor, outdoor, step limit, violation spans)
                   totalBelowMillis(violations)      → "13 h below limit"
             ──▶ HomeUiState.Loaded ──▶ HomeContent (Compose) ──▶ TemperatureChart (Canvas)
```

The same `ingest()` is used by the foreground service (live), by **Replay cold night** (97 back-dated samples) and by the instrumented test `ReadingsPipelineTest`, so the demo path *is* the production path.

## Data model (Room, schema v1)

| Table | Key columns | Notes |
| --- | --- | --- |
| `apartments` | id, address, unit, city, latitude, longitude, landlord_name, landlord_email | One per install for now |
| `sensors` | id, apartment_id, ble_address ("SIM" for simulator), model, room, placement_photo_path | |
| `temperature_readings` | id, sensor_id, temp_f, humidity_pct, recorded_at, flags, synced | Insert-only; `flags` = comma-joined `ReadingFlag` names |
| `heat_violations` | id, sensor_id, started_at, ended_at, min_temp_f, required_temp_f, kind, issue_id | One row per contiguous run below/above the limit |
| `issues` | id, apartment_id, category, title, description, status, opened_at, closed_at, opened_automatically | Status drives the Escalate button |
| `timeline_events` | id, issue_id, type, occurred_at, note | Everything shown on the issue timeline |
| `outdoor_readings` | apartment_id + time_millis, temp_f, fetched_at | Open-Meteo cache, pruned after 3 days |

The server keeps only what sync needs: `users`, `sensors`, `temperature_readings`, `evidence_anchors`, `issues`, `notices` (see `backend/src/db/schema.sql`). The two schemas are intentionally not identical.

## Navigation

Type-safe routes (`@Serializable` objects/classes) in `ui/navigation/HabitableNavHost.kt`:

```
Setup ──(first run only)──▶ Home ◀──────────────┐
                            │  ▲                │
 bottom bar:  Home · Issues · Sensors · More    │  back pops to the tab it came from
                            │                   │
          Home / Issues ──▶ NewIssue ──(saved)──▶ IssueDetail
          Home / Issues ─────────────────────────▶ IssueDetail
          More ──▶ Setup (edit apartment) ──▶ Home
```

Tab switches save and restore each tab's own back stack (`popUpTo(start) { saveState }` + `restoreState`), so leaving Issue detail, switching to Sensors and coming back returns to the same issue.

## State and lifecycle

- `MainActivity` declares `configChanges` for rotation and uses a single `setContent`; all screen state lives in ViewModels, so rotation, backgrounding and returning never refetch.
- Lists and charts are derived with `combine` from Room `Flow`s and exposed through `stateIn(WhileSubscribed(5 s))`, which stops collection when the screen is not visible.
- Form text (setup, new issue, notes) lives in the ViewModel or `rememberSaveable`.
- `LifecycleResumeEffect` nudges the "now" clock so the 16-hour window slides forward when the user comes back.

## External API: Open-Meteo

`GET https://api.open-meteo.com/v1/forecast?latitude&longitude&hourly=temperature_2m&temperature_unit=fahrenheit&timezone=America/New_York&past_days=2&forecast_days=1&timeformat=unixtime`

`WeatherRepository.refresh()` runs off the main thread (Retrofit suspend function), writes rows into `outdoor_readings`, and publishes `WeatherStatus` (Idle / Loading / Fresh / Error(hasCache)). The chart always reads outdoor rows from Room, so an outage degrades to a "from cache" banner with a Retry button instead of an empty chart.

## Device capability: Bluetooth LE thermometer

`BleSensorSource` scans for one MAC address with `SCAN_MODE_LOW_POWER`, decodes the manufacturer payload (Govee H5075 layout: `raw/1000/10` °C, `raw%1000/10` %RH), and emits `SensorSample`s through a `callbackFlow`. Permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) are requested only when the user picks BLE mode. `SensorForegroundService` (`foregroundServiceType="connectedDevice"`) keeps collecting while the screen is off; Android's background-scan limits are the project's biggest technical risk, so gaps are flagged rather than hidden.

## Accessibility and adaptive behaviour

- Material 3 components with default 48 dp touch targets; custom rows (sensor mode) set `height(48.dp)` and `Role.RadioButton`.
- All colours come from the theme; text/background pairs meet WCAG AA (navy #0F1B2D on white 14.6:1; muted #5B6676 on white 5.4:1; heat ink #A33A1B on heat-soft #FCE5DC 5.1:1). Dark theme provided.
- Chart has a `contentDescription`; status is never colour-only (chips carry words; legend has labels).
- `sp` units throughout; scrolling columns everywhere so 200 % font scale still works.
- Rotation tested on the Pixel 8 emulator; landscape uses the same scrolling layouts.

## Privacy and security

- Photos (sprint 2) and addresses stay on the device; only readings and hashes sync, after explicit sign-in.
- `backup_rules.xml` / `data_extraction_rules.xml` exclude the database and evidence folder from cloud backup.
- Secrets: `local.properties` and `.env` are git-ignored; examples are committed. The device stores only the backend's own JWT in DataStore.
- Input validation on both ends; server responses never reveal whether an email exists.
- `usesCleartextTraffic=true` is only for the emulator → `10.0.2.2` dev loop; the deployed backend is HTTPS.
