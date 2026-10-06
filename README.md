# QVoice

Natural-sounding, offline text-to-speech for Android, published by Riniso.

QVoice is a **system text-to-speech engine**: once installed, any app that
reads aloud can use it — screen readers, e-book readers, maps, and the Riniso
apps (QTune, QBrain, QTunes, LifeCue). Everything is generated on the phone;
no text ever leaves the device.

It combines the best ideas of four open-source projects (HayaiTTS, NekoSpeak,
Marmalade TTS, AudioLab) into one clean app built on a single speech runtime,
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). See
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for what was taken from each and
why.

**Status:** version 1.0.0 (versionCode 1), slice 19 of the
[backlog](docs/BACKLOG.md): the system engine with a built-in English voice
(8 speakers, read straight from the APK), generation that runs ahead of
playback, speech rates up to 6× with pitch, a voice library of seven
validated, downloadable voice packs — Kokoro (48 voices in 8 languages),
Supertonic 3 (10 voices in 31 languages, including Hindi) and five fast Piper
English voices — that knows how fast each runs on the phone, and "Read
aloud" for text selected, shared (text or .txt files) or copied in any app,
which keeps reading with the screen off (lock screen and headset controls,
sleep timer) and picks up where it stopped, even after a restart. How
each was chosen and checked: [docs/CATALOGUE.md](docs/CATALOGUE.md). The Play
Store kit (icon, feature graphic, listing text, privacy policy page, release
guide) is in [store/](store/LISTING.md).

## Binary files (one-time setup)

About 110 MB of files come from their publishers and rarely change, so they
are kept out of the source (the slice zips and git alike). One script puts
them all in place and checks each one's SHA-256; from the project folder in
PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File tools\get-binaries.ps1
```

| File | Where | From |
|---|---|---|
| `sherpa-onnx-1.13.8.aar` (48 MB) | `app/libs/` | sherpa-onnx's v1.13.8 release (`633c2432…bd96`) |
| The built-in voice: `LICENSE`, `model.fp32.onnx`, `tokens.txt`, `voices.bin` (57 MB) | `app/src/main/assets/bundled/kitten-nano-en/` | sherpa-onnx's `kitten-nano-en-v0_8-fp32.tar.bz2` (`16092117…883e`), each file checked |
| `espeak-ng-data.zip` (9 MB) | `app/src/main/assets/bundled/` | the `espeak-ng-data` folder of that same archive, zipped; its content checked |

Files already in place and correct are left alone (a run on a folder that
has them all takes a few seconds), so running it again is harmless.
It downloads only what is missing or wrong: the voice from the zip earlier
versions used if that is still there, else the archive (64 MB, downloaded at
most once), and it deletes the old voice zips, which the build refuses
because they would be packed into the APK unused. Nothing is replaced unless
its checksum matches. GitHub runs the same script before every build.

The app reads the voice straight from the APK (the model files are stored
uncompressed there, see `noCompress` in `app/build.gradle.kts`); only
`espeak-ng-data.zip` is unpacked on the phone, on first run. The build stops
with a clear message if a file is missing, truncated or obsolete.

## Build

Requirements: Android Studio Panda 4 or newer (AGP 9.2 support), JDK 17+,
Android SDK 36, and Gradle **9.4.1**. Only the wrapper's properties file is
included (`gradle/wrapper/gradle-wrapper.properties`, pinned to 9.4.1): Android
Studio reads it — without it Studio creates one with an older Gradle that
AGP 9.2 rejects. Command-line builds use the standalone Gradle below.

PowerShell, from the project folder (`docs/TESTING.md` has the full routine
for a new zip):

```powershell
cd C:\Users\Public\QVoice-slice02-fix1-src\QVoice
"sdk.dir=C:/Users/Public/sdk" | Set-Content local.properties
& "C:\Users\Public\gradle-9.4.1-all\gradle-9.4.1\bin\gradle.bat" test installDebug
```

Release bundles for Play: `store/RELEASE.md`.

Release builds refuse to run until `qvoice.sourceUrl` in `gradle.properties`
names the public repository holding this source — a GPL-3.0 requirement (see
Licence below).

Before packaging a change, run the static checks (no Gradle needed):

```powershell
python tools/check_project.py
```

## Publishing the source (GitHub)

The GPL-3.0 requires the source of every released build to be public, and
release builds refuse to run until `qvoice.sourceUrl` names it. QVoice's
repository is **https://github.com/cyber-cyper/qvoice** (already set in
`gradle.properties`). Every push is built and tested there with the real
Android tools (`.github/workflows/build.yml`), and each run's page offers
the debug APK to download. How the source gets there: `store/RELEASE.md`,
step 1.

With Git for Windows instead, from a fresh copy of the latest zip
(`.gitignore` keeps the binary files, `local.properties`, build output and
any keystore out):

```powershell
git init -b main
git add .
git commit -m "QVoice 1.0.0"
git remote add origin https://github.com/cyber-cyper/qvoice.git
git push -u origin main
```

The **Actions** tab shows the build; the first one takes about 10 minutes,
later ones less (downloads are cached).

Still to add for the GPL, as release assets of the first release: the
source archives of eSpeak NG, piper-phonemize and sherpa-onnx v1.13.8 (see
NOTICE.md).

## Using QVoice from another app

Apps don't need QVoice to be the phone's default engine; they can ask for it
by package name:

```kotlin
tts = TextToSpeech(context, { status -> /* ready */ }, "com.riniso.qvoice")
```

On Android 11+ the calling app must declare that it looks for TTS engines, or
it can't see QVoice at all:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

Voice names are stable (`<voiceId>#<speaker>`, e.g. `kitten-nano-en#rosie`;
a voice offered in several languages gets `<voiceId>#<speaker>@<language>`,
e.g. `sherpa-onnx-supertonic-3-tts-int8-2026-05-11#f1@hi-IN`) and can be
stored and passed to `setVoice()`. (Names from 0.1-0.2 builds,
`kitten-nano-en-v0_8-int8#rosie`, still work.) A ready-made helper for the
Riniso apps is planned (see docs/BACKLOG.md).

