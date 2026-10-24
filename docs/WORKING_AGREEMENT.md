# Working agreement

Agreed October 20, 2026. Revisit at each milestone.

## Roles

| Area | Owner | Second |
| --- | --- | --- |
| Sensing: BLE, simulator, Room readings, heat rules, charts, Open-Meteo, WorkManager, backend | Priyanshu Singh | Ankita |
| Evidence: issues and timeline, CameraX + hashing, notices and Escalate, deadline text, PDF packet | Ankita Patra | Priyanshu |
| Shared: Room schema, navigation, integration, tests, docs | Both | — |

Both of us must be able to run the whole project and explain any file in it. Before each milestone we swap: each person walks the other through their half.

## Scrum master rotation

| Week of | Scrum master |
| --- | --- |
| Oct 20 | Priyanshu |
| Oct 27 | Ankita |
| Nov 3 | Priyanshu |
| Nov 10 | Ankita |
| Nov 17 | Priyanshu |
| Nov 24 | Ankita |
| Dec 1 | Priyanshu |

The scrum master runs the two weekly calls, keeps the GitHub Project board current, and writes the milestone notes. The role does not make one person the other's manager.

## Cadence

- Daily async check-in on WhatsApp (what I did, what's next, blockers).
- Two 30-minute calls per week: **Tuesday** (plan) and **Friday** (demo to each other + review).
- Iterations of one week; each ends with something that runs.

## Repository rules

- One repository. `main` is protected: no direct pushes.
- Branches: `feat/<area>-<short-name>`, `fix/<short-name>`, `docs/<short-name>`.
- Every change goes through a pull request reviewed by the other person. Small PRs (one story or task).
- Conventional commits: `feat(sensing): step-shaped limit line`, `fix(issues): link second violation to open issue`, `docs: update 1 notes`.
- CI must be green before merge (backend tests, Android build + unit tests). Instrumented tests run locally before opening a PR that touches Room or UI.
- Secrets never enter the repo. `local.properties` and `.env` are git-ignored; `*.example` files are committed.

## Definition of done (for a story)

1. Acceptance criteria in BACKLOG.md met.
2. Unit test for any new domain logic; UI change checked in both themes and with font scale 150 %.
3. Loading / empty / error states handled where a network or sensor is involved.
4. PR reviewed and merged; board updated; README or docs updated if setup changed.

## When things go wrong

- Blocked for more than a day → say so in the check-in; the other person helps or we re-scope.
- Missed work or communication problems → raise it on the next call; if it threatens a milestone, we tell the instructor early.
- Scope changes are recorded in UPDATE*.md under "Decisions and scope changes".
