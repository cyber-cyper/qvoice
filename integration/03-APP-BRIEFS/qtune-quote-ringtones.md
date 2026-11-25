# Brief: QTune: Quote Ringtones (com.riniso.qtune)

**Speaks:** English quotes, live (preview, players) and **rendered to
audio files that become ringtones**.

## What QVoice brings

The best case for QVoice. A ringtone is rendered once and heard many times,
so the slow, most natural voices (Kokoro: 48 voices with US and British
accents) are worth it here. Render time doesn't matter the way it does for
live speech.

## Changes

1. Add `<queries>` and copy RinisoSpeech (guide steps 1–2).
2. **Rendering:** where the app calls `synthesizeToFile`, use
   `speech.speak(Request(..., file = target))`. Wait for `onDone` before
   post-processing, and treat `onError` as "render failed" (the helper
   already tried the phone's engine).
3. **Read the WAV header.** QVoice outputs 22,050, 24,000 or 44,100 Hz
   depending on the voice. Every step that trims, normalises, adds silence
   or converts the file must use the header's rate and channel count. Check
   the app's ringtone pipeline for a hard-coded rate. That is the most
   likely bug.
4. **Voice choice for ringtones:** offer the voice list
   (`speech.voices(Locale.US)`) with labels and gender. Store the chosen
   name per ringtone set, so re-rendering later gives the same voice. If
   the stored voice was deleted, the helper uses the engine's default.
5. Settings: Voice engine, the "Get QVoice" card, and "More voices" (opens
   QVoice's library to download Kokoro and the others).
6. **Live preview:** the same RinisoSpeech, `USAGE_MEDIA`.

## Watch out

- If ringtones are rendered in the background (WorkManager, say), the
  helper can be used from that worker. Create it on the main thread
  (`Handler(Looper.getMainLooper())`), or render from a foreground context.
  RinisoSpeech is main-thread only.
- Large renders with Kokoro on a slow phone take several seconds per
  quote. Show progress.
- Keep the app's existing "finalize" step for every rendered file.

## Tests (on the phone)

1. Render a ringtone with the built-in voice, then with a Piper voice
   (22.05 kHz) and Kokoro (24 kHz). Each plays as a ringtone at the right
   speed and pitch: a wrong rate sounds like chipmunks or slow motion.
2. Uninstall QVoice: rendering still works with the phone's voice.
3. Delete the voice a ringtone set uses: re-rendering falls back to the
   default voice without an error.
