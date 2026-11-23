# Project Update 2 — November 24, 2026

**Feature-complete beta: every MVP story runs end to end, with tests, failure handling, known issues and a release plan.**
Team: Ankita Patra, Priyanshu Singh · Scrum master: Priyanshu (week of Nov 17), Ankita (week of Nov 24) · Version `0.2.0-beta`
Slides: [Update2_Slides.pptx](Update2_Slides.pptx) · Presenter script and code map: [UPDATE2_SCRIPT.md](UPDATE2_SCRIPT.md) · Tests: [TESTING.md](TESTING.md) · Backlog: [BACKLOG.md](BACKLOG.md) · Real phone and thermometer: [REAL_DEVICE.md](REAL_DEVICE.md) · Accessibility: [ACCESSIBILITY.md](ACCESSIBILITY.md)

## 0. Requirement checklist

| Update 2 asks for | Where we show it |
| --- | --- |
| Feature-complete beta, core flows and integrations end to end | §1 demo; §2 story status: all MVP stories done |
| Failure and empty states | §3 |
| Early automated test results | §4: 156 tests (120 unit, 23 on-device, 13 backend) across unit, integration and end-to-end UI, CI on every push |
| Accessibility and adaptive-layout evidence | §5 and ACCESSIBILITY.md |
| Known defects | §6 |
| Release plan for the remaining two weeks | §7 |
| What changed from Update 1 | "Since Update 1" below |
| Privacy, permissions, API keys, storage, security | §9 |
| Repository and agile evidence, scrum master, scope changes | §8, §10; scope changes in §2 |
| Both students participate | §8 and the pull requests listed there |
| Generative AI and outside resources | [AI_USE.md](AI_USE.md); README "Outside resources and licenses" |

## Since Update 1 (November 10)

| Area | Update 1 | Now (Update 2) |
| --- | --- | --- |
| Device capability | Simulated sensor only | **Real Govee H5075 thermometer over Bluetooth**: picker, permissions, battery-saving batched scan, 1/5/10-minute setting |
| Evidence | Readings, issues, notice email | **Photos hashed with SHA-256 at capture**, **PDF evidence packet**, inspection and certified steps |
| Alerts | None | **"No heat detected" notification**, **daily reminder** when an issue waits on a step |
| Sync | Readings only | Readings, **issues and photo fingerprints**, in the background every 6 h |
| Failure handling | Weather offline | Plus Bluetooth off, permissions denied, sensor gaps, camera cancelled, PDF failure, save failure, **pasted text far over a field's limit** |
| UI | Working screens | Keyboard never covers a field, right keyboard per field, smoother chart, back behaviour fixed |
| Tests | 86 | **156**: unit (incl. view models with fakes, boundaries), integration with forced errors, end-to-end Compose UI, backend |

## 1. What we demonstrate (about 6 minutes)

New since Update 1 is in **bold**.

| Step | What the audience sees | Underneath |
| --- | --- | --- |
| 1. **Real thermometer** (phone) | More → Bluetooth → **Find my thermometer** lists the Govee H5075 with the temperature on its screen; tap it; Sensors → Start; Home shows the room's real temperature | `BleDeviceScanner` scans for Govee advertisements; `BleSensorSource` decodes them and stores one reading every 5 minutes (user can pick 1, 5 or 10) using a battery-saving batched scan; the foreground service ingests each one |
| 2. Cold night (emulator) | Replay cold night: chart, shading, **a "No heat detected" notification**, the automatic issue | Same `ingest()` path; `ViolationAlert` is shared by the service and Replay |
| 3. **Photo** | On the issue: Add photo → the camera → a thumbnail with the time and the start of its fingerprint; the timeline gains "Photo added" | System camera through `TakePicture` + `FileProvider` (no camera permission); `PhotosRepository.add()` hashes the file with SHA-256 and writes the row and the timeline event in one transaction |
| 4. Notice → inspection → certified | Email letter, Boston 311 choices, case number | As in Update 1, plus the inspection and certified dialogs |
| 5. **PDF evidence packet** | Export PDF → share sheet → open it: address, legal standard, summary, chart with the stepped limit and shading, timeline, photos with 64-character fingerprints, data notes | `BuildEvidenceReport` (pure Kotlin, unit-tested) decides every word and number; `PdfEvidenceWriter` lays it out with Android's `PdfDocument` |
| 6. **Sync** | "Uploaded N readings · 1 issue backed up · 1 photo fingerprint anchored" | Readings, issues and photo **hashes** go to our API; photos never leave the phone. The background worker now sends issues and hashes too |
| 7. Failure states | Airplane mode: cached weather with Retry. Bluetooth off: monitoring stops and says why | §3 |
| 8. **Reminder** | (Explained, with the unit tests) a notification when an issue has waited 7 days on the same step | `ReminderWorker` once a day; `ReminderRules` decides who is due |

