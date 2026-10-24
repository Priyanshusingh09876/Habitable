# Generative AI use statement

This project follows the BU CDS Generative AI policy and the CS 501 course policy. We disclose below what we used AI for, what we did ourselves, and representative prompts. Both team members have read, run and can explain every file in this repository; "the AI produced it" is never our explanation for anything.

## Tool

Claude (Anthropic), used through the Claude app between October 14 and November 10, 2026.

## What we used it for

| Task | How AI helped | What we did |
| --- | --- | --- |
| Proposal and presentation | Reviewed our October 6 draft against the instructor's verbal feedback; drafted slide and proposal text; produced UI mockups for the proposal. | Chose the product, scope and priorities; corrected the §127L sequence after checking the statute ourselves; rewrote wording to our own voice. |
| Legal rules | Pointed us to 105 CMR 410.180 and MGL c.111 §127L. | Read the regulation text, confirmed 68/64/78 °F, 7 AM / 11:01 PM and Sept 15 – May 31, and wrote the unit tests at those boundaries. |
| Architecture | Discussed Room-as-source-of-truth, repository boundaries, a hand-written DI container vs. Hilt, and the Canvas-vs-Vico chart decision. | Made the decisions and documented them in ARCHITECTURE.md and UPDATE1.md. |
| Code | Generated first drafts of the domain layer, Room entities/DAOs, repositories, ViewModels, Compose screens, the foreground service, the backend routes and the test suites. | Reviewed every file for correctness and for consistency with our architecture; fixed logic errors found in review (the 11:00:59 PM boundary, the Govee decoder, linking a second violation to the open issue); ran the backend tests; build and run the app in Android Studio; wrote the demo script from what the app actually does. |
| Documentation | Drafted README, BACKLOG and this statement from our notes. | Edited for accuracy against the code and our actual process. |

## What we did not use AI for

Team decisions, the working agreement, scrum-master rotation, our individual reflections, and the final judgement on every line that ships.

## Representative prompts

- "Here is the transcript of our proposal presentation. What did the instructor ask for and what should change?"
- "Massachusetts heat regulation: at what exact times does the 68 °F / 64 °F requirement switch, and what is the heating season?"
- "Write a pure-Kotlin ViolationDetector that folds readings into open/closed violation periods, with a minimum duration before an issue is opened."
- "Review this Compose chart code for a step-shaped limit line and shading only where indoor < limit."
- "Our Express tests fail with MODULE_NOT_FOUND when using `node --test tests/`; why?"

## Data we did not share

No real addresses, landlord names, photos, credentials or personal data were given to the AI service. All sample data in the repo (12 Pratt St, Apt 3; "M. Rivera") is fictional.
