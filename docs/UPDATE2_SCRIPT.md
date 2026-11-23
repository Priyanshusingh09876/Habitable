# Update 2: presenter script, demo plan and code map

15 minutes in total: **slides about 5 min, live demo about 6 min, questions about 4 min.**
Both of us speak and both of us drive part of the demo. Slides: [Update2_Slides.pptx](Update2_Slides.pptx).

## 1. Who says what

| Slide | Speaker | Time | Key sentence |
| --- | --- | --- | --- |
| 1 Title | Priyanshu | 0:20 | "Habitable turns a cold apartment into evidence a Boston inspector accepts. This is our feature-complete beta, 0.2.0." |
| 2 Since Update 1 | Ankita | 0:40 | "Since Update 1 we added the real thermometer, photo fingerprints, the PDF packet, alerts and reminders, and tests went from 86 to 156." |
| 3 Stories | Ankita | 0:30 | "All ten MVP stories run end to end; the backend is deploy-ready and deploying it is the first task of our release plan." |
| 4 Architecture | Priyanshu | 0:40 | "Compose screens observe a ViewModel's StateFlow; ViewModels call repositories; repositories own Room, DataStore, Retrofit and Bluetooth. One ingest path serves the real and simulated sensor." |
| 5 Integrations | Priyanshu | 0:40 | "Open-Meteo with loading, fresh, cached and failed states; Room version 2 plus DataStore; and the Bluetooth thermometer, with a batched scan so the phone sleeps between readings." |
| — Demo | Both | 6:00 | See §2 |
| 6 Failure states | Ankita | 0:30 | "Every failure ends in a sentence that says what to do, and nothing is lost." |
| 7 Tests | Priyanshu | 0:40 | "Every level of the pyramid: view-model tests with a fake repository, boundary tests, forced-error integration tests and end-to-end Compose tests, including the Gettysburg Address." |
| 8 Accessibility | Ankita | 0:30 | "Semantics, headings, 48 dp targets, font scale, rotation and a 720 dp column on tablets; results are in ACCESSIBILITY.md." |
| 9 Privacy and security | Ankita | 0:30 | "Photos and the address never leave the phone; only fingerprints and readings sync, after sign-in. No keys in the repository." |
| 10 Known issues | Priyanshu | 0:20 | "Honest list: overnight test, sensor-lost alert, restore from server, cleartext for the laptop backend." |
| 11 Release plan | Ankita | 0:30 | "Real-world proof, close the gaps, polish, freeze on December 5, submit December 8." |
| 12 Workflow | Ankita | 0:20 | "Every change is a pull request reviewed by the other; Ankita is scrum master this week." |

## 2. Live demo (6 minutes)

**Before class:** emulator open with the app set up (12 Pratt St, Apt 3; Mark Rivera; landlord@example.com), Wi-Fi on, backend running on the laptop (`cd backend` → `npm run dev`) and signed in with the test account. Phone charged, thermometer next to it, app monitoring on "1 min". Close every other app and notification. Font size normal.

| # | Who drives | What to do | What to say |
| --- | --- | --- | --- |
| 1 | Priyanshu | **Phone:** show Home with the real room temperature and the thermometer's own screen beside it | "This is the real Govee thermometer read over Bluetooth; we never connect to it, we decode its broadcast. The phone stores one reading per interval and sleeps in between." |
| 2 | Priyanshu | **Emulator:** Sensors → Replay cold night → pull down the notification shade | "A recorded cold night through the same pipeline. The chart shades every hour below the stepped legal limit, 68 by day and 64 at night, and the app opened a heat issue and notified us." |
| 3 | Ankita | Home → tap the "No heat" issue → Add photo → take a picture → OK | "The photo is hashed with SHA-256 the moment it comes back from the camera, so we can show later it was not edited. Here is the start of its fingerprint, and the timeline gained 'Photo added'." |
| 4 | Ankita | Send notice → show the email → back → Escalate → Request inspection | "The letter cites 105 CMR 410.180 and is dated; the status follows the legal order: notice, inspection, certified." |
| 5 | Ankita | Export PDF evidence packet → open it | "One PDF for the landlord or inspector: the rule, the summary, the chart, the timeline, and every photo with its full 64-character fingerprint." |
| 6 | Priyanshu | More → Sync now | "Readings, the issue summary and photo fingerprints go to our API; the photos themselves never leave the phone." |
| 7 | Priyanshu | Airplane mode on → Home | "Offline: the outdoor weather comes from the cache with a Retry button, and the chart still draws." Airplane mode off. |
| 8 | Ankita | Issues → + → paste the Gettysburg Address into the title | "Pasted text far over the limit: the field keeps 80 characters on one line and says 'Limit reached'. That is also an automated test." |

