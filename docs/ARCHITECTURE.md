# QVoice architecture and decisions

Read this before changing anything structural. Each decision records why, so
a later change doesn't undo it by accident.

## One-paragraph overview

QVoice is a single-module Android app (Kotlin, Compose). Its core is
`QVoiceTtsService`, an `android.speech.tts.TextToSpeechService`. Voices are
folders under `files/voices/<id>/`, each described by a `qvoice-voice.json`
manifest. `VoiceStore` keeps an in-memory snapshot of them; `VoiceSelector`
decides which voice speaks a request; `EngineHost` loads sherpa-onnx models
on demand and guarantees none is released mid-synthesis; `ReadAhead` runs the
model on its own thread, ahead of playback, while `PcmStreamer` feeds the
audio to the framework at playback speed. The UI talks to the engine through
the public TextToSpeech API, the same path any other app uses.

Voices beyond the built-in one come from a curated catalogue
(`assets/catalog.json`). `VoiceLibrary` downloads them with Android's
DownloadManager, an `InstallJobService` job verifies and unpacks each archive
(`ArchiveInstaller`), and the new folder appears in `VoiceStore` like any
other voice.

Read aloud (D-042, D-043): `ReaderActivity` (text selections, Share, Home,
the clipboard shortcut) shows and drives `ReadAloud`, an app-wide controller
that speaks the text paragraph by paragraph through that same public API.
While a listening session lasts, `ReadAloudService`, a media-playback
foreground service, mirrors it as the media notification and media session,
so reading goes on with the screen off and the lock screen and headset
buttons control it. `ReaderMemory` keeps the last text and place on the
phone so reading continues after a restart (D-050).

```
client app ──TextToSpeech IPC──▶ QVoiceTtsService
                                   │  VoiceSelector (which voice?)  ◀── QVoiceSettings (user defaults)
                                   │  VoiceStore.prepare (ready?) ◀── BundledVoices (first-run eSpeak NG copy)
                                   │  EngineHost.withModel (load/cache/lock)
                                   │     └── SherpaModelLoader → SherpaConfigFactory → sherpa-onnx OfflineTts
                                   │           (downloads from files; the built-in voice from the APK, D-040)
                                   │  ReadAhead: model.generate (TextChunker chunks) on its own thread ─┐
                                   └─ PcmStreamer → SynthesisCallback.audioAvailable ◀── queue ─────────┘

Voices screen ─▶ VoiceLibrary ─▶ DownloadManager ─(DOWNLOAD_COMPLETE / refresh)─▶ InstallJobService
                    ▲                                                                │
                    └── items (StateFlow) ◀── VoiceStore.refresh ◀── ArchiveInstaller ┘
                                                (size + SHA-256, tar.bz2, manifest last, swap)

ReaderActivity ─▶ ReadAloud (AppGraph, main thread) ──TextToSpeech──▶ QVoiceTtsService
                    │ state ▲ buttons      └──▶ ReaderMemory ─▶ FileMemoryStore (noBackupFilesDir)
                    ▼       │
             ReadAloudService: notification, lock screen, MediaSession (headset), wake lock
```

## Decisions

**D-001 GPL-3.0, open source, no ads or billing in QVoice.** Every
multi-language voice (Piper/VITS, Kokoro, Kitten, Matcha) needs eSpeak NG,
which is GPL-3.0 and is compiled into sherpa-onnx's Android library (verified
in the binary). A distributed QVoice is therefore a GPL work: source published
with every release, and no proprietary SDKs (AdMob, Play Billing, Crashlytics)
linked in. Owner approved, 28 Sep 2026. The alternative (no espeak) would have
meant English plus a few Supertonic languages, no Indian languages, and far
more custom engine code.

**D-002 A separate engine app, not a library inside each sister app.** Sister
apps reach QVoice through Android's TextToSpeech IPC, which keeps them
closed-source and monetised (separate programs), keeps their APKs small
(~30 MB of native code per ABI stays out), and lets one install serve every
app. Sister apps bind by package name, so users don't have to change the
system default.

**D-003 sherpa-onnx as the single runtime.** It runs every model family the
four reference apps use (VITS/Piper, Kokoro, Kitten, Matcha, Supertonic,
Pocket, ZipVoice) behind one API. NekoSpeak and Marmalade hand-wrote
per-family ONNX engines (1,600–2,300 lines each for Pocket alone); that is
more code than this project can verify without a compiler in the loop.
Pinned to 1.13.8 for crash fixes after 1.13.2 (espeak voice rejection no
longer aborts the process; native init failures now throw instead of leaving
a null pointer).

**D-004 The filesystem is the source of truth.** A voice is installed exactly
when its folder holds a parseable manifest, written last. No database to
drift out of sync (both HayaiTTS and Marmalade shipped fixes for that). Installs
stage into `files/staging/` and rename into place; an interrupted install is
simply ignored and cleaned up.

**D-005 One install lock (`QVoicePaths.installLock`).** Every installer and the
start-up staging cleanup hold it. Without it, the cleanup could delete the
eSpeak NG data copy the first synthesis request is writing (race proven by
test `startupCleanupWaitsForARunningInstall`).

