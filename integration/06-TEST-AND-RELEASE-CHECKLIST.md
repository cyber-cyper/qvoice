# Test and release checklist (every app)

## Before building

- [ ] `com/riniso/speech/` is identical to the handover's copy. Check with
      `RINISO_SPEECH_VERSION`, and diff it.
- [ ] `<queries>` has the QVoice package and the TTS_SERVICE intent.
- [ ] No other `TextToSpeech(` remains in the app (search the code).
- [ ] The unit tests pass (`SpeechRoutingTest` plus the app's own).

## On the phone (about 10 minutes)

Useful adb lines (PowerShell):
```powershell
$adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
& $adb shell am force-stop com.riniso.qvoice      # QVoice's process gone: tests the cold start
& $adb logcat -s QVoice.Tts TextToSpeech          # what the engine did
```

1. **Without QVoice** (uninstall it, or disable it in App info): the app
   speaks exactly as before. There is no error and no QVoice prompt except
   the dismissible card in settings.
2. **Install QVoice from Play** using the card's **Get QVoice** button,
   then come back. The card now says **More voices**, and English speech
   uses QVoice (its voice sounds different from Google's).
3. **Voice engine → Phone's default voice:** QVoice is never used.
4. **Gender:** Female and Male give QVoice's female and male voices.
5. **Cold start:** force-stop QVoice, then speak. The first words come
   within about a second with the built-in voice.
6. **Each app's own tests** from its brief (alarms: locked phone and a
   3-second fallback; ringtones: three sample rates; Tamil Wisdom: Tamil
   stays on Google).
7. **TalkBack on:** the app's speech and TalkBack take turns. Nothing is
   cut off for good.

## Release

- [ ] versionCode +1 (each app's rule), and versionName as the app does it.
- [ ] CHANGELOG and TESTING updated.
- [ ] Release notes: "Natural offline voices with QVoice (optional, free)."
- [ ] Privacy policy: the optional QVoice sentence (see the integration
      guide).
- [ ] Data safety: no change.
- [ ] Internal testing first, check the pre-launch report, then promote.
