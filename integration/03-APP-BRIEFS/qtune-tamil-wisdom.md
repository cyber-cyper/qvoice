# Brief: QTune: Tamil Wisdom (com.riniso.philosophers)

**Speaks:** Tamil and English quotes, proverbs and the Thirukkural. It does
this live (Quote Radio, previews) and renders to ringtone files. Its own
hard rules apply (from its ARCHITECTURE.md): versionCode exactly +1 and
never reused, no READ_CALL_LOG, all ringtone paths end in
`finalizeRingtoneFile()`, opt-in backup rules, and new screens inside
`QTuneBackground`.

## What QVoice brings, and what it doesn't

- **English:** natural offline voices through QVoice.
- **Tamil: nothing yet.** QVoice has no Tamil voice, and that was a
  deliberate decision (Indian-language voices are left to Google's engine
  for now). Tamil must keep going to the phone's engine, normally Google's
  speech services with its Tamil voice. RinisoSpeech does this by itself:
  QVoice answers `LANG_NOT_SUPPORTED` for `ta`, so Tamil requests go to the
  system engine.

## Changes

1. Add `<queries>` and copy RinisoSpeech (guide steps 1–2).
2. **Give every text its real language.** Tamil text uses
   `Locale.forLanguageTag("ta-IN")`, English uses `en-IN`. Where a quote
   has both (Tamil and its English meaning), send two requests one after
   the other. Don't send mixed text in one request: QVoice would refuse the
   Tamil part, and the whole request would fall back to the system engine.
3. Quote Radio, previews and ringtone rendering: the same changes as
   QTune: Quote Ringtones (see that brief): read the WAV header, keep
   `finalizeRingtoneFile()`.
4. **Settings:**
   - The one voice choice (Automatic / Your phone's voice / A voice you
     choose), as in the guide.
   - The English voice: gender or a named voice.
   - The Tamil voice stays as the app has it today.
   - The "Get QVoice" card should say "for English quotes", so nobody
     expects Tamil from it (`source = "qtune_tamil"`).
5. **Tamil voice check:** keep (or add) the app's existing check that the
   phone's engine has Tamil, and its prompt to install Google's Tamil voice
   data. QVoice changes nothing there.

## Watch out

- The privacy policy page lists the package as `com.riniso.qtune`. That is
  a known mismatch: fix it to `com.riniso.philosophers` when the policy
  gets its QVoice sentence.
- Quote Radio runs in the background: one RinisoSpeech in its service, with
  `USAGE_MEDIA`.

## Tests (on the phone)

1. Tamil quote: spoken by Google's Tamil voice, with QVoice installed and
   without it.
2. English quote: QVoice when installed, Google's when not.
3. A Tamil quote with its English meaning: Tamil voice, then QVoice,
   without a gap longer than about a second.
4. Ringtone renders in both languages; `finalizeRingtoneFile()` still runs.
