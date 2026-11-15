# Accessibility and adaptive layout

What the app does for people using a screen reader, large text or a different
screen size, and how we check it. Results are filled in after running the
manual cases in [TESTING.md](TESTING.md) (M19–M21).

## What is built in

| Area | What the app does | Where |
| --- | --- | --- |
| Screen reader labels | Every icon-only button has a description ("Back", "Share timeline"); decorative icons next to text are hidden from TalkBack so nothing is read twice | all screens |
| Headings | Section titles ("Photos", "Timeline", "Sensor mode") are announced as headings and read in normal case, not letter by letter, so TalkBack users can jump between sections | `SectionHeader` in `ui/components/Common.kt` |
| Chart | The temperature chart is one element with a spoken description; the numbers it shows are also on the stat tiles as text | `TemperatureChart.kt` |
| Photos | Each thumbnail is described with the time it was taken | `IssueDetailScreen.kt` |
| Not colour alone | A cold reading is red **and** says "6.6° below the 68°F limit"; status chips carry their label; the escalation tracker has step names | Home, Issue detail |
| Touch targets | Buttons are 48–52 dp tall; list rows and radio rows are full-width | all screens |
| Text scaling | All text uses `sp` through the Material typography; every screen scrolls, so nothing is cut off at the largest font size | all screens |
| Contrast | Body text is near-black on white or white on navy; muted text is `#4A5568` on white (about 7.5:1) | `ui/theme/Theme.kt` |
| No gesture-only actions | Everything is a button or a list row; no swipe or long-press is required | all screens |
| Clear errors | Problems are said in words with what to do next ("Turn on Bluetooth to read the thermometer"), never just a colour or an icon | Sensors, More, Setup |

## Adaptive layout

| Situation | Behaviour |
| --- | --- |
| Small and large phones | Layouts use `fillMaxWidth` and weights; the chart sizes itself from the canvas width |
| Rotation | State is kept (ViewModels + `rememberSaveable` for half-typed text and a photo in progress); screens scroll in landscape |
| Tablets, foldables | Content stays in a centred column at most 720 dp wide instead of stretching edge to edge (`HabitableNavHost.kt`) |
| Not yet | A two-pane tablet layout (issue list beside issue detail) and a navigation rail are planned for the final release |

## How we check it (manual)

1. **Large text:** Settings → Display → Font size → largest, and Display size → largest. Open every screen and scroll to the bottom.
2. **TalkBack:** Settings → Accessibility → TalkBack on. Swipe right through Home, Sensors, an issue and More. Use the headings control to jump between sections.
3. **Rotation and tablet:** rotate the phone on every screen; run once on a tablet emulator (Pixel Tablet).

| Check | Result | Notes |
| --- | --- | --- |
| Largest font, every screen (M19) | Not run yet | |
| TalkBack through Home and an issue (M20) | Not run yet | |
| Rotation and tablet (M21) | Not run yet | |

Record anything that fails here and in the known issues list in [UPDATE2.md](UPDATE2.md).