Backup: the recorded demo and screenshots, as for Update 1.

## 2. Story status

| Story | Status | Owner |
| --- | --- | --- |
| US-1 Account + apartment | Done; the city is now looked up for weather | Both |
| US-2 Bluetooth thermometer | Done: picker, permissions, battery-saving batched scan, reading interval setting. Overnight run on the real device is this week's test | Priyanshu |
| US-3 Chart vs. legal limit | Done | Priyanshu |
| US-4 Photos hashed at capture | Done | Ankita |
| US-5 Issues + automatic heat issue | Done | Ankita |
| US-6 Notice + reminders | Done | Ankita |
| US-7 PDF evidence packet | Done | Ankita |
| US-8 Simulated sensor | Done | Priyanshu |
| US-9 Backend | Deploy-ready: Render blueprint and steps; deployment itself is the first task of the release plan | Priyanshu |
| US-10 Open-Meteo weather | Done | Priyanshu |

Scope decisions since Update 1:

- **Location permission in Bluetooth mode.** The thermometer also broadcasts an iBeacon-format packet, and Android hides beacon-like devices from apps that declare `neverForLocation`. We ask for Location only in Bluetooth mode and never read the phone's position. This replaces the Update 1 plan to avoid the permission.
- **System camera instead of CameraX.** The phone's camera app writes into our private folder, so the app needs no camera permission and less code. The hash is still taken the moment the file comes back.
- **Wear OS: no watch app this semester.** A tiny watch screen (room temperature against the limit, a buzz for "No heat") fits the app, but with two weeks left the phone app and real-device testing come first. Android shows phone notifications on a paired watch, so the "No heat detected" alert can reach a watch without a separate app (not tested; we have no watch).
- **A long gap ends a cold period.** If readings stop for more than 30 minutes (phone off, sensor out of range) the open period ends at the last real reading. Unmeasured time is never counted as below the limit.
- **Battery: one stored reading every 5 minutes by default** (1, 5 or 10 under More → Bluetooth). The thermometer broadcasts every two seconds; what drains a phone is waking the processor for each broadcast, not the radio. The scan is filtered to the paired thermometer in the Bluetooth chip and runs in low-power mode, and on phones whose chip supports **batched scanning** the chip collects broadcasts by itself and hands them over once per interval, so the processor sleeps in between. Phones without batching fall back to the filtered scan with one stored reading per interval. Ten minutes is still three readings inside the 30-minute gap limit.
- **Smoother UI.** The keyboard never covers a field (the content shrinks above it, also with edge-to-edge), every field has the right keyboard (email, capitals, Next/Done/Send/Search), tapping empty space closes the keyboard, and form text lives in Compose state so typing never lags. The chart caches its drawing and thins long histories to at most 800 points while keeping every dip (was K6). A release build (`gradlew assembleRelease`, signed with the debug key for demos) runs without debug checks and is what we install on the phone.

## 3. Failure and empty states

| Situation | What the user sees | How |
| --- | --- | --- |
| No internet, weather | "Showing saved outdoor weather" with Retry; the chart still draws from the cache. With no cache: "Outdoor weather unavailable" and the indoor line only | `WeatherStatus.Error(hasCache)` |
| No internet, sync | "Backend unreachable"; nothing is lost and it is retried by the worker | readings stay `synced = 0` |
| Sign-in expired | Signed out with "Sign in to sync" | HTTP 401 handled in `SyncRepository` |
| Bluetooth or Location off, permission denied | Start is refused with a sentence saying what to turn on or allow | `BleSupport.problem()` |
| Bluetooth switched off while recording | Monitoring stops; Sensors shows the reason | the service saves the reason for the screen |
| Thermometer out of range / phone off | Gap flagged on the next reading; the open cold period ends at the last real reading | `DataQualityChecker`, `ViolationDetector.closeAtGap` |
| City not found or offline during setup | Setup still saves; weather uses the previous coordinates, or Boston | `WeatherRepository.findCity()` returns null |
| Camera cancelled, or no camera app | Nothing is added; "No camera app was found" if there is none | `PhotosRepository.add()` returns null for an empty file |
| PDF cannot be written | "Could not create the PDF" with the reason; the issue is untouched | `IssueDetailEvent.Message` |
| Invalid setup input | Inline errors on address and email | `SetupViewModel` |
| Very long or pasted text (e.g. the Gettysburg Address in a title) | Each field keeps up to its limit; one-line fields stay on one line; a counter appears near the limit and says "Limit reached"; lists cut long titles with "…" | `TextInput`, `fieldSupport` |
| Saving an issue fails | "Could not save the issue"; the typed text stays for a retry | `NewIssueViewModel.saveError` |
| Empty states | "No issues yet", "Nothing recorded yet", "No photos yet", "No events yet", "No thermometer found… scan again" | each screen |
| Notifications denied | Recording, alerts on screen and reminders in the app still work; only the notifications are missing | `SecurityException` caught |

## 4. Tests

156 automated tests, covering every level of the test pyramid:

- **120 unit tests** (no Android): heat rules, violation detector with gaps, quality flags, weather cross-check, chart data and downsampling, letters, Bluetooth decoding with a real H5075 packet, city lookup, SHA-256, reminder rules, PDF content, issue tools, **boundary tests for every text limit** (one below, at, one above), and **view-model tests with a fake repository** injected through the `IssueWriter` interface, including a forced storage failure.
- **15 integration tests** on a real Room database, including **forced errors** from a fake weather service (offline, server error, empty answer).
- **8 end-to-end UI tests** that drive real screens: report an issue and find it in the database, search and open it, and paste the **Gettysburg Address** into the title, description and search.
- **13 backend tests.**

`run-all-tests.ps1` runs them all; GitHub Actions runs the backend tests, the Android build and the unit tests on every push. Devices used, the bug report template and the Logcat privacy check are in TESTING.md §5–6.

Manual cases M1–M21 are in TESTING.md. M13–M21 cover what is new (real sensor, sensor failure, photos, PDF, empty states, large text, TalkBack, tablet); their results are recorded there as we run them.

## 5. Accessibility and adaptive layout

Built in: descriptions on icon buttons, section titles announced as headings, a spoken description for the chart, text that scales with the system font size on scrolling screens, 48 dp touch targets, status never shown by colour alone, and a centred column of at most 720 dp on tablets. Details, and the results of the large-text, TalkBack and tablet checks, are in ACCESSIBILITY.md.

## 6. Known issues

| # | Issue | Effect | Plan |
| --- | --- | --- | --- |
| K1 | The real thermometer has not yet been left running overnight | Unknown how different phones behave with the screen off | Overnight test this week (REAL_DEVICE.md §4) |
| K2 | No alert when the thermometer goes out of range | The gap is only flagged when readings resume | "Sensor not heard for 15 minutes" notification |
| K3 | Sync on a real phone needs a reachable backend | `10.0.2.2` only works in the emulator | Deploy to Render; until then the laptop's address in `local.properties` |
| K4 | No restore from the server | A new phone starts empty even though the data is backed up | Download readings and issues after sign-in |
| K5 | "Notice sent" is logged when the email app opens | The app cannot know whether the tenant pressed Send | Ask "Did you send it?" on return |
| K6 | Chart has no zoom or tap-for-value | Exact values are read from Sensors or the PDF | Tap-for-value if time allows (long histories are already thinned to 800 points) |
| K7 | Photos show as thumbnails only; no full-screen viewer | Details are hard to see in the app (the file and the PDF have them) | Full-screen viewer |
| K8 | The 1 → 2 database migration has no automated test | Checked by Room when the database opens, and by hand | Room migration test |
| K9 | On-device tests are not run in CI | A broken on-device test is only caught locally | Emulator job in GitHub Actions |
| K10 | Tablets get a centred column, not a two-pane layout | Space is unused on large screens | Two-pane issues screen if time allows |
| K11 | Weather is for the city, not the street address | Fine for hourly temperature | No change planned |
| K12 | Cleartext HTTP is allowed app-wide for the laptop backend (`10.0.2.2` / LAN address) | Technical debt: a deployed build should only use HTTPS | Once the backend is on Render (HTTPS), allow cleartext only in debug builds |
| K13 | Two deprecated icon names (`Icons.Filled.ListAlt`) give build warnings | None today | Switch to `Icons.AutoMirrored` |

## 7. Release plan: November 24 to December 8