**D-006 A model is never released while it generates.** `EngineHost` holds one
lock across load, use and release. Warm-ups and memory trims only *try* the
lock, so they never delay a sentence and never free a busy model. Proven by
test `concurrentLoadNeverReleasesTheModelInUse` (fails if the block runs
outside the lock).

**D-007 Streaming, one sentence per chunk.** `maxNumSentences = 1`: the first
sentence plays while the rest is generated. HayaiTTS buffered whole
utterances, which read-aloud apps and screen readers experience as lag.
(Since slice 3 generation also runs ahead of playback: D-031.)

**D-008 No `android:permission` on the TTS service.** A TTS engine is public
API. On Android 11 and older the client binds the engine itself; HayaiTTS's
`BIND_TEXT_SERVICE` (system-only) makes `TextToSpeech()` fail for every normal
app there.

**D-009 Negotiation answers come from memory.** Binder calls
(`onIsLanguageAvailable`, `onGetVoices`, ...) read snapshots; they never load
models or read voice files. Settings are SharedPreferences (synchronous,
in-memory) rather than DataStore, so the hot path never uses `runBlocking`.

**D-010 `onGetDefaultVoiceNameFor` must name a listed voice.**
`TextToSpeech.setLanguage()` looks the name up in `onGetVoices()` and reports
"not supported" otherwise. Both come from `VoiceSelector` over the same list.

**D-011 Refuse rather than speak gibberish.** With no voice for the request's
language, QVoice falls back to English only if the text is in Latin script
(`ScriptDetector`); Tamil text with no Tamil voice gets
`ERROR_NOT_INSTALLED_YET`, so the client can fall back to another engine.

**D-012 Framework voice names are public API.** `<voiceId>#<speakerKey>`, or
`<voiceId>` for single-speaker models. Apps may store them, so ids and keys
are never renamed; ids are restricted to `[A-Za-z0-9._-]`.

**D-013 A built-in voice: Kitten nano 0.8 int8.** *Superseded by D-033
(slice 3 ships the fp32 build under a build-neutral id).* 23 MB, 8 speakers,
Apache-2.0. int8 matched fp32 in an objective comparison (average-spectrum
correlation ≥ 0.996, same durations) at under half the size. Piper
Amy/Lessac were rejected for unclear or non-commercial dataset licences.
Speaker ids were mapped to KittenML's names by comparing embeddings
(bit-identical); their female/male labels were checked by pitch (female
205–255 Hz, male 107–183 Hz).

**D-014 One shared eSpeak NG data set.** All sherpa-onnx bundles from the same
release carry byte-identical 19 MB `espeak-ng-data` folders; QVoice keeps one
copy in `files/shared/espeak-ng-data`, marked with its build id
(`BundledVoices.ESPEAK_DATA_VERSION`). Bump that id whenever the bundled zip
changes.

**D-015 Manual dependency wiring.** `AppGraph` builds a handful of objects by
hand. No Hilt/Koin: annotation processing is the part of a build most likely
to break on Kotlin/AGP upgrades, and there's no build machine here to catch it.

**D-016 Toolchain: AGP 9.2.1 / Gradle 9.4.1 / built-in Kotlin 2.2.10.** AGP 9.2
is the version whose minimum Gradle is exactly the installed 9.4.1 (AGP 9.3
needs 9.5). Built-in Kotlin means no `org.jetbrains.kotlin.android` plugin
(AGP 9 rejects it) and no `kotlinOptions` (the JVM target follows
`compileOptions`). AndroidX versions are mid-2025 releases because newer ones
need compileSdk 36.1/37.

**D-017 Native packaging.** ABIs: arm64-v8a, armeabi-v7a, x86_64 (32-bit x86
dropped). `libsherpa-onnx-c-api.so` and `-cxx-api.so` are excluded: the JNI
library links only `libonnxruntime.so` (readelf). All libraries are 16 KB
page-aligned, as Play requires.

**D-018 R8 keeps sherpa-onnx whole.** Native code reads the Kotlin config
classes' fields by name; renaming any of them crashes release builds inside
the .so with no useful stack trace.

**D-019 A curated catalogue, generated from the real archives.** Voices are
offered only after a licence check and a sandbox round trip with the real
engine (docs/CATALOGUE.md). `tools/catalog/make_catalog.py` measures every
size and checksum from the archives and checks their contents; the parser is
strict and a unit test parses the shipped file. The catalogue ships in the
APK (works offline, always matches the code that reads it); an online copy
can be added later.

**D-020 Downloads through DownloadManager, into app-specific storage.** It
resumes after network loss and reboots, follows GitHub's redirect, can wait
for Wi-Fi, shows progress in the notification shade and needs no foreground
service. Files go to `Android/data/<pkg>/files/voice-downloads` (no storage
permission, invisible to other apps, removed on uninstall) and are deleted
once installed. Downloads over 50 MB ask before using mobile data; on Wi-Fi
they are queued "Wi-Fi only" so a dropped connection doesn't eat a data plan.
If Android 16's job quotas stall large downloads in the field, the fallback
is a user-initiated data transfer job with our own HTTP code.

