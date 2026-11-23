# Generative AI and outside resources

## What we used

We used **Claude (Anthropic)**, an AI assistant, throughout the project.

| Used for | How |
| --- | --- |
| Code | Claude drafted a large share of the Kotlin app code, the tests and the backend from our user stories, design decisions and bug reports, in both students' areas |
| Documentation and slides | Claude drafted the README, the docs in this folder and the slide decks from our notes |
| Explanation | We asked Claude to explain code, Android APIs (Room, WorkManager, Bluetooth LE scanning, Compose) and design trade-offs until we could explain them ourselves |
| Debugging | We ran the app and tests, sent the errors and screenshots back, and worked through fixes (for example the bottom-navigation back bug, the edit-apartment data loss, and a missing test import) |

## What we decided and did ourselves

- The product idea, the user stories and their priority, the scope cuts (system camera instead of CameraX, no Wear OS app), the choice of the Govee H5075 thermometer and the demo data.
- Every build, test run and manual test happened on our own machines, the emulator and a real phone; results in TESTING.md are from those runs.
- Every change went through a branch and a pull request that the other student reviewed before merging.
- We are responsible for all submitted code and can explain and locate any part of it ([UPDATE2_SCRIPT.md](UPDATE2_SCRIPT.md) lists where each feature lives).

## How we verified AI output

- Pure logic (heat rules, violation detection, text limits, PDF content) is covered by unit tests at the legal and input boundaries.
- Data and UI paths are covered by on-device tests on a real Room database and Compose UI tests.
- Facts about the law were checked against 105 CMR 410.180 and MassLegalHelp; API behaviour against the official Android and Open-Meteo documentation.
- When AI output was wrong (for example a test that did not compile), the failure was found by running it, then fixed and re-run.

## Outside resources

Libraries, APIs and legal sources, with their licenses, are listed in the README under "Outside resources and licenses". No code was copied from other projects.