| Dates | Goal | Work | Owner |
| --- | --- | --- | --- |
| Nov 24–27 | Real-world proof | Overnight run with the thermometer on a phone (K1); deploy the backend and point the app at it (K3); run M13–M21 and record results | Priyanshu; both for the manual run |
| Nov 28–Dec 1 | Close the gaps that matter | Restore from the server (K4); sensor-lost alert (K2); "Did you send it?" (K5); migration test (K8) | Priyanshu K4, K2 · Ankita K5, K8 |
| Dec 2–4 | Polish | Photo viewer (K7); TalkBack and large-text fixes found in M19–M20; emulator job in CI (K9) | Ankita K7 + accessibility · Priyanshu K9 |
| Dec 5 | **Feature freeze** | Only bug fixes after this; tag `v1.0-rc` | Both |
| Dec 5–7 | Paper and presentation | 1,000–1,500-word paper, final README, final slides, 8–10 minute demo rehearsed twice, backup recording | Both |
| Dec 8 | Submit | Tag `v1.0`, final commit identified, individual reflections | Both |

Stretch items (two-pane tablet layout, humidity mold alert) stay frozen unless everything above is done.

| Risk | Likelihood | Effect | What we do |
| --- | --- | --- | --- |
| A phone maker's battery saver kills the service overnight | Medium | Gaps in the real-device record | Gaps are flagged and never counted as cold; REAL_DEVICE.md tells the user to set the app to Unrestricted; overnight test on Nov 24–27 shows how often |
| Render free tier sleeps or the database expires | Medium | First sync is slow or fails | Sync is retried by WorkManager and nothing is lost on the phone; the demo keeps the laptop backend as backup |
| Emulator, Bluetooth or Wi-Fi fails during the final demo | Medium | Live demo stalls | Recorded demo and screenshots (`docs/capture-backup.ps1`); Replay cold night works fully offline |
| Accessibility checks (M19–M20) find many problems | Low | Polish time runs out | Checks run this week, fixes scheduled Dec 2–4, before the freeze |
| Late scope creep | Low | Missed freeze | Second-priority and stretch stories stay frozen until everything above is done |

## 8. Who did what for this update

| Area | Owner | Pull request |
| --- | --- | --- |
| Bluetooth on real hardware (picker, permissions, battery-saving batched scan and interval setting, service types), keyboard and chart smoothness, gaps ending a cold period, city lookup, deploy blueprint, issue and hash backup in the worker, shared cold-night alert | Priyanshu | `feature/update2-sensor-and-backend` |
| Photos with SHA-256, photo fingerprint sync, PDF evidence packet, reminders, accessibility pass and docs | Ankita | `feature/update2-evidence` |

Each of us reviewed the other's pull request. Later pull requests for this update: #29 smooth UI and Bluetooth battery saving, #30 input limits and the full test pyramid, #31 a test import fix (Priyanshu, reviewed and merged by Ankita for #29 and #31).

## 9. Privacy, permissions and security

| Topic | Decision |
| --- | --- |
| What leaves the phone | Only after sign-in: temperature readings, an issue summary (category, title, status, dates; never the description) and photo **fingerprints**. Photos and the apartment address never leave the phone |
| Backups | The database and evidence folder are excluded from Android cloud backup (`backup_rules.xml`, `data_extraction_rules.xml`) |
| Secrets and API keys | Open-Meteo needs no key. The backend URL lives in `local.properties`, the server secret in `backend/.env` (both git-ignored); on Render the JWT secret is generated by Render (`generateValue`). The phone stores only its own sign-in token, in DataStore |
| Passwords | Hashed with bcrypt on the server; wrong password and unknown email give the same answer, so accounts cannot be discovered |
| Permissions | Asked only when needed: notifications when monitoring starts; Nearby devices + precise Location only in Bluetooth mode (Android requires it to see the thermometer; we never read the position); no camera permission (the system camera app is used). Every denial is handled with a sentence saying what to allow |
| Logging | No password, token, email or reading content in Logcat; network logging is debug-only at `BASIC` level |
| Input | Validated on the phone (lengths, email, address) and again on the server (ranges, formats) |
| Demo data | Fictional only: 12 Pratt St, Apt 3; Mark Rivera; landlord@example.com; test accounts. No real addresses or passwords on screen |
| Known gap | Cleartext HTTP for the laptop backend (K12) |

## 10. Workflow evidence

- **Board:** [GitHub Project](https://github.com/users/Priyanshusingh09876/projects/3), mirrored by BACKLOG.md.
- **Pull requests:** every change goes through a branch and a pull request reviewed by the other student (#21–#31 this month).
- **Scrum master:** Priyanshu for the week of November 17, Ankita for the week of November 24; we swap weekly.
- **Bugs:** filed with the Bug report template (`.github/ISSUE_TEMPLATE/bug_report.md`); a fixed bug gets a test (I3b, I3c).
- **CI:** GitHub Actions on every push: backend tests, Android build and unit tests.