**D-021 Installs run in a JobScheduler job, one work item per download.**
Unpacking Kokoro takes 35 s in the sandbox JVM and longer on a phone: too
long for a broadcast receiver, and it must survive the user leaving the app.
Work items (`JobScheduler.enqueue`) queue a second install behind the first;
re-scheduling the same job id would cancel the running one. An interrupted
install isn't completed, so it is redelivered; the download is kept until an
install succeeds or fails for good. The download-complete broadcast is only a
hint: `VoiceLibrary.refresh` re-reads DownloadManager (at app start and every
second while the voice screen is open), so a missed broadcast is recovered
and a spoofed one does nothing — which is why the receiver doesn't demand a
sender permission that some OEM builds might not grant their download
provider.

**D-022 Only the archive the catalogue validated is unpacked.** Size, then
SHA-256, before any byte is extracted. tar.bz2 via Apache Commons Compress
(Android has neither format); path traversal is rejected and links are never
created (the tar form of zip slip). The installer holds the install lock
(D-005), checks every file the manifest names, writes the manifest last and
swaps the folder in, so a failed or interrupted update leaves the old voice
working. Each archive's espeak-ng-data is skipped: it was checked
byte-identical to the shared copy (D-014).

**D-023 A byte budget for loaded models, made room for before loading.**
Kokoro is ~360 MB and sherpa-onnx fixes its language per instance, so
English and Hindi are two instances. `EngineHost` keeps at most
`budgetFor(RAM)` (RAM/6, 256-768 MB: on a 4 GB phone Kokoro and Supertonic
fit together, two Kokoro languages don't) of model files loaded and evicts
before loading, so the peak is budget + one model. A model larger than the budget
still loads, alone. Background services at 750 MB are what the low-memory
killer takes first, which users see as a crash.

**D-024 Multi-language voices.** A speaker lists the languages it is offered
in; empty means all of the model's (Supertonic: every style speaks all 31).
Kokoro speakers list only their native language, otherwise an American voice
would be offered for Hindi. Kokoro's per-language settings (`languageConfig`:
espeak-ng voice, lexicons, and for Chinese only the jieba dictionary and
number rules, which otherwise turn English digits into Chinese) make each
language a load variant. Supertonic takes the language per request. A
speaker offered in several languages gets one framework voice per language,
named `<voiceId>#<speaker>@<tag>`; single-language names are unchanged
(D-012).

**D-025 Text chunking for models that don't stream.** Supertonic returns
audio only when a whole request is done, so `TextChunker` feeds it one
sentence first (fast first audio), then packs the rest up to 220 characters.
(Extended to every family in slice 3: D-032.)
The rules lean towards *not* splitting (a missed split costs a slightly
longer chunk; a wrong one puts a pause inside a sentence): no split before a
lower-case word, after an initial, a title or a number.

**D-026 Deleting a voice: move away, refresh, release, delete.** The folder is
renamed out of `voices/` first (atomic: new loads fail cleanly), the list is
refreshed so nothing selects it, `EngineHost.release` waits for any synthesis
using it, then the files go. The bundled voice can't be deleted.

**D-027 OpenRAIL-M is accepted before download.** Its use restrictions bind
every user, so the voice library shows the licence and records acceptance
(`QVoiceSettings`, backed up with the other settings). Everything else in the
catalogue is Apache-2.0, MIT or public domain.

**D-028 Default voice per language.** A user's choice for "en-GB" beats one
for "en", which beats the automatic pick: region match, then quality
(per speaker where the model grades them), then downloaded before bundled,
then catalogue order. "Make default" in a language group sets both the tag
and the bare language, so the latest choice also covers regions without
their own voice.

**D-029 The engine's state is ready before `super.onCreate()`.**
`TextToSpeechService.onCreate()` calls `onLoadLanguage()` for the default
locale synchronously, on the main thread. So `QVoiceTtsService` gets its
graph lazily, sets up its voice list before calling `super.onCreate()`, and
never blocks on the voice scan when called on the main thread. Slice 1 broke
this rule and crashed on open; the static checker now rejects `lateinit` in
the engine class.

**D-030 Native callbacks are real classes.** sherpa-onnx's JNI looks up
the streaming callback's method by name and signature
(`invoke([F)Ljava/lang/Integer;`) on the object's own class. A class
implementing `(FloatArray) -> Int` has it; a Kotlin 2 lambda desugared by D8
does not, and the failed lookup aborts the process (slice 2 on a phone). So
`SherpaChunkCallback` is a class, EngineTest checks the signature, R8 keeps
it, and the static checker rejects lambdas passed to `generateWith*Callback`.

**D-031 Generation runs ahead of playback (`ReadAhead`).** Android's playback
queue blocks `audioAvailable()` while more than ~0.5 s of audio waits to play.
Generating on the framework's thread therefore paced generation to playback:
each sentence started generating ~0.5 s before the previous one ended, and
any sentence that took longer left a gap — even Kristin, five times faster
than real time, paused before long sentences. The model now generates on a
second thread into a queue (up to 20 s of audio); the framework thread only
hands audio over. Rules: `ReadAhead.run` returns only after the producer
thread has ended, joining uninterruptibly (EngineHost's lock is released
right after, and releasing a model still in use is a native crash); onStop
cancels the request's ReadAhead, and sherpa-onnx stops at the next sentence
boundary — it cannot interrupt a sentence. The logged speed counts only
generating time (0.2.2's "total" included playback and made Piper look 5x
slower than it is). The static checker rejects `generate()` outside
ReadAhead's `produce` lambda in the engine class.

