# Reply to LifeCue's review (26 November 2026)

LifeCue's plan is right, and its pushback improved the design for every app.
RinisoSpeech **1.1.0** is in `code/`. It compiles against API 35 with
warnings as errors, and has 13 unit tests, all passing.

## What changed in RinisoSpeech 1.1.0

| LifeCue's point | 1.1.0 |
|---|---|
| A chosen voice must stay tied to its engine (a Google voice must not turn into QVoice) | `VoiceRef`, stored as `qvoice:<name>` / `system:<name>` in the existing voice-name field; a bare old name reads as a phone voice. `Request.voice` replaces `voiceName`. A chosen voice decides the engine (`engineFor(..., pinned)`); a QVoice voice falls back to the phone's voice only when QVoice is gone. |
| QVoice's voices across languages, for one picker | `voices(locale = null) { options -> }`: both engines, every language, QVoice first; each with `ref`, `label`, `locale`, `gender`, `fast`. |
| Pass on where the voice is in the text | `Callback.onRange(id, start, end, engine)` from `onRangeStart`. Google reports words; **QVoice reports nothing yet** (roadmap Q1), so LifeCue's sentence-by-sentence speaking stays the baseline for its ringing screen. |
| `getApplicationInfo(String, int)` deprecated on API 33 | `ApplicationInfoFlags` on 33+, the old call below. |
| Android silently binds Google when QVoice can't start | Checked after start: QVoice always lists its built-in voice (`kitten-nano-en`); without it the instance counts as failed (`QVOICE_REPLACED_BY_ANDROID`) and the phone's voice speaks. A failed start is retried after a minute, not never. |
| Measure installs from the card | `QVoice.openPlayListing(context, source = "lifecue")`: a UTM `referrer` on the Play link, counted in Play Console → Tracked channels (UTM). |
| Which engine spoke and why it fell back (opt-in analytics) | `Callback.onFallback(id, FallbackReason)`: slow to start, failed to start, replaced by Android, refused, no sound in time. `onStart`/`onDone` carry the engine. |
| Cues in any language | `ScriptLanguage.guess(text)` (certain scripts only; null for Latin, Cyrillic, Arabic, Devanagari), `canSpeak(locale) { qvoice, system -> }` to check when a cue is saved, `QVoice.openVoiceLibrary(context, locale)` with the language extra (QVoice honours it from roadmap Q1). |

LifeCue's own handover (`LifeCue-QVoice-Handover.md`) didn't reach the QVoice
chat; only its summary did. Items 1–9 are covered as far as the summary names
them. If an item isn't in the table above, send that section here.

## Answers to LifeCue's questions

1. **Is QVoice in production?** The owner reports it's live on Google Play.
   Check once: open https://play.google.com/store/apps/details?id=com.riniso.qvoice
   signed out (or on a phone that isn't a tester's). If the page opens,
   show the card. If it doesn't, ship with the card hidden, as LifeCue
   proposed.
2. **Automatic as the default for everyone:** yes. No chosen voice changes.
3. **Helper first:** done, 1.1.0.
4. **Chunk B after BL-507:** yes. Two notes for it:
   - store the pre-made audio in **device-protected storage**
     (`createDeviceProtectedStorageContext()`), or it can't be read before
     the first unlock, which is the case it exists for;
   - render it while the phone is unlocked. QVoice itself can't run before
     the first unlock.

## LifeCue: all languages

Cues are user-written, so LifeCue can't assume English. It should:
- find each sentence's language with the cue's own language setting if it
  has one, else `ScriptLanguage.guess`, else the phone's language;
- check `canSpeak` when a cue is saved, and offer "Get a voice" if neither
  engine has the language;
- keep speaking sentence by sentence, each in its own language (LifeCue
  already does).

QVoice's side of "all languages" is roadmap phases Q2–Q4: fast voices in
about 39 languages.
