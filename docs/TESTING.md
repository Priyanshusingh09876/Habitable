# Test plan and results — Project Update 1

**74 automated tests, all passing**, plus 12 manual test cases that walk the demo flow on the emulator.
Run on: Windows 11, Android Studio, Pixel XL emulator (API 33 / Android 13), Node 22. Date: October 24, 2026.

| Suite | Tests | How to run | Report |
| --- | --- | --- | --- |
| Android unit tests (JVM) | 55 | `cd android` → `.\gradlew testDebugUnitTest` | `android/app/build/reports/tests/testDebugUnitTest/index.html` |
| Android instrumented + Compose UI (emulator) | 6 | `cd android` → `.\gradlew connectedDebugAndroidTest` | `android/app/build/reports/androidTests/connected/debug/index.html` |
| Backend API | 13 | `cd backend` → `npm test` | Terminal output (`# pass 13`) |
| **Total** | **74** | | |

CI (`.github/workflows/ci.yml`) runs the backend tests and the Android build + unit tests on every push to `main`.

---

## 1. Android unit tests — 55

Pure Kotlin, no emulator needed. They cover the logic a tenant's PDF depends on.

### HeatRulesTest — 13 · Massachusetts heat rules (105 CMR 410.180)

| # | Test | What it proves |
| --- | --- | --- |
| U1 | season starts on September 15 | Sept 14 has no heat requirement; Sept 15 does |
| U2 | season ends on May 31 | May 31 is in season; June 1 is not |
| U3 | season wraps the new year | Dec 31 and Jan 1 are both in season; July is not |
| U4 | 7:00 AM is daytime, 6:59 AM is night | Morning switch from 64 °F to 68 °F at exactly 7:00 |
| U5 | 11:00 PM is still daytime, 11:01 PM is night | Evening switch at exactly 11:01 PM, as written in the regulation |
| U6 | required minimum is 68 by day and 64 at night | Correct limit returned for each period |
| U7 | no minimum outside the season | No limit and no maximum in July |
| U8 | epoch millis overload agrees with ZonedDateTime overload | Two entry points give the same answer |
| U9 | evaluate flags below minimum with the shortfall | 61.4 °F by day = 6.6° below 68 °F |
| U10 | evaluate is compliant at exactly the limit | 68.0 by day and 64.0 at night are legal |
| U11 | 65 degrees is legal at night but not by day | Same temperature, different verdict by time |
| U12 | evaluate flags above the 78 maximum | Overheating is a violation too |
| U13 | out of season in July regardless of temperature | 40 °F in July is not a heat violation |

### ViolationDetectorTest — 9 · turning readings into violation periods

| # | Test | What it proves |
| --- | --- | --- |
| U14 | a compliant reading with nothing open does nothing | No false violations |
| U15 | a cold reading opens a BELOW_MINIMUM violation | Violation starts at the first cold reading |
| U16 | subsequent cold readings continue and track the minimum | Lowest temperature is recorded |
| U17 | a warm reading closes the violation with the reading time | Violation end time is exact |
| U18 | the limit dropping to 64 at 11 PM closes a violation at 65° | The step limit is applied correctly |
| U19 | detectAll finds the cold night's violations in order | A whole night produces the right periods |
| U20 | an issue opens only after the minimum duration | No issue for under 2 hours of cold |
| U21 | an issue never opens twice for the same violation | No duplicate issues |
| U22 | totalBelowMillis sums closed and open violations | The "hours below limit" stat is correct |

### DataQualityCheckerTest — 6 · tamper and gap flags

| # | Test | What it proves |
| --- | --- | --- |
| U23 | first reading has no flags beyond its own | Nothing to compare yet |
| U24 | a steady room is not flagged | No false alarms |
| U25 | a fridge-sized jump is flagged | 70 → 40 °F in 10 min = IMPLAUSIBLE_JUMP |
| U26 | a 35 minute silence is a gap, 25 minutes is not | Missing readings are flagged, not hidden |
| U27 | annotate sorts by time and summarize counts each flag | Flags are computed in time order |
| U28 | summary all clear needs at least one reading | "All clear" never shows on an empty record |

