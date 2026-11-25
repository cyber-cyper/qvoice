# Integrating QVoice into a Riniso app

## The design in one paragraph

The app keeps speaking through Android's TextToSpeech API, but through
**RinisoSpeech** (`code/com/riniso/speech/`) instead of its own
`TextToSpeech` instance. Each request names its language:
- if QVoice is installed and has a voice for that language, QVoice speaks
  it;
- otherwise the phone's default engine speaks it (as today);
- if QVoice refuses or is too slow, the same request is repeated on the
  phone's engine.

The user gets one setting, "Voice engine": **Automatic** (the default) or
**Phone's default voice**. Optionally there is also a voice (gender or a
named voice) for each language the app speaks.

## Step by step

1. **Copy the helper.** Put the two files into
   `app/src/main/java/com/riniso/speech/` (`RinisoSpeech.kt`,
   `SpeechRouting.kt`) and the test into
   `app/src/test/java/com/riniso/speech/`. Don't edit them in the app. If
   something must change, change the canonical copy (QVoice repository,
   `integration/`), raise `RINISO_SPEECH_VERSION` and copy it to every app.
   No new dependencies are needed: only the Android SDK and Kotlin's
   standard library.
2. **Manifest: package visibility.** This is required on Android 11+, or
   `isInstalled` returns false and TextToSpeech can't list engines:
   ```xml
   <queries>
       <package android:name="com.riniso.qvoice" />
       <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
   </queries>
   ```
   No permission is needed: this is not QUERY_ALL_PACKAGES. If the app
   already has a `<queries>` block, add to it.
3. **Replace the app's TextToSpeech** wherever it speaks:
   ```kotlin
   val speech = RinisoSpeech(
       context,
       choice = { settings.engineChoice },              // EngineChoice.AUTOMATIC / SYSTEM
       audioAttributes = AudioAttributes.Builder()
           .setUsage(AudioAttributes.USAGE_MEDIA)       // USAGE_ALARM in an alarm
           .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
           .build(),
   )
   speech.speak(RinisoSpeech.Request(
       id = "quote-42",
       text = quote.text,
       locale = if (quote.isTamil) Locale.forLanguageTag("ta-IN") else Locale.forLanguageTag("en-IN"),
       gender = settings.voiceGender,                   // VoiceGender.ANY / FEMALE / MALE
       callback = object : RinisoSpeech.Callback {
           override fun onDone(id: String, engine: SpeechRouting.Engine) { /* next quote */ }
           override fun onError(id: String) { /* show "couldn't speak" */ }
       },
   ))
   ```
   For a file (ringtones), pass `file = File(...)`. The callback's `onDone`
   then means the file is written. Call `speech.shutdown()` where the old
   code called `tts.shutdown()`.

   About locales: QVoice's English voices are en-US and en-GB.
   `Locale("en", "IN")` still gets them, because the language matches and
   the region only ranks voices. Keep using the app's own locale.
4. **Settings: one setting, maybe two.** These go in the app's existing
   voice or speech settings:
   - **Voice engine:** *Automatic: QVoice when installed, else the phone's
     voice* / *Phone's default voice*. Store it as `EngineChoice.name`.
   - **Voice** (optional, per language the app speaks):
     `speech.voices(locale) { engine, options -> }` lists name, label and
     gender. Store the name and pass it as `voiceName`. Or offer just
     "Female / Male / Any" (`gender`), which needs no list.
   - A **"Get natural offline voices" card**, shown only when QVoice isn't
     installed. Use QVoice's icon (`assets/`), one line ("Free, offline,
     no ads: QVoice adds natural English voices"), a **Get QVoice** button
     (`QVoice.openPlayListing`) and a **Dismiss** button that is remembered.
     When QVoice is installed, show **More voices** instead
     (`QVoice.openVoiceLibrary`).
5. **Re-check on return.** When the settings screen resumes, call
   `QVoice.isInstalled` again: the user may just have installed QVoice from
   the card.
6. **Tests.** Copy `SpeechRoutingTest.kt` and add a test for any routing
   the app adds itself (e.g. which locale a quote gets). Then run the
   device checks in `06-TEST-AND-RELEASE-CHECKLIST.md`.

## UX rules (best practice for an optional companion app)

- **Never block or nag.** The app works fully without QVoice. QVoice is
  offered in one place, dismissible, never in a dialog, and never on first
  launch.
- **Say what the user gets, in one line:** "Natural, offline voices: free,
  no ads." Don't explain engines.
- **Show who is speaking.** Next to a voice preview, a small "via QVoice"
  or "via phone's voice" (from the `engine` in the callback) saves support
  emails.
- **Preview button:** "Hear a sample" speaks one short quote with the
  current settings.
- **Respect the phone's settings.** With "Phone's default voice", the app
  never touches QVoice, even when it's installed.

## Play Console and privacy

- **Permissions:** none new. `<queries>` with a named package and the
  TTS_SERVICE intent is standard and needs no declaration.
- **Data safety:** unchanged. The text goes to a speech engine on the
  phone, as before, and QVoice sends nothing anywhere.
- **Privacy policy:** optionally add one sentence: "If QVoice (by Riniso)
  is installed, [App] may use its voices to read text aloud; QVoice works
  entirely on your device."
- **Store listing:** you may add "Works with QVoice for natural offline
  voices". Release notes: "Natural offline voices with QVoice (optional,
  free)."
- **versionCode:** +1 for the upload, under each app's own rule (QTune
  Tamil Wisdom: exactly +1, never reuse a number).

## Pitfalls (each one has bitten an app before)

1. **TextToSpeech starts asynchronously.** Never call `speak` right after
   constructing it. RinisoSpeech queues requests until the engine is ready
   and falls back to the system engine if QVoice isn't ready within 4 s.
2. **`setLanguage` caches the voice name.** After a `setVoice` the voice
   sticks until `setLanguage` is called again. RinisoSpeech calls
   `setLanguage` on every request.
3. **Android 11+ package visibility.** Without `<queries>`, QVoice looks
   uninstalled and the engine list is empty.
4. **Alarms (LifeCue):** speak with `fastStart = true` and
   `startTimeoutMs = 3000`, and call `warmUp(locale)` the moment the alarm
   fires. A Kokoro voice can take seconds on a cold start, and an alarm must
   never wait in silence. The timeout hands the text to the system engine.
5. **Ringtones (QTune):** a WAV from QVoice can be 22.05, 24 or 44.1 kHz.
   Read the header. If the ringtone pipeline resamples or trims, test with
   each sample rate.
6. **Mixed-language text:** send each part in its own language (split a
   Tamil quote and its English translation into two requests).
   RinisoSpeech routes each part to the engine that has its language.
7. **Instances:** create one RinisoSpeech per app or per service, not per
   screen. Each instance binds the engines, and starting QVoice costs time
   the first time.
8. **Background:** speaking from a service needs that service to be a
   foreground service (an alarm or media type) on Android 12+. The engine
   isn't the problem, the app's own process is.