**D-032 Every family is chunked; the opening is cut at a clause.** How long
the first sound and a Stop take is the time to generate one chunk, so every
family now goes through `TextChunker` (Supertonic up to 220 characters, the
streaming families up to 150, which only cuts over-long sentences). The first
two sentences are spoken one per chunk, and an opening sentence over 50
characters is cut once at its first comma, semicolon, colon or spaced dash
that leaves at least 12 characters on each side. The sample text showed why:
"Hello!" followed by a 60-character sentence meant waiting for the whole
second sentence after half a second of speech. Clause cuts are allowed only in
the opening, where the pause is natural and the gain largest; everywhere else
the rule is still "don't split".

**D-033 The built-in voice is Kitten nano 0.8 fp32, id `kitten-nano-en`.** On
the Galaxy M31 the int8 build generated at ~0.8x real time: its dynamically
quantised layers (ConvInteger) run slowly on CPUs without int8 dot-product
instructions. The fp32 build is 2.7x faster (sandbox), the same voices
(identical `voices.bin`), and quantisation-free; it costs 33 MB more (57 MB).
The sandbox timeline model reproduced the phone's int8 log (0.74 s first
audio, 5.0 s gap vs 0.72 s and ~5 s measured); on the M31 the fp32 build then
measured 1.2-1.5x real time (0.3.0 logs), enough to keep up.
The id no longer names the build, so later builds can ship without renaming
voices (names are public API). The old id is retired: `BundledVoices.RETIRED_IDS`
deletes its copy at start-up and the scan never lists it, and
`VoiceNames.RENAMED` maps names saved with it to the new id. The voice files
aren't in the source tree; `tools/get-binaries.ps1` puts them in place
after checking their SHA-256 (see D-040 for how they ship).

**D-034 Thread count: fast cores, a per-phone setting, reload on change.**
Automatic = the old core-count policy (1/2/4), capped at the number of fast
cores read from sysfs (`CpuInfo`): ONNX Runtime splits each step evenly
across threads, so a thread on a slow core holds up the rest (2 fast + 6 slow
phones get 2, the M31 keeps 4; an unreadable value means "unknown", never a
guess). "CPU threads" under Try it overrides it; it lives in a
not-backed-up preferences file because the best value depends on the
processor. The loader's `signature` carries the count and EngineHost reloads
a cached model whose signature differs — the next time it is needed, under
the lock, never mid-sentence.

**D-035 The logo is an adaptive icon, generated from the owner's artwork.**
The artwork (a flat 1254 px picture with baked rounded corners and a rim
light) is separated from its background by colour-to-alpha against a fitted
background field, limited to the artwork's neighbourhood, and placed on a
vector gradient in the same tones; the owner's square fills 68 of the 72 dp a
mask shows (ring inside the 66 dp safe zone, tail inside a circle mask). The
themed-icon glyph is the solid shapes traced to vector paths, with the ring
cut cleanly where the original fades. One script
(`tools/brand/make_brand_assets.py`) makes every icon and store graphic from
`tools/brand/logo-master.png`, so a new logo is one command.

**D-036 Brand colours instead of dynamic colour.** The app is visited rarely
(set up, pick voices), so recognising it as the icon's app matters more than
matching the wallpaper, and Play screenshots then show what every user sees.
All Material 3 roles are set explicitly (surface containers included) and
every text pair meets WCAG AA.

**D-037 Version policy: versionCode 1 until the owner says otherwise.**
versionName 1.0.0 for the first Play upload; debug builds carry the slice as
a suffix. Play refuses a repeated versionCode, so any later upload needs the
owner's go-ahead to raise it.

**D-038 Speed on this phone decides labels, warnings and the automatic
default.** Measured speed (SpeedBook: utterances of 1.5 s or more, smoothed,
per voice pack, device-only storage) wins; before a voice has spoken here,
its speed is predicted as the built-in voice's measured speed × the
catalogue's relative hint (from phone tests). Tiers: fast ≥ 1.5×, keeps up
≥ 1.1× (read-ahead makes anything faster than real time work; the margin
absorbs a busy phone), slow below. The voice library shows the tier, asks
before downloading a slow voice, and VoiceSelector ranks "keeps up" above
region and quality for the automatic default (a user's choice still wins;
an only voice still speaks). If the built-in voice was never measured, the
library measures it silently once.

**D-039 The first-run guide is one card, not a carousel.** The only step a
new user must take is making QVoice the preferred engine, and it happens in
Android's settings. So Home leads with a branded card naming that step and
the button that opens the settings, until the step is done (checked on every
resume); then it shrinks to a confirmation. Onboarding screens would add
swipes before the one action that matters.

