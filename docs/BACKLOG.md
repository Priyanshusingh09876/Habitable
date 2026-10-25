# Backlog

Source of truth for scope. Mirrors the GitHub Project board; update both when a story moves.
Sizes: **S** < 1 day · **M** 1–3 days · **L** 3–5 days.

## MVP — must ship (sprints 1–3)

| ID | User story | Acceptance criteria | Owner | Size | Sprint | Status |
| --- | --- | --- | --- | --- | --- | --- |
| US-1 | As a renter, I can create an account and add my apartment, so my evidence is tied to one address. | Sign up / log in with email + password (JWT); add address, unit, landlord contact; data survives app restart; validation errors shown inline. | Both | S | 1 | Done |
| US-2 | As a renter, I can pair a BLE thermometer and take a placement photo, so readings are tied to a location. | Scan and pair one sensor by MAC; placement photo saved with a hash; readings stored in Room every 10 minutes while the screen is off. | Priyanshu | M | 1–2 | Partial (scan + decoder done; hardware + photo pending) |
| US-3 | As a renter, I can see my temperature charted against the legal limit, so I know when it is too cold. | Chart shows indoor, outdoor and the 68/64 °F step limit; shading only where below the limit; Night / 24 h / 7 d / 30 d ranges; works offline. | Priyanshu | L | 1 | Done |
| US-4 | As a renter, my photos are time-stamped and hashed, so I can show they weren't edited later. | SHA-256 taken at capture; hash sent to `/evidence/anchor` when online; photos cannot be edited in the app; thumbnails on the timeline. | Ankita | M | 2 | Not started |
| US-5 | As a renter, I can open an issue and attach evidence. | Category, title, description with validation; photos and readings attach to it; a heat issue opens automatically after 2 h below the limit; timeline in order. | Ankita | M | 1 | Done |
| US-6 | As a renter, I can send a dated notice and get reminders for next steps. | Notice built from a template citing 105 CMR 410.180 with the "not legal advice" line; sent via email or share sheet and logged; Escalate follows notice → inspection → certified; WorkManager reminders once certified. | Ankita | M | 1–2 | Partial (state machine, timeline, letter template + email done; WorkManager reminders pending) |
| US-7 | As a renter, I can export one PDF for the landlord, legal aid or an inspector. | PDF includes the chart, weather, photos with hashes, notice log and data flags; generated on the phone with `PdfDocument`; shareable. | Ankita | L | 3 | Not started |
| US-8 | As developers, we can demo without hardware. | Simulated-sensor mode produces readings through the same pipeline as BLE; can replay a cold night on demand; live mode for motion. | Priyanshu | S | 1 | Done |
| US-9 | As developers, we have the backend deployed. | Node/Express API with `/auth`, `/readings`, `/evidence/anchor`, `/issues` running on Render with Neon PostgreSQL; tests pass in CI. | Priyanshu | M | 1–2 | Done locally; deploy pending |
| US-10 | As a renter, my outdoor weather is shown next to indoor readings. | Open-Meteo hourly temperature for the apartment's location; cached in Room; loading, error and stale states handled; retry. | Priyanshu | S | 1 | Done |

## Second priority — after the MVP works

| ID | Story | Notes | Owner |
| --- | --- | --- | --- |
| US-11 | Landlord communication log | Replies, calls and emails as timeline events; `/issues/notices` already exists server-side. | Ankita |
| US-12 | Mold-risk alerts from humidity | Humidity is already logged; rule + notification. | Priyanshu |
| US-13 | Move-in / move-out photo checklists | Keeps the app useful between problems. | Ankita |

## Stretch — only if time allows

| ID | Story |
| --- | --- |
| US-14 | Noise log in decibels only (no audio recorded) |
| US-15 | Building-wide counts: neighbors opt in to share only "unit X has the same problem" |
| US-16 | Open311 / address lookup |
| US-17 | Encrypted backup of the evidence folder |
| US-18 | Support more than one apartment / sensor |
| US-19 | Tamper-evident install: numbered seal sticker in the placement photo, weekly seal-check photo, readings voided (shown as "invalid", never deleted) from a broken seal or gap until a fresh verified photo |
| US-20 | Witnessed install: landlord confirms sensor location via a share link; a refusal is recorded with a timestamp |

## Technical tasks (not user-facing)

| Task | Sprint | Status |
| --- | --- | --- |
| Project scaffold: Gradle, version catalog, CI | 1 | Done |
| Room schema v1 + mappers | 1 | Done |
| Foreground service + notification channels | 1 | Done |
| WorkManager sync worker + custom WorkerFactory | 1 | Done |
| Weather cross-check detector (indoor tracking cold outdoors ⇒ suspicious span) | 1 | Done |
| Unit tests for rules, detector, quality, simulator, cross-check, chart data | 1 | Done (36 tests) |
| Instrumented tests: Room pipeline, Compose Home flow | 1 | Done (6 tests, run locally) |
| Backend API + 13 tests | 1 | Done |
| Geocode address for Open-Meteo | 2 | To do |
| Deploy backend (Render + Neon) | 2 | To do |
| Overnight BLE test on two phone brands | 2 | Blocked on hardware |
| Emulator in CI for instrumented tests | 3 | To do |
| Accessibility pass (TalkBack, font scale 200 %, contrast check) | 3 | To do |
| Short paper (1,000–1,500 words) + architecture diagram | 3 | To do |

## Non-goals

Legal advice · iOS · a live landlord portal · audio recording · rent-withholding automation.