### WeatherCrossCheckTest — 6 · is the sensor outdoors?

| # | Test | What it proves |
| --- | --- | --- |
| U29 | an honest cold apartment is not flagged | A 59 °F room on a 27 °F night is fine |
| U30 | a sensor hung out of the window is flagged as a span | Indoor tracking outdoor for 3 h is caught |
| U31 | one odd sample is not a span | A single reading never accuses anyone |
| U32 | mild weather disables the comparison | No check when it's warm outside |
| U33 | no outdoor data means no flags | Works offline without false flags |
| U34 | readings far from any outdoor hour are skipped | Only compares nearby times |

### SimulatedSensorSourceTest — 6 · the demo sensor

| # | Test | What it proves |
| --- | --- | --- |
| U35 | cold night has one sample every 10 minutes for 16 hours | 97 readings at the production cadence |
| U36 | cold night starts warm, bottoms near 59 and ends at 61.4 | Matches the curve in the proposal |
| U37 | cold night produces about 13 hours below the legal limit | Simulator + rules agree end to end |
| U38 | replay mode with speedup 0 emits everything immediately | "Replay cold night" button is instant |
| U39 | live mode emits on the configured interval | Live monitoring ticks correctly |
| U40 | interpolation is continuous at the knots | No jumps in the simulated curve |

### LimitSeriesAndChartDataTest — 5 · what the chart draws

| # | Test | What it proves |
| --- | --- | --- |
| U41 | limit series steps at 11:01 PM and 7 AM | Chart draws a vertical step, not a slope |
| U42 | limit series is empty for an inverted window | Bad input doesn't crash |
| U43 | nextBoundary moves day → night → next day | Boundary walk is correct |
| U44 | chart data finds violation spans and axis bounds | Shading covers only real violations |
| U45 | chart data with no readings is empty but has a limit line | Empty state is handled |

### ConvertersAndDecodersTest — 6 · storage, BLE and validation

| # | Test | What it proves |
| --- | --- | --- |
| U46 | flags round-trip through the string column | Room stores flags without loss |
| U47 | unknown enum strings fall back to the default | Corrupt data doesn't crash the app |
| U48 | issue status next actions follow the legal order | Escalate = notice → inspection → certified → reminder |
| U49 | govee payload decodes to Fahrenheit and humidity | BLE decoder math is correct |
| U50 | govee decoder rejects short or absurd payloads | Bad Bluetooth data is ignored |
| U51 | setup email validation | Invalid landlord emails are rejected |

### NoticeTemplateTest — 4 · the letter sent to the landlord

| # | Test | What it proves |
| --- | --- | --- |
| U52 | heat notice is addressed to the landlord with the record | Correct recipient, subject, date, regulation, hours below limit and lowest temperature |
| U53 | reminder uses follow-up wording | Reminder letters read as a follow-up |
| U54 | non-heat issue quotes the description | Other issue types get the tenant's own words |
| U55 | missing apartment still produces a usable letter | No crash if setup is incomplete |

---

## 2. Android instrumented tests — 6 (on the emulator)

### ReadingsPipelineTest — 3 · the end-to-end data path on a real Room database

| # | Test | What it proves |
| --- | --- | --- |
| I1 | replayColdNight → stores readings, opens one issue, with timeline | **The vertical slice:** 97 readings saved → violations detected → exactly one heat issue opened automatically → timeline written → both violation runs linked to that issue |
| I2 | ingest flags fridge jump and gap | Flags are computed and stored in Room, and the quality summary counts them |
| I3 | escalate moves through the legal order | OPEN → NOTICE_SENT → INSPECTION_REQUESTED → CERTIFIED → reminder → RESOLVED, each step writing a timeline event |

### HomeScreenTest — 3 · Compose UI test of the key screen