**D-040 The built-in voice is read from the APK, not copied.** Given an
AssetManager, sherpa-onnx reads a voice's model, speaker table and tokens
straight from the APK — the way its own Android engine does. So the 60 MB copy
earlier versions unpacked into `filesDir` is gone: 55 MB less per phone, since
the APK now holds the files uncompressed (5 MB bigger than the zip it
replaces). Only the eSpeak NG data (19 MB) is still copied: espeak-ng opens
its files by path. The model files are stored uncompressed (`noCompress`
onnx, bin), so a load copies them out of the memory-mapped APK; deflated,
every load would first inflate them (start-up logs which it is). The asset
route has no error handling in sherpa-onnx 1.13.8 — a missing asset ends the
process, a damaged model aborts it — so there are three nets: the config
factory checks every asset exists before the native call (a missing one
fails that request, cleanly), the build refuses missing or truncated voice
files (exact sizes) and leftover zips, and `BundledAssetsTest` checks the
files match what the code asks for. Copies made by earlier versions are
hidden by the scan and deleted at start-up. `InstalledVoice.bundled` is now
derived from `assetDir`, so the two can't disagree. Accepted cost: during a
load sherpa-onnx briefly holds a second copy of the model (it copies the
asset into a buffer for ONNX Runtime), 57 MB freed as soon as the load ends.

**D-041 Fast speech: the model up to 1.5×, Sonic for the rest; pitch by
Sonic.** Android asks for rates from 0.1× to 6× and screen-reader users
listen at 2-4×. Measured in the sandbox (sherpa-onnx + Whisper small as the
listener, the ten Harvard sentences of list 1, the built-in voice and Piper
Kristin): word error rate, with the rate actually delivered in brackets.

| Asked | Kitten: model alone | Sonic alone | model 1.5× + Sonic | Kristin: model alone | Sonic alone | model 1.5× + Sonic |
|---|---|---|---|---|---|---|
| 2× | 9.9% (1.98×) | 4.6% | 4.7% (1.88×) | 22.6% (1.60×) | 30.1% | 24.4% (1.74×) |
| 2.5× | 29% (2.29×) | 20.3% | 14.0% (2.35×) | 35% (1.83×) | 51.3% | 38.3% (2.17×) |
| 3× | 53.5% (2.58×) | 37.8% | 30.1% (2.81×) | 42.3% (2.01×) | 70.7% | 53.8% (2.60×) |
| 4× | 60.1% (2.55×) | 68.1% | 52.5% (3.77×) | 51.4% (2.20×) | 97.5% | 83.5% (3.48×) |

The models' own speed control saturates (Piper tops out near 2.2×, Kitten
near 2.6×: phonemes hit their one-frame minimum) and blurs before that; at
equal delivered rates, Sonic alone and the 1.5× split are close, the split
slightly ahead from 2.5× up on Kitten (Whisper isn't a trained fast
listener, so only the ranking counts). The split wins on work: the model
makes ~30% fewer frames than for Sonic alone, which decides whether a voice
keeps up at speed on a mid-range phone. Hence `Prosody`: model speed =
rate clamped to 0.5-1.5×, Sonic's tempo = the rest; pitch 0.5-2× is Sonic's
(resampling + time-stretch, measured: 0.8× and 1.3× shift both voices' F0
by 0.80/1.28, durations unchanged). The model's delivered speed runs a
little under the asked one below 1.5× (Kitten 1.41×, Piper 1.30× at 1.5×);
accepted, it's the model's own behaviour and monotonic. Sonic runs on the
playback side per chunk and is flushed at each chunk's end: chunks end at
sentence or clause pauses, so no word tail waits for the next chunk. The
vendored Sonic.java (Apache-2.0) is unchanged except for clipping float
input before its 16-bit conversion (upstream wraps a +1.2 overshoot into a
full-scale click) and a removed debug print.

**D-042 Read aloud: an app-wide controller over the public TTS API,
paragraph utterances, main-thread confined.** The reader speaks through
QVoice's own engine exactly as any app would (TextToSpeech bound by package
name), so it gets voice choice, streaming, stop and D-041 for free and tests
the public path. Each paragraph is an utterance: onStart tells which one is
playing (highlight, resume point), and the next is queued when the current
starts, so the engine synthesises it while the current plays out. Utterance
ids carry a generation number that every stop, jump or reload bumps; late
callbacks of dropped utterances are ignored. The controller (`ReadAloud`)
lives in AppGraph so reading survives activity recreation, and is confined
to the main thread: TtsSpeaker posts TTS callbacks there and the audio-focus
listener runs there, so there are no locks and no tap can race a callback.
It is JVM-tested through two small interfaces (`Speaker`, `PlaybackGuard`).
Languages are chosen per paragraph (`ReaderLanguage`: the phone's locale if
its voice can read the script, else an installed language written in exactly
that script, English first for Latin). Audio focus: gain with
pause-when-ducked (speech ducked under a notification is hard to follow);
transient loss keeps the focus request so the gain callback can resume;
"becoming noisy" pauses. With a screen reader on, pieces are ≤300
characters and nothing is queued ahead: Android's TextToSpeechService runs
one utterance at a time for all callers, so TalkBack would otherwise wait
for everything the reader queued. Until slice 10 reading needed the reader
on screen (screen kept on): without a foreground service the process is
frozen or killed in the background. D-043 lifts that.

**D-043 Listening with the screen off: a mediaPlayback foreground service
that mirrors the reader, kept through pauses for 10 minutes.**
`ReadAloudService` is a platform `Service` with a platform `MediaSession`
and a `Notification.MediaStyle` notification. Media3's `MediaSessionService`
was the alternative; it would bring two libraries, a `Player` adapter over
TextToSpeech (`SimpleBasePlayer`) and code the authoring sandbox cannot
compile (Google Maven is unreachable there), while the platform classes are
compile-checked against the Android API there with warnings as errors. What
Media3 would have handled is done by hand, following its choices:
- *A listening session* is Play until Stop, new text, the reader closed
  while not reading, or a long pause (`ReadAloud.stop()` → READY, text and
  place kept). The notification and the session exist only during one
  (`NowPlaying.of`, unit-tested); the service holds no reading state and
  hands every button back to `ReadAloud`.