**If something fails:** say so in one sentence and switch to the backup (recording and screenshots in the slides' backup section); never debug live.
**Backup recording:** run the demo once on Monday and record it with the emulator's screen recorder; keep it open in a second window. `docs/capture-backup.ps1` saves screenshots of each step.

## 3. Questions Ron may ask, and where the code is

Paths start at `android/app/src/main/java/edu/bu/cs501/habitable/`. Open the file and point at the line.

### Screens, state and navigation

| Question | Short answer | Where |
| --- | --- | --- |
| How does a screen get its data? | The ViewModel exposes a `StateFlow<UiState>`; the screen collects it with `collectAsStateWithLifecycle()`, so collection stops when the app is in the background | `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt` |
| Why is form text not in a StateFlow? | Text-field state must update in the same frame as typing; Compose snapshot state (`mutableStateOf`) avoids lost characters and cursor jumps | `ui/issues/IssuesViewModel.kt` (`NewIssueViewModel.form`) |
| How does navigation work? Back? | Type-safe routes (`@Serializable` objects) in one NavHost; tabs keep their own stack; pushed screens pop to their tab; Home always pops straight back | `ui/navigation/HabitableNavHost.kt` (`openTab`) |
| What happens on rotation? | ViewModels survive configuration changes; Room and DataStore are the source of truth, so state is rebuilt from them | any `*ViewModel.kt` |
| Why doesn't the keyboard cover fields? | Edge-to-edge needs `imePadding()`; we apply it once around the NavHost, plus `consumeWindowInsets` | `ui/navigation/HabitableNavHost.kt` |
| How is the chart drawn? | A Compose Canvas (`drawWithCache`): paths are built once per size and data, the animation only redraws; long histories are thinned to 800 points keeping each bucket's minimum and maximum | `ui/components/TemperatureChart.kt` (`buildChartDrawing`), `domain/usecase/BuildChartData.kt` (`downsample`) |

### Data, async and persistence

| Question | Short answer | Where |
| --- | --- | --- |
| What is in Room? | Apartments, sensors, readings, violations, issues, timeline events, outdoor weather, photos; version 2 with a written migration | `data/local/HabitableDatabase.kt` (`MIGRATION_1_2`) |
| What is in DataStore? | Settings: sensor mode, thermometer address, reading interval, sign-in token | `data/settings/SettingsRepository.kt` |
| Walk through one reading | Sensor emits a sample → foreground service → `ReadingsRepository.ingest()` stores it, runs quality checks and the violation detector, opens or extends a heat issue in one transaction | `service/SensorForegroundService.kt`, `data/repository/ReadingsRepository.kt` (`ingest`) |
| How is a violation detected? | Pure Kotlin: compare each reading with the limit for that time of day and season; readings below the limit form a period, a period of two hours opens a heat issue, and a gap over 30 minutes ends the period | `domain/rules/HeatRules.kt`, `domain/rules/ViolationDetector.kt` |
| Where are coroutines used? | `viewModelScope` for UI actions, `Flow` from Room for live lists, `callbackFlow` to turn Bluetooth callbacks into a stream, `suspend` Retrofit calls | `data/sensor/BleSensorSource.kt`, `data/repository/WeatherRepository.kt` |
| Where is WorkManager used? | Sync every 6 hours when online; reminders once a day | `work/SyncWorker.kt`, `work/ReminderWorker.kt` |

### API, failures and the device

| Question | Short answer | Where |
| --- | --- | --- |
| Which API and how are its states handled? | Open-Meteo hourly temperature (no key); `WeatherStatus` is Loading, Fresh, or Error with/without cache; results are cached in Room so the chart works offline | `data/repository/WeatherRepository.kt` (`WeatherStatus`) |
| How do you read the thermometer? | It broadcasts temperature in its Bluetooth advertisement; we scan with a filter for its address and decode Govee's manufacturer data | `data/sensor/BleSensorSource.kt` (`decodeGovee`), `data/sensor/BleDeviceScanner.kt` |
| How do you save battery? | Low-power scan, filtered in the Bluetooth chip, **batched**: the chip collects broadcasts and hands them over once per interval (1/5/10 min), so the processor sleeps | `data/sensor/BleSensorSource.kt` (`setReportDelay`, `onBatchScanResults`) |
| Why Location permission? | The thermometer also looks like a beacon; Android hides beacons from apps that promise not to use location. We never read the position | `data/sensor/BleSupport.kt` |
| What if a permission is denied or Bluetooth is off? | `BleSupport.problem()` returns a sentence saying what to fix; Start refuses and Sensors shows it | `data/sensor/BleSupport.kt` |
| Photo integrity? | System camera writes to our private folder through a FileProvider; we hash the file with SHA-256 and store the row and timeline event in one transaction | `data/repository/PhotosRepository.kt` (`add`), `domain/usecase/Sha256.kt` |
| How is the PDF made? | `BuildEvidenceReport` (pure, tested) decides the content; `PdfEvidenceWriter` draws it with Android's `PdfDocument`, wrapping text across pages | `data/export/PdfEvidenceWriter.kt` |
| Long or pasted input? | `TextInput` limits each field, makes one-line fields one line, shows a counter and "Limit reached" | `domain/text/TextInput.kt`, `ui/components/FieldSupport.kt` |

### Tests

| Question | Short answer | Where (under `android/app/src/`) |
| --- | --- | --- |
| Show a logic test | Heat-rule boundaries: 6:59 vs 7:00 AM, Sept 14 vs 15 | `test/.../domain/HeatRulesTest.kt` |
| Show a ViewModel test with a fake | `NewIssueViewModel` gets `FakeIssueWriter` through the `IssueWriter` interface; includes a forced storage failure | `test/.../ui/NewIssueViewModelTest.kt` |
| Show a Compose UI test | Report an issue end to end, and the Gettysburg paste | `androidTest/.../ui/ReportIssueFlowTest.kt`, `androidTest/.../ui/HomeScreenTest.kt` |
| Show an integration test with an error | Fake Open-Meteo switched offline; cached hours still shown | `androidTest/.../data/WeatherRepositoryErrorTest.kt` |
| How do you run them all? | `.\run-all-tests.ps1`; CI runs unit and backend tests on every push | repo root, `.github/workflows/ci.yml` |

### Workflow and contributions

| Question | Answer |
| --- | --- |
| Show your commits / PRs | GitHub → Pull requests → closed: #26–#31 for this update, each reviewed and merged by the other student |
| Scrum master? | Priyanshu the week of Nov 17, Ankita the week of Nov 24 |
| How did you use AI? | Say it plainly, as in [AI_USE.md](AI_USE.md): Claude drafted much of the code and docs from our stories and bug reports; we decided the scope and design, ran and tested everything, reviewed every PR, and can explain each part. Then show you can: open the file for the feature you own and walk through it |

**Each of us should be able to open and explain these without notes:**
- **Priyanshu:** `BleSensorSource.kt`, `ReadingsRepository.ingest`, `ViolationDetector.kt`, `WeatherRepository.kt`, `TemperatureChart.kt`, `HabitableNavHost.kt`, `NewIssueViewModelTest.kt`.
- **Ankita:** `PhotosRepository.add`, `Sha256.kt`, `PdfEvidenceWriter.kt` and `EvidenceReport`, `ReminderWorker.kt` and `ReminderRules`, `IssueDetailScreen.kt` (photo launcher, notice, escalate), `ReportIssueFlowTest.kt`.