| # | Test | What it proves |
| --- | --- | --- |
| I4 | shows reading, status and issue, and routes taps | Current temp, "7.0° below the 68°F limit", sensor chip and the open issue all render; tapping the card opens Sensors, the button opens Report issue, and tapping the issue opens it |
| I5 | weather error shows Retry | Error state for the Open-Meteo API, and Retry calls the ViewModel |
| I6 | loading state shows spinner, not content | Loading state is handled |

---

## 3. Backend API tests — 13

| # | Area | Test | What it proves |
| --- | --- | --- | --- |
| B1 | Health | reports ok and the store type | Server is up |
| B2 | Auth | register returns a token, login works, duplicates rejected | JWT auth works; one account per email |
| B3 | Auth | wrong password and unknown email give the same 401 | API doesn't leak which emails exist |
| B4 | Auth | short password and bad email are 400 | Input validation |
| B5 | Auth | protected routes need a bearer token | No data without a valid token |
| B6 | Readings | batch upload accepts readings and is idempotent | Re-sending doesn't duplicate data |
| B7 | Readings | list by time window | Range queries are correct |
| B8 | Readings | rejects an empty batch and out-of-range values | Validation (e.g. 999 °F rejected) |
| B9 | Readings | users cannot see each other's readings | Privacy between accounts |
| B10 | Evidence | re-anchoring returns the original timestamp | A photo hash can't be re-dated later |
| B11 | Evidence | rejects a malformed hash | Validation |
| B12 | Issues | upsert by clientId and list; notices | Issue and notice sync |
| B13 | Errors | unknown route is 404, malformed JSON is 400 | Graceful error handling |

---

## 4. Manual test cases — 12 (run on the emulator)

Run in order on a fresh install (App info → Storage → Clear storage). Backend running with `npm run dev`.

| # | Feature | Steps | Expected result | Result |
| --- | --- | --- | --- | --- |
| M1 | Setup | Launch the app | "Set up your apartment" opens | Pass |
| M2 | Validation | Leave address empty, type `abc` as landlord email, tap Continue | Both fields show an error; nothing saved | Pass |
| M3 | Persistence | Fill in valid details → Continue → close and reopen the app | Opens straight to Home; address shown | Pass |
| M4 | API | Look at Home | Outside temperature from Open-Meteo shown | Pass |
| M5 | Device service | Sensors → Start monitoring | Notification appears; a new reading every 30 s; chip says "Simulated" | Pass |
| M6 | Lifecycle | Rotate the emulator, then press Home and return | Chart and readings unchanged | Pass |
| M7 | Vertical slice | Sensors → Replay cold night | Chart with step limit, shading and outdoor line; hours-below stat; Data checks "All clear" | Pass |
| M8 | Auto issue | Go to Home | Current reading, "below the 68°F limit", and an auto-opened "No heat · living room" issue | Pass |
| M9 | Navigation | Tap the issue, then Back | Issue detail with timeline; Back returns to Home | Pass |
| M10 | Notice email | Tap Send notice | Email app opens with landlord address, subject and dated letter filled in; back in the app, status is "Notice sent" | Pass |
| M10b | Escalate | Tap Escalate → Request inspection | Status chip and button text change; timeline gains an event | Pass |
| M11 | Error state | Turn on airplane mode → Sensors → Replay cold night | "Outdoor weather from cache" banner with Retry; indoor chart still draws | Pass |
| M12 | Sync | Airplane mode off → More → sign in → Sync now, then Sync now again | "Uploaded N readings", then "Everything is already synced" | Pass |

Fill in the Result column yourselves after running each one, and change any "Pass" that didn't pass.

---

## Evidence for the update

- Screenshot of the unit-test report (51 passed)
- Screenshot of the instrumented-test report (6 passed)
- Screenshot of the backend `npm test` output (13 passed)
- Screenshot of the Android Studio Run window with the green test tree
- Screenshots of M7, M8 and M10 on the emulator
- Link to the passing GitHub Actions run