## Project layout

```
app/src/main/java/com/riniso/qvoice/
  QVoiceApp.kt, AppGraph.kt   process entry point and object graph
  voices/                     manifests, voice list, built-in voices, installers (downloaded tar.bz2)
  catalog/                    the downloadable-voice catalogue and its parser
  download/                   DownloadManager, install job, voice library state
  engine/                     sherpa-onnx configuration, model cache, read-ahead, PCM streaming, text chunking, CPU layout, fast speech (Sonic)
  reader/                     read aloud: paragraphs, per-paragraph language, the reading state machine, audio focus, the background media service
  service/                    the TextToSpeechService and its helper activities
  settings/                   user choices read on the synthesis hot path
  ui/                         Compose UI (home, voice library, about, the reader)
app/src/main/assets/catalog.json   the voice catalogue (generated, see below)
app/src/main/assets/bundled/  the built-in voice (plain files, read from the APK) and espeak-ng data (zip)
app/src/debug/res/            debug builds only: "QVoice debug" names and its launcher shortcuts (com.riniso.qvoice.debug)
app/libs/                     sherpa-onnx 1.13.8 AAR (vendored)
docs/                         architecture, catalogue, testing, backlog, the facts behind the privacy policy
tools/check_project.py        static checks
tools/catalog/make_catalog.py generates catalog.json from the real voice archives
tools/get-binaries.ps1        puts every binary file in place (checksums checked)
tools/brand/                  the logo master and the script that makes every icon and store graphic
store/                        Play Store listing text, icon, feature graphic, privacy policy page, release guide
.github/workflows/build.yml   the GitHub build: tests, lint, a debug APK and a release check per push
```

## Licence

QVoice is free software under the **GNU General Public License v3.0 or
later** ([LICENSE](LICENSE)). It has to be: the sherpa-onnx native library
compiles in eSpeak NG (GPL-3.0-or-later), which every multi-language voice
needs for pronunciation. Consequences, all deliberate:

- the complete source is published alongside every release;
- no ads, billing or other proprietary SDKs inside QVoice;
- apps that merely *use* QVoice through Android's TextToSpeech API are
  separate programs and are not affected.

Third-party components and their licences: [NOTICE.md](NOTICE.md).
