# QVoice integration handover (Riniso apps)

Prepared 25 November 2026 from the QVoice project (repository state: version
1.0.1, versionCode 2, slice 24). **Updated 26 November 2026: RinisoSpeech
1.1.0**, after LifeCue's review (see `08-LIFECUE-REPLY.md`). This zip contains everything an app's chat
needs to add QVoice's voices to that app, plus a template for the handover
that chat sends back.

## The apps

| App | Package | Text it speaks | What QVoice brings |
|---|---|---|---|
| QVoice Text To Speech Offline | com.riniso.qvoice | (the engine) | — |
| QTune: Quote Ringtones | com.riniso.qtune | English quotes | natural offline English voices, male and female |
| LifeCue Alarm Clock & Reminder | com.riniso.lifecue | English quotes, reminders | the same, plus fast voices that start at once for alarms |
| QTune: Tamil Wisdom | com.riniso.philosophers | Tamil and English | English through QVoice. **Tamil stays on Google's engine**, because QVoice has no Tamil voice |

## How to use this package

1. **Open the app's own chat** (QTune Quote Ringtones, LifeCue, QTune Tamil
   Wisdom), one at a time. Attach this zip and the app's latest source zip.
2. **Paste the prompt** from `04-PROMPT-FOR-APP-CHAT.md`. In it, the chat:
   1. reads this package;
   2. reads the app's code and writes the app's handover
      (`05-HANDOVER-BACK-TEMPLATE.md`);
   3. integrates QVoice as one small slice, using its usual rules: read the
      code first, CHANGELOG, TESTING, a zip, a versionCode bump.
3. **Bring each app's handover back here** (the QVoice chat) only if it asks
   for something QVoice itself should change. Otherwise the app chat
   finishes the job alone.
4. Do the apps in this order: **LifeCue → QTune Quote Ringtones → QTune Tamil
   Wisdom**. LifeCue is the simplest test of live speech. Quote Ringtones
   adds rendering to a file. Tamil Wisdom adds the two-engine split.

## What's inside

| File | For |
|---|---|
| `01-QVOICE-FACTS.md` | What QVoice is and how it behaves as an engine (voices, languages, names, speeds). This is the contract the apps rely on. |
| `02-INTEGRATION-GUIDE.md` | The design, the step-by-step integration, the UX rules, Play and privacy notes, and pitfalls. |
| `03-APP-BRIEFS/` | One brief per app: what to change, where, and how to test it. |
| `04-PROMPT-FOR-APP-CHAT.md` | The text to paste into each app's chat. |
| `05-HANDOVER-BACK-TEMPLATE.md` | What each app's chat writes about its app. |
| `06-TEST-AND-RELEASE-CHECKLIST.md` | Device tests and release steps, the same for every app. |
| `07-ROADMAP.md` | Where QVoice goes next (global languages) and the phases for the apps. |
| `08-LIFECUE-REPLY.md` | What changed in 1.1.0, and answers to LifeCue's questions. |
| `code/com/riniso/speech/` | **RinisoSpeech**: the shared helper, two Kotlin files. It is copied unchanged into every app and compiled against Android 35 with warnings as errors. |
| `code/test/com/riniso/speech/` | Its unit tests (13, all passing). |
| `assets/qvoice-icon-512.png` | QVoice's icon, for the optional "Get QVoice" card. |

## Decisions already made (and why)

- **QVoice is optional.** Every app keeps working exactly as today without it.
  With QVoice installed, the setting "Automatic" (the default) uses QVoice for
  every language it has a voice for, and the phone's engine for the rest.
- **One shared helper, copied rather than shared as a library.** The apps have
  separate repositories and builds, and two files are simpler than a
  published library. The header carries a version number
  (`RINISO_SPEECH_VERSION`). The canonical copy should live in the QVoice
  repository under `integration/`; change it there first, then copy it out.
- **Tamil and Malayalam go to Google's engine.** QVoice has no voice for
  them, and the helper routes them automatically.
- **Licence of the helper: Apache-2.0**, set in its header. QVoice is
  GPL-3.0, but the apps only talk to it through Android's TextToSpeech
  service, so they don't become GPL. The helper is your own code, so it can
  carry a permissive licence and go into closed apps. Change the header if
  you prefer another licence; it's your call. This isn't legal advice.
- **No nagging.** The apps offer QVoice in one place (their voice/speech
  settings), as a dismissible card, never as a pop-up.

## What was checked, and what wasn't

- `RinisoSpeech.kt` and `SpeechRouting.kt` compile against the Android 15
  (API 35) SDK with Kotlin 2.2 and warnings as errors.
- The rules have 13 unit tests, all passing:
  - which engine speaks;
  - which voice is chosen;
  - gender, region and fast start;
  - labels, voice references and script detection.
- **Not yet run on a phone:** the Android side, i.e. engine start-up, the
  fallback to the phone's engine, and keeping two engines from talking at
  once. The first app's device tests (LifeCue,
  `06-TEST-AND-RELEASE-CHECKLIST.md`) are that check. If something there
  misbehaves, fix it in the canonical copy and send it to all apps, not in
  one app alone.