- *Started when reading starts* (`NowPlaying.startsService`: PLAYING, not a
  Play that failed at once), by AppGraph collecting the reader's state on
  `Dispatchers.Main` (never `.immediate`, so it runs after the reader's own
  call and sees the latest state only). Reading starts with QVoice in front
  (the reader, or the notification during a session), which Android 12+
  requires for starting a foreground service.
- *Kept in the foreground while paused, up to 10 minutes*
  (`LONG_PAUSE_MS`), with a Stop button, instead of leaving the foreground
  at every pause: Android 12+ refuses to put a service back in the
  foreground from the background, which is where Play on the lock screen,
  a headset button or the end of a phone call happens. Media3 1.6 made the
  same change ("keep foreground service state for an additional 10 minutes
  when playback pauses"). A paused session then ends by itself: the
  notification goes and the engine is let go (reopening the reader and
  pressing Play starts a new one).
- *startForegroundService's contract*: `startForeground()` is called in
  `onStartCommand` whatever the state (a session that ended in between gets
  one and leaves); the state is collected only from then on, so nothing can
  stop the service first (that would crash the app).
- *Buttons*: Android 12 and older draw the player from the notification's
  actions (broadcasts to a non-exported receiver; always the same four);
  Android 13+ draws it from the session's `PlaybackState` actions (next is
  offered only while there is a next paragraph, with the slot reserved so
  Stop, a custom action, doesn't move) and sends transport controls to the
  session callback. Headset and Bluetooth keys reach the same callback
  through the platform's default media-button handling (single press
  play/pause, double press next).
- *Privacy*: the notification, the lock screen and the session metadata
  show "Read aloud" and the position ("Paragraph 3 of 12", or why it
  paused), never the text: lock screens are public and the text may be a
  private message; the listener hears the words anyway. Hence public
  lock-screen visibility needs no redacted version.
- *Wake lock*: a partial wake lock while PLAYING only, renewed at every
  paragraph with a 10-minute timeout. With the screen off the phone may
  sleep in any gap where no audio plays, and gaps are certain whenever the
  voice computes more slowly than the audio plays: the built-in voice runs
  1.2-1.5× real time on the Galaxy M31, so at the reader's 3× (model 1.5×
  plus a 2× stretch, D-041) it makes audio at ~0.7× the playback rate. Play's
  "excessive wake locks" vital exempts apps playing audio to the user.
- *No POST_NOTIFICATIONS*: media-session notifications are exempt on
  Android 13+, so QVoice asks for no permission at all.
- *The reader is singleTask in its own task* (`taskAffinity`
  `.reader`), so the notification and Home return to the one reader, and a
  reader opened from Chrome's selection menu doesn't stay stacked on top of
  Chrome once the user goes back there. Callers asking for a result
  (PROCESS_TEXT) get "cancelled" at once; the reader never returned one.

**D-044 Binary files stay out of git; one script fetches and checks them;
GitHub builds every push.** The ~110 MB of binaries (the sherpa-onnx AAR,
the built-in voice, the eSpeak NG data) come from their publishers and
rarely change. In git they would weigh on every clone forever (git keeps
every version), and Git LFS's free quota is small; so `.gitignore` lists
them and `tools/get-binaries.ps1` fetches them by URL and SHA-256. One
script in PowerShell, because the owner builds on Windows (PowerShell 5.1)
and GitHub's Linux runners have PowerShell 7: one list of URLs and checksums.
It is tested with 7.4 in the sandbox and written to 5.1's limits (ASCII
only, nothing newer than PowerShell 5, compression assemblies loaded
explicitly); its first 5.1 run is the owner's. The eSpeak NG zip,
which had no recorded origin, is the voice archive's `espeak-ng-data` folder
zipped (Python's zipfile at level 9 reproduces the slice-1 zip byte for
byte); the script checks its content (the sha256sum lines of its files,
sorted) rather than its bytes, because .NET and Python compress differently
and the app only reads the content. The workflow runs, with the real
Android tools, what the sandbox can only type-check (the Compose compiler's
code generation, resource merging, lint, the real test runner); lint is
reported but not enforced until its first clean run, since it never ran on
this code. It also makes an unsigned release build (R8, resource shrinking,
lint's release checks) with a placeholder source URL, so what only a Play
build exercises fails on the push, not in Android Studio at upload time. Debug APKs built there carry GitHub's debug signature, so the
first one installed over a locally built debug app needs an uninstall: a
debug keystore shared through the repository (not secret: debug builds
can't be published) removes that when the repository is live.

**D-045 The sleep timer ticks at paragraph ends.** `ReadAloud` stores the
end time (`sleepAt`, on `SystemClock.elapsedRealtime`: it counts through deep
sleep and never jumps with a clock change) and checks it when a paragraph
ends: due → pause before the next paragraph, which is where Play resumes. No
timer thread, no alarm, nothing to keep awake beyond what reading already
holds, and no sentence is ever cut. The one catch is the queue-ahead of
D-042: the engine starts a queued paragraph the instant the previous one
ends, so stopping then would clip its first syllable. Within two minutes of
the end (`SLEEP_LOOKAHEAD_MS`, longer than a paragraph takes at normal
speed) nothing is queued ahead; paragraphs are queued one by one when the
previous ends, costing the engine's start-up pause (~0.3 s with the built-in
voice) in those minutes only. The time runs during pauses (it's shown as a
clock time, "stops at 23:45", which a countdown that froze on pause would
contradict), and a timer found expired at Play is dropped rather than
stopping the new start. The UI and the notification show the clock time,
converted from elapsedRealtime when drawn, so nothing needs to tick.

**D-046 The reader's voice choice is the language's default voice.** A
reader-only voice (a setting beside Home's per-language defaults) was the
alternative; it would let TalkBack keep a fast voice while the reader uses a
natural one, but it doubles what the user has to understand ("why does the
reader sound different from my other apps?"), and per-app voices, planned
for the engine itself, would cover that case for every app, not just the
reader. So the reader lists the voices of the paragraph's language (all
regions, `VoiceRow.listFor`), marks the one `VoiceSelector.defaultFor`
picks for the paragraph's locale, and saves a choice exactly as Home's
"Make default" does (`QVoiceSettings.makeDefault`: the voice's language tag
and its language), then restarts the paragraph. A detail of Android's
TextToSpeech makes the restart need more than a new request:
`setLanguage()` asks the engine for the language's default voice
(`onGetDefaultVoiceNameFor`) once and then names that voice in every
request, and a named voice outranks the default (VoiceSelector.resolve; D-028). So
`Speaker.voicesChanged()` makes the reader's TtsSpeaker forget its language
and call `setLanguage()` again with the next paragraph. Other apps pick up
a changed default the next time they set their language, as with any
engine.

**D-047 A readable width, applied as padding.** Content stops at 640 dp
(about 70 characters of body text, Material's comfortable line length) and
is centred; the scrolling list itself stays full width, so it scrolls from
anywhere on the screen and its edge glow spans it. The width comes from
`BoxWithConstraints` (the real space, right in split screen too, unlike the
configuration's screen width), and the side padding is the system bars on
that side plus a margin of at least 16 dp (`ReadableWidth.sidePadding`,
plain Kotlin and unit-tested; `readablePadding` applies it). Phones in
portrait keep exactly their 16 dp margins. Launcher shortcuts are static
(`res/xml/shortcuts.xml`): the system starts them in a new, cleared task,
which for the singleTask reader means its own task, showing the session in
progress or the paste field.

**D-048 Shared text files: the stream wins, read capped and off the main
thread.** A share with `EXTRA_STREAM` is read from the sharing app's
provider (its read grant comes with the intent), and its content is used
even when the share also carries `EXTRA_TEXT`, which file managers often
fill with the file's name; the text is only the fallback when the file
can't be read. At most 1 MiB is read (`SharedText.MAX_BYTES`: more than the
reader's 100,000 characters in any encoding, so a cut file is still marked
cut), decoded as UTF-8 unless a byte-order mark says UTF-16, with bad bytes
replaced; more than 2% replacement or NUL characters in the first 4,096
means it isn't text, and the paste field explains. The read runs on its
own short thread rather than the app's background executor, which a model
warm-up can hold for seconds; a share counter and an `isFinishing` check
keep a slow read from replacing a newer share or starting to read after
the user left. Only `text/plain` shares are accepted (the existing
filter): Markdown or HTML read aloud would include their markup.

**D-049 The privacy policy on the publisher's domain; every outside
address in one place.** The policy is `store/privacy.html`, published at
https://zinijo.com/qvoice/privacy.html next to QTune's, rather than on
GitHub Pages: Play asks for the policy inside the app too, and an address
inside an installed app can never be changed again, so it belongs on a
domain Riniso owns, which outlives any repository or account name. The
page is self-contained (no fonts, scripts or images from elsewhere), so the
policy page itself tracks nobody. `ui/Links.kt` holds the addresses (policy,
support email, Play listing); the source repository stays in
`gradle.properties` because the release build checks it. Feedback is a
plain email (`ACTION_SENDTO`, "mailto:" with extras, as Android's guide
does): no form, no server, nothing sent until the user sends it, and the
details it starts with (`FeedbackEmail`: versions, phone, preferred engine)
are visible and deletable in the mail app; never any text QVoice read.
Rating is a link to the Play page (the https address, in the Play Store app
when installed), as Google advises for a button; the in-app review card
would need Play's proprietary library and suits prompts QVoice doesn't make.

**D-050 The reader remembers its text and place, on the phone only.**
`ReaderMemory` follows `ReadAloud`'s state and keeps the prepared
paragraphs (not the raw text: the place is a paragraph number, and
preparing again could split differently, e.g. with TalkBack on) and the
paragraph index, in one file in `noBackupFilesDir` (never in a backup or a
device transfer; the backup rules are opt-in lists anyway). A write happens
only when the text or the place changes, on a thread of its own (the
background executor can be busy with a model warm-up for seconds), only the
latest pending one is done, and each goes through a temporary file renamed
over the old one, so a crash mid-write leaves the previous text whole. A
finished text is remembered from its start; an emptied reader ("Read
something else") deletes the file. The file is read once per process, on
the main thread, when the reader is first needed (a few hundred kilobytes
at most): the reader then never shows empty first and jumps a moment
later, and the TTS service, which never touches the reader, never pays for
it. `ReadAloud.restore` only fills an empty reader, so a share that arrives
first always wins, and a recreated `ReaderActivity` re-reads its intent
only when there was no memory. Only the latest text is kept, deliberately:
a list of recent texts would keep messages and emails the user shared once
on the phone indefinitely. With the memory, a share that can't be read (a
web address alone, a file that isn't text) no longer empties the reader:
its notice shows above the text, and reading goes on.

**D-051 "Read copied text" reads the clipboard only once the reader has
focus, and never a sensitive clip unasked.** Android 10+ lets only the
focused app (or the keyboard) read the clipboard, so the shortcut
(`ReaderActivity.ACTION_READ_CLIPBOARD`) sets a flag and the read happens
in `onWindowFocusChanged(true)` (at once if the window already has focus).
A static shortcut rather than a Quick Settings tile first: one XML entry,
works on every launcher, and Google ranks it the most value for the least
effort; a tile can follow. `ClipboardText` looks at the clip's description
before its content: a clip marked sensitive
(`android.content.extra.IS_SENSITIVE`, set by password managers and for
one-time codes) is refused without being read, so Android's "pasted"
notice doesn't even appear, and "Read it anyway" reads it on request. It
takes text, or the text of copied HTML, and never calls `coerceToText()`,
which would open a copied file's content on the main thread. A recreated
reader never reads the clipboard again.

**D-052 Debug builds are a separate app.** `applicationIdSuffix ".debug"`
makes test builds com.riniso.qvoice.debug ("QVoice debug", engine label
"QVoice Text-to-Speech (debug)", src/debug/res), so they install next to
the version from Google Play: the Play version is signed with Google's app
signing key and a local build with the debug key, so neither can update
the other, and swapping between them meant uninstalling, which deletes the
downloaded voices. The namespace (R, BuildConfig, class names) stays
com.riniso.qvoice; everything that needs the running package already asks
for it (`context.packageName`: the reader's TextToSpeech, the "is QVoice the
preferred engine" check), and what must name the Play app says so
(`Links.PLAY_APP_ID`). Static launcher shortcuts have to write their target
package out, so debug builds carry their own copy of `shortcuts.xml`;
`tools/check_project.py` (rule 13) fails when the copies differ in anything
but the package. Sister apps bind com.riniso.qvoice: the Play version.

**D-053 Help inside the app; a battery warning only when it applies.**
Help is a screen of the app (`HelpScreen`), not a web page: it works
offline like everything else, can't go stale against the installed
version, and its answers carry buttons that do what they say (the
text-to-speech settings, QVoice's App info, the voice library, the privacy
policy). The questions are the ones users of third-party engines and
readers ask most (researched October 2026); one answer is open at a time
so the list stays scannable, and each question is a TalkBack heading.
The battery warning on Home is shown only when Android actually restricts
QVoice: `ActivityManager.isBackgroundRestricted` (the "Restricted" battery
setting, which is also how some makers' "deep sleeping" works) or the
restricted app standby bucket (Android 11+). A permanent "disable battery
optimisation" nag would be noise for most people, and requesting the
exemption directly (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is limited by
Play's policy to other kinds of apps; App info is the one page every
Android version has.

**D-054 "Preparing the voice" is part of the reader's state.**
`ReadAloud.State.preparing` is true from Play (or a jump, a speed or voice
change) until the paragraph's first audio (`onStart`), and again between
paragraphs from one's end to the next one's start; every way of stopping
clears it. The UI shows it only once it has lasted 400 ms
(`rememberLastingFlag`): a paragraph computed ahead starts within
milliseconds of the previous end, and a cue that flashed at every
paragraph would be worse than none. The notification ignores it
(NowPlaying leaves it out), so it causes no extra notification updates.
Try it uses the same cue from its own state (speaking, no first audio
yet).

## What was taken from each reference app, and what was left behind

| App | Taken | Left behind (and why) |
|---|---|---|
| HayaiTTS (GPL-3.0) | sherpa-onnx family configs; sid clamping; catalogue of sherpa-onnx voices (checksums verified correct) and the idea of per-locale defaults | `BIND_TEXT_SERVICE` bug; in-app APK updater (banned on Play); non-streaming synthesis; "MIT" labels on Piper voices whose dataset licences differ |
| Marmalade (MIT src) | Engine registration checklist; locale-code handling; hot-path caching; Kitten names/order; planned: per-app voices, effects, read-aloud, clipboard tile | Cloud voices with user API keys (conflict with the offline/private promise); battery-optimisation exemption request (Play-restricted); `specialUse` keep-alive service |
| NekoSpeak (MIT) | Streaming and engine-lock design; planned: voice cloning of the user's own voice | Celebrity voice clones (Play impersonation policy); every voice labelled en-US (breaks Tamil requests); `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| AudioLab (MIT) | Model-manager UX ideas | React Native code (can't merge into a native app); non-TTS demos (ASR, diarization, keyword spotting...) — out of scope for a voice engine |
