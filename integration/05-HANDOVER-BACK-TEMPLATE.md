# [APP] — handover for the QVoice integration

Filled in from the code by the app's chat (not from memory). Keep it short:
facts, file paths and line references.

## 1. The app

- Name, package, Play link:
- Current versionName / versionCode, and the next one to use:
- minSdk / targetSdk / compileSdk, Kotlin and AGP versions, Compose or Views:
- Languages of the app's interface (strings / tr() maps):
- Hard rules from its ARCHITECTURE.md or similar that this change must keep:

## 2. Speech today

| Where (file:line) | What it says | Language(s) | Live or to a file | Runs in (activity / service / worker) | Audio usage (media, alarm, ringtone…) |
|---|---|---|---|---|---|
| | | | | | |

- How TextToSpeech is created, and when it is shut down:
- Engine chosen today (the default, or a package name):
- Locales passed (exact `Locale(...)` / tags):
- Voice choice in settings today (gender, voice name, none):
- Rate and pitch settings:
- Checks for missing language data, and prompts to install it:
- Post-processing of rendered audio (trim, normalise, convert, ringtone
  finalisation), and any sample rate assumed anywhere:
- Existing `<queries>` block:

## 3. The plan for this app

- Files to change:
- Settings to add (wording):
- Where the "Get QVoice" card goes:
- Anything in the brief that does NOT fit this app, and what to do instead:

## 4. Needs from QVoice (only if any)

- …

## 5. Privacy and Play

- Privacy policy URL, and whether it gets the QVoice sentence:
- Data safety changes: (expected: none)
- Release notes line:
