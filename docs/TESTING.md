# Testing QVoice

## Testing a new zip (Windows PowerShell)

The same steps for every slice; the slice's own checks are in its section
below. Phone connected by USB with USB debugging on (or use Android Studio:
open the project, then the green ▶ Run button installs it on the phone).

1. Unpack over the project (the download folder and the zip's name change):

   ```powershell
   Expand-Archive -Force "$env:USERPROFILE\Downloads\QVoice-slice22-src.zip" -DestinationPath "C:\Users\Public\QVoice-slice02-fix1-src"
   ```

2. Unit tests, then build and install on the phone:

   ```powershell
   cd C:\Users\Public\QVoice-slice02-fix1-src\QVoice
   "sdk.dir=C:/Users/Public/sdk" | Set-Content local.properties
   & "C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1\bin\gradle.bat" test installDebug
   ```

   "BUILD SUCCESSFUL" at the end means the tests passed and the app is on
   the phone. A failure prints the reason above that line: paste it to me.
   If it says binary files are missing, run step 4 first, then this again.

   Unpacking over the folder keeps files a slice deleted. When a slice's
   section below lists deleted files, remove them once (they are never
   needed again).

3. Only if something on the phone misbehaves, the log (paste both outputs):

   ```powershell
   $adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
   & $adb logcat -d -s QVoice:I QVoice.Tts:I
   & $adb logcat -d -b crash
   ```

4. Optional, any time: the binary-file check (prints "in place" three
   times when all is well; downloads only what is missing):

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\get-binaries.ps1
   ```

## Automated checks

| Check | Command | Where it runs |
|---|---|---|
| Unit tests (233) | `gradle test` | your machine; also run in the authoring sandbox |
| Static project checks | `python tools/check_project.py` | anywhere with Python 3 |
| Kotlin compile of all non-Compose code (incl. both view models and the reading service with its media session and notification), warnings as errors | (sandbox: kotlinc 2.2.10 against android-35, sherpa-onnx 1.13.8, Commons Compress 1.27.1) | authoring sandbox |
| Compose screens compiled against stubs of the real Compose 1.9 / Material 3 1.3.2 API (made from the published API signature files), warnings as errors, then the Compose compiler plugin's checks | `bash tools/sandbox/verify.sh` step 4; stubs in `tools/sandbox/compose-stubs/` | authoring sandbox |
| Every real catalogue archive installed by the app's `ArchiveInstaller`, result compared file by file with the archive | `QVOICE_ARCHIVES=<folder> bash tools/sandbox/verify.sh` | authoring sandbox |
| Catalogue regenerated from the archives (checksums, sizes, required files, espeak data identical to the shared copy) | `python tools/catalog/make_catalog.py --archives <folder>` | anywhere with Python 3 and the archives |
| Voices spoken by the real sherpa-onnx engine and transcribed back (every speaker, every language) | (sandbox: Python sherpa-onnx + Whisper; results in docs/CATALOGUE.md) | authoring sandbox |
| `tools/get-binaries.ps1` on copies of the project: everything missing (both downloads), everything present (nothing downloaded), the voice from the old zip (no download), a truncated eSpeak NG zip (rebuilt), a damaged old zip and a wrong download checksum (both refused, nothing changed), extra files and leftover zips removed; the zip it builds read with java.util.zip as the app does (same 355 files, same content hash) | (sandbox: PowerShell 7.4) | authoring sandbox |
| The eSpeak NG zip sent with slice 1 = the voice archive's `espeak-ng-data` folder (rebuilt byte for byte with Python's zipfile, level 9) | (sandbox) | authoring sandbox |
| GitHub workflow: actionlint, and every action input checked against that action's own `action.yml` at the pinned major version | `actionlint .github/workflows/build.yml` | authoring sandbox |
| Unit tests, the Compose compiler, a debug APK, lint (a gate since slice 20), an unsigned release build (R8, resource shrinking, lint's release checks) — with the real Android tools, on every push | `.github/workflows/build.yml` | GitHub (first run, slice 20: all green) |
| The GPL components' source archives, checked against sherpa-onnx's pinned hashes, published as release assets | `.github/workflows/gpl-sources.yml` (on a `gpl-sources-*` tag) | GitHub |
| Timeline model: per-chunk generation times × the sandbox-to-M31 factor (4.1), with and without read-ahead. Reproduces the 0.2.2 log (Kitten int8: 0.74 s first audio, 5.0 s gap; measured 0.72 s, ~5 s) | (sandbox: Python sherpa-onnx) | authoring sandbox |
| Same-run sandbox speeds of every family, mapped onto the M31 measurements (the source of the catalogue's speed hints, see tools/catalog/make_catalog.py) | (sandbox: Python sherpa-onnx) | authoring sandbox |
| Fast speech: model-alone vs Sonic-alone vs model 1.5× + Sonic at 1.5-4×, word error rate by Whisper on ten Harvard sentences, two voices; Sonic pitch on real voices (F0 before/after) — the numbers behind D-041 | (sandbox: Python sherpa-onnx + the vendored Sonic.java) | authoring sandbox |
| Launcher icon under circle, squircle and rounded-square masks, themed glyph, Play icon, feature graphic — rendered and looked at | `python tools/brand/make_brand_assets.py` (+ preview renders in the sandbox) | authoring sandbox |
| Native libraries 16 KB page aligned (`readelf -l`: LOAD align 0x4000 for all three ABIs) | `readelf -lW <lib>.so` | authoring sandbox |
| Brand colours: every text/background pair ≥ 4.5:1 (WCAG AA) | (sandbox script) | authoring sandbox |

Guards are proven by mutation: each was broken on purpose in a copy and the
named test failed there.

| Guard | Test that catches it |
|---|---|
| Engine lock across load/use/release | `concurrentLoadNeverReleasesTheModelInUse` |
| Install lock vs start-up cleanup (eSpeak NG data copy and downloads) | `startupCleanupWaitsForARunningInstall` (both suites) |
| Byte budget; evict before load; release returns bytes | `byteBudgetMakesRoomBeforeLoading`, `kokoroLanguagesShareTheBudget`, `modelBiggerThanTheBudgetStillLoadsAlone` |
| SHA-256 check (same-size, one-bit change) | `rejectsAnyOtherFile` |
| Required-files check before the swap | `archiveWithoutAFileTheManifestNamesIsNotInstalled` |
| Never create links from an archive | `neverCreatesLinks` |
| Each TextChunker rule (10 mutations) | `TextChunkerTest` |
| sherpa-onnx callback has `Integer invoke(float[])` | `sherpaCallbackHasTheMethodTheNativeCodeLooksUp`; checker rule 10 |
| Generation runs ahead of playback (a synchronous ReadAhead) | `producerRunsAheadOfTheListener` (and 4 others) |
| Logged speed excludes waiting for playback | `synthTimeExcludesWaitingForTheListener` |
| ReadAhead returns only after its thread ended | `listenerStopEndsTheProducerBeforeRunReturns`, `listenerFailureStopsTheProducer` |
| Read-ahead queue is bounded | `queueIsBounded` |
| Opening clause cuts (threshold, both minimum lengths, space after Latin marks, spaced dashes, CJK commas, first break not last — 7 mutations) | `TextChunkerTest` |
| Engine class generates only inside ReadAhead | checker rule 11 |
| A changed thread count reloads the model | `changedLoadSettingsReloadOnNextUse` |
| The retired int8 copy is neither listed nor kept | `retiredBuiltInVoiceIsHiddenThenDeleted` (2 mutations) |
| Names saved with the old built-in id still resolve | `savedChoiceUnderTheOldBuiltInIdStillApplies` |
| Automatic default skips voices too slow for the phone | `automaticDefaultKeepsUpOnThisPhone`, `defaultsOnASlowPhoneKeepUp` |
| Keeping up outranks the region | `keepingUpOutranksRegion` |
| Speed samples: short utterances ignored, smoothing, measured beats predicted | `SpeedBookTest` (3 mutations) |
| "Slow on this phone" is asked first, before the licence | `questionsComeInOrder` |
| Catalogue speed hints validated | `rejectsBadEntries` |
| Every asset checked before sherpa-onnx is given it (it would end the process) | `missingAssetFailsBeforeTheNativeLoad` |
| No folder from inside the APK (eSpeak NG data, dictionary) | `aVoiceInTheApkCantUseItsOwnFolders` |
| The copy earlier versions made of the built-in voice is neither listed nor kept (2 mutations) | `oldCopyOfTheBuiltInVoiceIsHiddenThenDeleted` |
| First use of the built-in voice copies the eSpeak NG data | `storeListsBuiltInVoiceBeforeAndAfterSetup` |
| Built-in voices listed first | `builtInVoicesComeFirst` |
| Memory budget sizes the built-in voice from the APK | `footprintOfABuiltInVoiceComesFromTheApk` |
| The asset files match what the code asks for; nothing unused is packed | `BundledAssetsTest` |
| Rates past 1.5× go to Sonic, not the model | `fasterRatesStretchTheModelsFastestClearSpeed` |
| Sonic's pitch is applied | `pitchMovesThePitchNotTheLength` |
| Each chunk is flushed (no word tail held for the next chunk) | `everyChunkComesOutWhole` |
| Overshooting samples are clipped, not wrapped into clicks (the Sonic.java change) | `overshootIsClippedNotWrapped` |
| Reader: late callbacks of dropped utterances ignored | `callbacksOfDroppedUtterancesAreIgnored` |
| Reader: a call pauses but keeps the audio request, so reading resumes after it | `aCallPausesAndReadingResumesAfterIt` |
| Reader: with a screen reader on, nothing is queued ahead | `aScreenReaderGetsATurnAfterEveryPiece` |
| Reader: a paragraph no voice can read stops reading there, with the reason | `aParagraphNoVoiceCanReadStopsReadingThereAndSaysWhy` |
| Reader: Han-only text goes to Chinese, not Japanese | `aVoiceOfTheTextsScriptOtherwise` |
| Reader: only blank lines separate paragraphs | `blankLinesSeparateParagraphsAndHardWrapsAreJoined` |
| Stop lets go of the engine, ends the session, restarts a finished text (3 mutations) | `stopEndsTheSessionButKeepsTheTextAndThePlace`, `stopAfterTheEndStartsOverAndStopWithNothingIsHarmless` |
| No notification outside a listening session (loaded but not started) | `theNotificationExistsOnlyDuringAListeningSession` |
| A Play that fails at once starts no service | `aPlayThatFailsAtOnceStartsNoService` |
| No "next" on the last paragraph (Android 13+ controls) | `theNotificationSaysWhenReadingIsDoneOrWhyItStopped` |
| Sleep timer: stops between paragraphs when due, queues nothing ahead near the end (2 mutations) | `theSleepTimerStopsBetweenParagraphsOnceItsTimeIsUp` |
| Sleep timer: an expired timer doesn't stop the next Play | `aTimerThatRanOutWhilePausedDoesntStopTheNextStart` |
| Sleep timer: cleared when the text ends and on Stop (2 mutations) | `theTimerEndsWithTheTextOrWhenTurnedOff` |
| Sleep timer: the notification gets its time | `theNotificationExistsOnlyDuringAListeningSession` |
| Voice choice: the speaker picks voices afresh and the paragraph restarts, only while reading (3 mutations) | `anotherVoiceIsHeardAtOnceFromTheParagraphsStart` |
| Voice choice lists only the paragraph's language | `theReadersVoiceChoiceListsEveryVoiceOfTheLanguage` |
| Readable width: phones keep 16 dp margins; side system bars are cleared (2 mutations) | `ReadableWidthTest` |
| Shared files: UTF-16 by its mark, binary refused, the cap held over a slow stream (3 mutations) | `SharedTextTest` |
| Home's preview: whole words, "…" when cut | `aSnippetIsWholeWordsAndSaysWhenItIsCut` |
| Feedback details name QVoice (or "unknown") as the engine | `theEngineIsNamedPlainlyWhenItIsQVoiceOrUnknown` |
| Reader memory: written only when the text or the place changes | `changesThatTouchNeitherTextNorPlaceWriteNothing` |
| Reader memory: a finished text is remembered from its start | `aFinishedTextIsRememberedFromItsStart` |
| Reader memory: an emptied reader deletes it, once | `anEmptiedReaderForgetsTheTextOnce` |
| Reader memory: a damaged file is deleted, not kept | `aDamagedMemoryIsDeletedRatherThanKept` |
| Reader memory: a restored text isn't written back | `aRestoredTextIsNotWrittenBackUnchanged` |
| Reader memory: a file of another version or damaged is refused | `foreignOrDamagedTextIsRefused` |
| Reader memory: a burst of changes costs one write, the latest wins | `aBurstOfChangesCostsOneWriteAndTheLatestWins` |
| A remembered text never replaces one that arrived first | `aRestoreNeverReplacesATextThatCameFirst` |
| A clip marked sensitive isn't even fetched unless asked for | `aSensitiveClipIsRefusedWithoutBeingRead` |
| No deprecated `Locale` constructors (the owner's JDK 21 build warns; the sandbox can't see it) | checker rule 12 (caught the three real ones) |
| Debug shortcuts match the main ones except the package (2 planted drifts) | checker rule 13 |
| Battery warning: the restricted bucket or the user's restriction, nothing else (2 mutations) | `everyOrdinaryBucketIsFine`, `theRestrictedBucketOrTheUsersRestrictionWarns` |
| "Preparing": on after Play, a jump and between paragraphs; off at the first audio (2 mutations) | `theReaderSaysWhenTheVoiceIsStillPreparing` |
| "Preparing": off on pause and on an engine error (2 mutations) | `nothingIsPreparingOnceReadingStops` |

The static checker was also run against copies with planted errors (seven
in slice 1, a bogus member import in slice 2) and reported each.

The Compose files (`ui/HomeScreen.kt`, `ui/VoicesScreen.kt`,
`ui/AboutScreen.kt`, `ui/MainActivity.kt`, `ui/Theme.kt`) are compiled here
only against stubs of the Compose API (since slice 7; the stubs follow the
published signature files, see `tools/sandbox/compose-stubs/README.md`). The
Compose plugin's code generation, lint, R8 and resource merging run only in
your build, so a build error there is still possible; paste it back.

If `assembleRelease` ever fails with "Missing classes detected while running
R8", copy the rules from `app/build/outputs/mapping/release/missing_rules.txt`
into `app/proguard-rules.pro` (the known ones for Commons Compress are there
already) and tell me which classes it named.

## On-device test — slice 1

Takes about 5 minutes. Needs one phone (Android 8+).

0. **One-time setup.** Put the three binary files in place (README.md,
   "Binary files"): download the AAR into `app\libs\`, copy the two voice
   zips (unextracted) into `app\src\main\assets\bundled\`.
1. **Build and install.** From the project folder in PowerShell:
   `& "C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1\bin\gradle.bat" installDebug`
   (or Run in Android Studio).
2. **First launch.** Open QVoice. Within a few seconds the top card changes
   from "Starting the voice engine…" to "QVoice is installed". The list shows
   8 voices (Rosie first, marked Default).
3. **Try it.** Tap **Speak**. You should hear Rosie read the sample. The line
   under the buttons shows "Speech started after N ms" — **note N** (first
   run includes copying the voice; tap Speak again and note the second N).
4. **Voices.** Tap ▶ on two or three other voices; each plays its sample in a
   different voice. Tap **Make default** on one; the Default label moves.
5. **Stop.** Paste a long paragraph, tap Speak, then Stop within a second:
   speech stops immediately.
6. **System settings.** Tap **Open text-to-speech settings**, choose QVoice
   as the preferred engine, then tap **Play** there (Settings' own sample).
   Come back: the card now says "QVoice is your phone's voice".
7. **Speed.** In Settings set Speech rate high, tap Play: faster speech.

Please report: the two "started after" numbers from step 3, the phone model,
and anything that didn't match the description.

Optional, if Logcat is handy: filter on `QVoice` — every utterance logs one
line with the calling app, voice, first-audio time, total time, audio length
and model load time.

## On-device test — slice 2 (voice downloads)

About 20 minutes, most of it waiting for downloads. Use Wi-Fi; about 1 GB
free space is needed for everything below.

1. **Build and install** as in slice 1 (same command). Gradle downloads one
   new library (Commons Compress) the first time, so the PC needs internet.
   The slice-1 binary files stay where they are; nothing new to copy.
   The About screen should show version **0.2.2-slice02**.
   *Android Studio instead:* File → Open the project folder. The included
   `gradle/wrapper/gradle-wrapper.properties` makes Studio download Gradle
   9.4.1 once (about 140 MB). To use your existing copy instead: Settings →
   Build, Execution, Deployment → Build Tools → Gradle → Distribution:
   *Local installation* → `C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1`.
   *If you tapped Make default in the slice-1 test, that choice stays in
   force and the "Default" marks below won't move; that's correct.*
2. **Voice library.** Home now ends with a **More voices** card. Tap
   **Get more voices**. "On this phone" shows *Kitten Nano — Built in*;
   "Available to download" shows seven packs with sizes (Kokoro 350 MB,
   Supertonic 3 129 MB, five Piper voices 67 MB).
3. **A small one first: Kristin.** Tap **Download**. The card shows
   "x MB of 67 MB" and the notification shade shows progress. Then
   "Checking the download…", "Installing… N%", "Installed". **Note how long
   the install part took.**
4. **Use it.** Back on Home, Kristin is in the list and marked **Default**
   (a download beats the built-in voice). Tap ▶ on Kristin, then **Speak**
   twice and **note the second "started after N ms"** (Piper should be much
   faster than Kitten).
5. **Supertonic 3.** Download: a licence dialog appears first; read it and
   tap **Accept and download**. After install, Home shows **language chips**.
   Tap **Hindi (India)**: the sample text switches to Hindi; tap Speak and
   note N. Try one more language chip.
6. **Kokoro.** Download (350 MB; install takes a minute or two — **note the
   time**). **English (United States)** now defaults to *Heart*. Speak a
   long paragraph: does it keep up without gaps? Note N. Try *Alpha* in the
   Hindi chip and compare it with Supertonic's Hindi.
7. **Leave during an install.** Start a download of John, press Home while
   it installs, come back a minute later: it should be installed.
8. **Cancel.** Start Norman, tap **Cancel**: the notification disappears and
   the card shows Download again.
9. **Offline.** Start Cori, switch on airplane mode: "Waiting for a
   connection…". Switch it off: the download continues.
10. **Delete.** Delete Kristin (confirm). It disappears from Home; English
    (US) falls back to Heart.
11. **Android settings.** Settings → Text-to-speech → QVoice → Language:
    choose Hindi, tap Play. Also tap "Install voice data" (if your phone
    shows it): QVoice's voice library opens.

Please report: phone model; the install times (steps 3 and 6); the
"started after" numbers for Kristin, Supertonic Hindi and Kokoro; whether
Kokoro kept up on a long paragraph; anything that didn't match.

Logcat (optional): filter on `QVoice` — each install logs
"Install <voice>: INSTALLED/FAILED", each utterance its timings.

**If the app crashes**, with the phone still connected, run in PowerShell:

```powershell
& "C:\Users\Public\sdk\platform-tools\adb.exe" logcat -d -b crash
```

and paste the output: it is the crash's stack trace, nothing else.

Harmless build messages: "Unable to strip the following libraries" (the
native libraries are already stripped; stripping needs the NDK, which isn't
installed) and "Consider enabling configuration cache".

## On-device test — slice 3 (smooth speech)

About 15 minutes. Same phone as before; Kristin should still be installed
(if not, download it once from the Voices screen).

1. **Update the source and fetch the new built-in voice** (PowerShell;
   adjust the folder if yours differs):

   ```powershell
   Expand-Archive -Force -Path "$env:USERPROFILE\Downloads\QVoice-slice03-src.zip" -DestinationPath "C:\Users\Public\QVoice-slice02-fix1-src"
   cd "C:\Users\Public\QVoice-slice02-fix1-src\QVoice"
   powershell -ExecutionPolicy Bypass -File tools\get-binaries.ps1
   ```

   (Since slice 11 the script is `get-binaries.ps1`, which also checks the
   other binary files.) The script downloads 64 MB from GitHub, checks it, and ends with
   "Done: ...kitten-nano-en-v0_8-fp32.zip (about 52 MB)"; it also deletes the old
   int8 zip. *If it says tar isn't found (Windows older than 10 1803):
   extract `kitten-nano-en-v0_8-fp32.tar.bz2` with 7-Zip and zip the four
   files LICENSE, model.fp32.onnx, tokens.txt and voices.bin (no folder) into
   `app\src\main\assets\bundled\kitten-nano-en-v0_8-fp32.zip`.*
2. **Build, install, start with a clean log:**

   ```powershell
   & "C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1\bin\gradle.bat" test
   & "C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1\bin\gradle.bat" installDebug
   $adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
   & $adb logcat -b all -c
   & $adb shell am start -n com.riniso.qvoice.debug/com.riniso.qvoice.ui.MainActivity
   ```

   About shows **0.3.0-slice03**. The Voices screen shows one Kitten Nano
   (Built in) — the old copy is gone.
3. **Built-in voice.** Select Rosie, tap **Speak** three times (wait for
   each to finish). Expected: speech starts within about half a second, at
   most one short pause after "Hello!", no long silence. Under the buttons:
   "This voice runs N× faster than real time on this phone (4 CPU threads)".
   **Note N** from the third run.
4. **Kristin.** Select Kristin, **Speak** three times; note N. No pauses.
5. **A long text.** Tap the text box, clear it, then type this in from the
   PC (the phone stays connected):

   ```powershell
   $p = "Good morning. Your next appointment is at half past ten, with Doctor Rao. Traffic on the way is light, so leaving at ten should be enough. Remember to bring the reports from last week, and the letter from the insurance company. After that, you have lunch with Priya at one o clock, at the new cafe near the station."
   & $adb shell input text ($p -replace ' ', '%s')
   ```

   Speak it with Kristin, then with Rosie. Listen for pauses between
   sentences.
6. **Stop and switch.** Speak the long text with Rosie; after two
   sentences tap **Stop** (sound stops at once). Speak again, and after two
   sentences tap ▶ on Bruno: Bruno should start within about 2 seconds.
7. **CPU threads.** Tap **CPU threads: Automatic (4)**, choose **2**. Select
   Rosie, Speak twice, note N from the second run (the first one reloads the
   voice). Repeat with **1** and **3**, then set it back to **Automatic**.
8. **Paste the log:**

   ```powershell
   & $adb logcat -d -s QVoice:I QVoice.Tts:I
   & $adb logcat -d -b crash
   ```

Please report: the N values (steps 3, 4 and 7), whether you heard pauses in
steps 3-5, and whether Bruno started quickly in step 6. The log line per
utterance now reads "model ready in …, first audio …, generated X ms of audio
in Y ms (Z× real time, N threads)", and "stopped: ended … ms after the stop
request" after a Stop.

## On-device test — slices 4 to 7 (1.0.0: logo, speed on this phone, new Home, storage diet)

About 5 minutes, same phone. Nothing to download.

1. **Update, build, install.** Two one-time extras this time. The built-in
   voice moves from a zip to plain files: the script does it from the zip you
   already have (no download). And the versionCode went from 5 back to 1, so
   Android refuses a plain install over the old build once ("version
   downgrade"); `-d` allows it for this test build and keeps your voices:

   ```powershell
   Expand-Archive -Force -Path "$env:USERPROFILE\Downloads\QVoice-slice07-src.zip" -DestinationPath "C:\Users\Public\QVoice-slice02-fix1-src"
   cd "C:\Users\Public\QVoice-slice02-fix1-src\QVoice"
   Remove-Item app\src\main\res\drawable\ic_launcher_foreground.xml -ErrorAction SilentlyContinue
   powershell -ExecutionPolicy Bypass -File tools\get-binaries.ps1
   & "C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1\bin\gradle.bat" test assembleDebug
   $adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
   & $adb install -r -d app\build\outputs\apk\debug\app-debug.apk
   & $adb logcat -b all -c
   ```

   The `Remove-Item` line deletes the 0.3.0 launcher-icon file that the new
   icon replaced (extracting a zip never deletes files; this one is unused).
   The script ends with "Removed the old kitten-nano-en-v0_8-fp32.zip." and
   "Done: …\kitten-nano-en (57.3 MB in 4 files)". If Android still refuses
   the install, `& $adb uninstall com.riniso.qvoice.debug` (before slice 20:
   `com.riniso.qvoice`) and install again
   (downloaded voices then need downloading again). Later builds install with
   `installDebug` as usual.
2. **Look:** the home-screen icon is the new logo (if the launcher still
   shows the old one, long-press → remove from home screen and add it again
   from the app drawer: launchers cache icons). Open QVoice: the logo sits
   next to the name, colours are indigo/navy. If QVoice isn't your preferred
   engine, Home starts with the dark "Make QVoice your phone's voice" card;
   its button opens the TTS settings, which now list **QVoice
   Text-to-Speech**. Choose it and come back: the card turns into a small
   "QVoice is your phone's voice" confirmation. Voices show an initial in a
   coloured circle; tap one and "Make default" appears on it. About shows
   **1.0.0-slice07**.
3. **Speed on this phone:** open **Voices**. Within a few seconds each voice
   shows a speed line — e.g. Kristin and LJ "Expected to be fast", Kokoro
   "Expected to be slow… about 0.3×". Tap **Download** on Supertonic 3: a
   "Slow on this phone" question appears first — tap **Cancel**.
4. **Automatic default:** back on Home, English (United States) should no
   longer default to a Kokoro voice (unless you chose one with Make default
   earlier). Tap Speak once with Rosie (built in), then once with Kristin: in
   Voices, Kristin now says "Fast on this phone · N× real time" (measured).
5. **Log:**

   ```powershell
   & $adb logcat -d -s QVoice:I QVoice.Tts:I
   & $adb logcat -d -b crash
   ```

   New lines to look for: "Deleted old copies of built-in voices: 57 MB
   freed" (this first start only) and "Built-in voice kitten-nano-en: read
   from the APK (54 MB model, stored uncompressed)". A warning saying
   "stored compressed" instead would mean the build ignored `noCompress`.

Please report anything that didn't match, and the log.

## On-device test — slice 8 (fast speech and pitch)

About 2 minutes, after the build and install steps of the slice being
installed (for slice 8 on its own: the same steps as above with
`QVoice-slice08-src.zip`, and `gradle.bat test installDebug` instead of the
`assembleDebug` + `adb install -r -d` pair if slice 7 is already on the
phone).

1. Home → Try it: Speed **2.5x**, Speak. Fast but clear, same voice pitch.
   Then Speed **1x**, Pitch **1.3x**, Speak: higher voice, normal speed.
2. Optional, if you use TalkBack: set a fast speech rate in TalkBack and
   swipe through a screen.
3. Log (the Speak lines now end with "speed 2.50x (model 1.50x, stretch
   1.67x), pitch 1.00x"):

   ```powershell
   & $adb logcat -d -s QVoice:I QVoice.Tts:I
   & $adb logcat -d -b crash
   ```

## On-device test — slice 9 (read aloud)

About 3 minutes after installing (build and install as for the slice before;
`installDebug` is enough once slice 7 or later is on the phone).

1. **Selection:** in Chrome, long-press a word in an article, drag to select
   a few sentences, then in the menu choose **Read aloud** (on some phones
   it's under ⋮). The reader opens and reads; the highlighted paragraph
   follows. Tap **Pause**, then play: it resumes at that paragraph. Try ⏭,
   ⏮, the speed button (bottom left) and tapping another paragraph.
2. **Share:** share a note or a message as text (for example from Keep or
   WhatsApp: Share → QVoice / Read aloud): the reader reads it. Sharing a web
   page from Chrome's menu shows a hint instead of reading the address.
3. **Home:** the "Read aloud" card → Open the reader → ✎ (top right) → paste
   something → Read aloud.
4. Optional: play music in another app while it reads — the reader pauses.
5. Log:

   ```powershell
   & $adb logcat -d -s QVoice:I QVoice.Tts:I
   & $adb logcat -d -b crash
   ```

   Each paragraph shows as a "Spoke … for com.riniso.qvoice.debug" line
   (`com.riniso.qvoice` in the version from Play).

## On-device test — slice 10 (listening with the screen off)

About 4 minutes after installing. Use a text of several paragraphs (select
most of an article in Chrome → Read aloud).

1. **Screen off:** while it reads, press the power button. It keeps
   reading. Press power again: the lock screen shows a QVoice player ("Read
   aloud", "Paragraph 3 of 12"); pause and play from there.
2. **Another app:** go back to Chrome (back button). It keeps reading. Pull
   down the notification shade: the player's ⏮ ⏯ ⏭ work, and expanding it
   shows ✕ (Stop), which ends reading and removes the player.
3. **Resume later:** play again from the reader, then pause from the
   notification and turn the screen off; after a minute or two press Play on
   the lock screen: it continues at the same paragraph. (After 10 minutes
   paused, the player goes away by itself; no need to wait for that.)
4. Optional, with wired or Bluetooth headphones: the headset's play/pause
   button pauses and resumes.
5. **Back while paused:** open the reader from the notification, pause,
   press back: the notification goes away.
6. Log:

   ```powershell
   & $adb logcat -d -s QVoice:I QVoice.Tts:I
   & $adb logcat -d -b crash
   ```

   Nothing new to look for when all is well; a "Read aloud: couldn't …"
   warning would mean Android refused the notification.

## On-device test — slice 12 (sleep timer)

One minute, no waiting for the timer. In the reader, while it reads: tap the
moon (top right) → **15 minutes**. The moon now shows the time reading will
stop, and the notification says "… · stops at" the same time. Open the moon
again → **Turn off**: both go back to normal. (To see it stop, set it at
bedtime; it finishes the paragraph, then pauses.)

## On-device test — slice 13 (the voice in the reader)

One minute. While the reader reads English: tap the speaking head (top
right). The list shows the English voices with the current one selected;
pick another: the paragraph starts over in that voice. Home's voice list now
shows it as Default too.

## On-device test — slice 14 (headings)

Optional, for TalkBack users only: with TalkBack on, open Home, choose
"Headings" in TalkBack's reading controls (swipe up or down with one finger
to change them), then swipe down: TalkBack jumps from section to section.

## On-device test — slice 15 (shortcuts, landscape)

Two minutes.

1. On the home screen, long-press the QVoice icon: **Read aloud** and
   **Voices** appear. Tap each: the reader (paste field, or what it is
   reading) and the voice library open.
2. Turn the phone to landscape on Home, the reader and the voice library:
   the content is centred with margins at both sides, and nothing sits under
   the navigation bar. Back to portrait: everything as before.

## On-device test — slice 16 (text files)

Two minutes. Make a small text file on the PC and put it on the phone:

```powershell
"A shared file.`r`n`r`nIts second paragraph." | Set-Content -Encoding UTF8 "$env:TEMP\qvoice-test.txt"
$adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
& $adb push "$env:TEMP\qvoice-test.txt" /sdcard/Download/qvoice-test.txt
```

1. On the phone, open **Files** → **Downloads** → long-press
   `qvoice-test.txt` → **Share** → **QVoice** (or "Read aloud"): the reader
   reads "A shared file." then its second paragraph.
2. Press back, open QVoice: Home's Read aloud card shows "“A shared file.
   Its…”"-style opening words, where the reader is, and **Back to the
   reader**, which returns to it.

## On-device test — slice 17 (About: feedback, rating, privacy policy)

Two minutes. Files deleted by this chunk (remove once, from the project
folder): `Remove-Item -ErrorAction SilentlyContinue store\privacy-policy.html`

1. Home → ⓘ (About). Under the logo: **Send feedback** and **Rate QVoice**.
2. **Send feedback**: your mail app opens a new email to
   support@zinijo.com, subject "QVoice feedback (1.0.1-slice22)", the
   cursor above a "Details for QVoice support" block with the app and
   Android versions, the phone and the preferred engine. Discard it (or
   send yourself a test).
3. **Rate QVoice**: the Play Store opens QVoice's page ("not found" until
   the app is on Play: expected).
4. **Read the privacy policy** (under Privacy): the browser opens
   zinijo.com/qvoice/privacy.html ("not found" until you upload
   `store/privacy.html`).
5. **Source code** shows https://github.com/cyber-cyper/qvoice; tapping it
   opens the repository.

## On-device test — slice 18 (continue listening)

Three minutes.

1. Open the reader (Home → Open the reader), paste a text of four or more
   paragraphs (blank lines between them), play, and let it reach the third
   paragraph. Pause.
2. Kill QVoice completely (this also ends the notification):

   ```powershell
   $adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
   & $adb shell am force-stop com.riniso.qvoice.debug
   ```

3. Open QVoice: Home's Read aloud card shows the text's opening words,
   "Paragraph 3 of …" and **Continue listening**. Tap it: the reader opens
   at the third paragraph (highlighted, in view) and reads on from there.
4. Restart the phone and open QVoice: the same.
5. In the reader, tap the pencil (**Read something else**): the paste field
   offers **Back to the previous text**; tap it: the text is back, at its
   place.
6. While it reads, share a web page from Chrome (⋮ → Share → QVoice): the
   reading goes on, with "QVoice reads text, not web pages…" above the
   text; **Dismiss** removes it.

## On-device test — slice 19 (read copied text)

Two minutes.

1. Copy a sentence in any app (Chrome, Messages). Long-press the QVoice
   icon → **Read copied text**: the reader opens and reads it; Android
   shows "QVoice pasted from your clipboard".
2. Copy a password from your password manager (Google Password Manager,
   Samsung Pass…), then **Read copied text** again: the reader says the
   copied text is marked as private and doesn't read it; **Read it anyway**
   reads it.
3. Copy a photo (long-press it in Gallery → Copy, if your phone offers it),
   then **Read copied text**: "There's no text on the clipboard."
4. Drag **Read copied text** from the long-press menu to the home screen: a
   "Clipboard" icon that does the same.

## On-device test — slice 20 (QVoice debug next to the Play version)

One time, before installing this build: remove the old test build (it has
the Play version's package and would block installing QVoice from Play).
Its downloaded voices go with it.

```powershell
$adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
& $adb uninstall com.riniso.qvoice
```

Then install as usual (`gradle test installDebug`).

1. The launcher shows **QVoice debug**; About shows version
   1.0.1-slice22 (or later).
2. Android's text-to-speech settings list **QVoice Text-to-Speech
   (debug)**; choose it as the preferred engine (or keep Google's until
   the Play version is installed).
3. Long-press QVoice debug's icon: Read aloud, Read copied text, Voices;
   each opens QVoice debug's screen (not another app).

## On-device test — slice 21 (Help, battery warning)

Three minutes.

1. Home: a **?** next to ⓘ in the top bar. Tap it: Help, with nine
   questions. Tap one: its answer opens (the one before closes); tap again
   to close. "How do I make apps speak with QVoice?" → **Open
   text-to-speech settings** opens them; "Reading stops when the screen
   turns off" → **Open QVoice's app settings** opens App info.
2. Back from Help returns to Home. About → **Help** opens it too, and back
   returns to About.
3. Battery warning: Settings → Apps → QVoice debug → Battery →
   **Restricted**, then back to QVoice: a "Battery saving can stop QVoice"
   card under the setup card; its button opens the same App info. Set it
   back to **Optimized** (or Unrestricted) and return: the card is gone.
4. Without QVoice as the preferred engine, the setup card shows **Need
   help?** under its button; it opens Help.

## On-device test — slice 22 ("Preparing the voice…")

Two minutes; easiest with a large voice (Kokoro or Supertonic) if one is
installed, otherwise right after opening QVoice (the first sentence loads
the voice).

1. Reader: paste a text and play. Until the first word: a ring around the
   play button and "Preparing the voice…" at the right of the controls;
   both go when it speaks. Between paragraphs with the built-in voice:
   nothing flickers.
2. Change the speed while reading: the ring shows briefly if the voice
   needs a moment.
3. Home → Try it with the same large voice: "Preparing the voice…" under
   Speak until it speaks, then the timing line as before.

## Not testable yet

Sister-app integration (the helper, deferred by the owner until QVoice is
final); Tamil and Malayalam voices (left to Google's engine for now, the
owner's decision; see BACKLOG.md).
