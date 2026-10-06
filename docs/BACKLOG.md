# QVoice backlog

Each slice is small, self-contained and shippable. Status as of slice 20
(version 1.0.0, versionCode 1).

## Done

- **Slice 1 — system engine with a built-in voice** (0.1.0). TTS service,
  language/voice negotiation, streaming, stop, Kitten nano bundled, home +
  about screens, GPL notices, static checker.
- **Slice 2 — voice library and downloads** (0.2.x). Seven validated packs
  (Kokoro, Supertonic 3, five Piper voices), DownloadManager downloads,
  verified installs in a job, delete/update/cancel, OpenRAIL-M acceptance,
  per-language Kokoro, engine memory budget, language chips. Fixes from the
  first phone builds: crash on open (D-029), crash on Speak (D-030).
- **Slice 3 — smooth speech** (0.3.0). Kitten nano fp32 built in (D-033),
  generation ahead of playback (D-031), chunking with an opening clause cut
  (D-032), real speed in log and UI, CPU threads setting (D-034). Confirmed
  on the Galaxy M31: built-in voice 1.2-1.5× real time with ~0.35 s to first
  audio, Kristin 2.5-3.0×; threads 1/2/4 gave Kristin 0.8×/1.3×/2.8×, so
  "automatic = 4" is right there.
- **Slice 4 — release basics and the new logo** (1.0.0). Adaptive launcher
  icon + themed icon from the owner's artwork (D-035), brand colours (D-036),
  version policy (D-037), engine label, Play Store kit in `store/` (icon,
  feature graphic, listing text, privacy policy page), 16 KB page alignment
  checked.
- **Slice 5 — speed on this phone** (1.0.0). Measured speed per voice,
  predictions before download, "slow on this phone" warning, speed-aware
  automatic default (D-038).
- **Slice 6 — premium look and first-run setup** (1.0.0). Branded setup card
  as the first-run guide (D-039), redesigned voice list (gender colour,
  Default badge, speed on this phone, "Make default" on the selected voice).
  Still to do: the Play screenshots (steps in store/LISTING.md; send the raw
  shots and I'll make captioned listing images in the logo's style).
- **Slice 7 — storage diet** (1.0.0). The built-in voice is read straight
  from the APK through the AssetManager instead of being unpacked on first run
  (D-040): about 55 MB less storage per phone, less to do before the first
  word. Checks against sherpa-onnx's one unguarded crash path (missing or
  damaged asset), a build guard for missing, truncated or leftover voice
  files, the bundled-voice script reworked. The Compose screens are now
  compiled in the authoring sandbox against stubs of the real Compose API,
  which found three small UI fixes (keyboard gap, `adjustResize`, a frame of
  Home before the voice library). 154 unit tests.
- **Slice 8 — fast speech and pitch** (1.0.0). Rates up to 6×: the model
  speaks up to 1.5×, a pitch-preserving time-stretch (Sonic, vendored) does
  the rest; pitch requests now work (D-041, measured with Whisper as the
  listener: the built-in voice at 3× went from 54% of words misheard to
  30%). Try it gained a pitch slider and speeds to 3×. 165 unit tests.
- **Slice 9 — read aloud** (1.0.0). "Read aloud" in every app's text
  selection menu and in Share; a reader with paragraph highlighting,
  play/pause, previous/next, speed; per-paragraph language; audio focus and
  headphone-unplug pauses; TalkBack-friendly pieces (D-042). 186 unit tests.
- **Slice 10 — listening with the screen off** (1.0.0). A mediaPlayback
  foreground service while a listening session lasts: media notification
  and lock screen player (previous, play/pause, next, Stop; position only,
  never the text), headset and Bluetooth buttons through a media session,
  a wake lock while reading. Paused sessions keep their controls for 10
  minutes (Media3's choice, D-043). The reader became one instance in its
  own task. 190 unit tests.
- **Slice 11 — repository and build kit** (1.0.0; no app change).
  `tools/get-binaries.ps1` fetches and checks every binary file (the eSpeak
  NG data now has a recorded origin: the voice archive), `.gitignore` and
  `.gitattributes` for a public repository, a GitHub Actions build (tests,
  Compose compiler, debug APK, lint reported), README steps to publish
  (D-044). *Switched on by the owner's repository;* then: a shared debug
  keystore in the repository (so APKs built on GitHub install over local
  builds), lint made a gate after its first clean run.
- **Slice 12 — sleep timer** (1.0.0). 15/30/45/60 minutes from the reader's
  top bar; stops at the end of a paragraph; the stop time in the reader and
  the notification (D-045). 193 unit tests.
- **Slice 13 — the voice, right in the reader** (1.0.0). The voices of the
  language being read, with speed on this phone; the choice is the
  language's default (D-046), heard at once. Fixed: a new default wasn't
  heard until the language changed. 196 unit tests.
- **Slice 14 — headings, release check** (1.0.0). Section titles are
  TalkBack headings; the GitHub build also makes an unsigned release build
  (R8, lint's release checks).
- **Slice 15 — shortcuts, big screens, release guide** (1.0.0). Launcher
  shortcuts (Read aloud, Voices); a readable, centred width on tablets and
  in landscape, clear of side system bars (D-047); `store/RELEASE.md`, the
  first Play upload step by step; exact PowerShell test commands at the top
  of TESTING.md. 199 unit tests.
- **Slice 16 — the reader takes text files** (1.0.0). Shared `.txt` files
  are read (the file's content, UTF-8 or UTF-16, capped, binary refused,
  D-048); Home's Read aloud card shows what the reader holds and leads back
  to it. 205 unit tests.
- **Slice 17 — contact, privacy policy, feedback** (1.0.0). The owner's
  details everywhere (Riniso, support@zinijo.com,
  github.com/cyber-cyper/qvoice as `qvoice.sourceUrl`); the final privacy
  policy page for zinijo.com/qvoice/privacy.html (D-049); About: Send
  feedback (prefilled, visible details), Rate QVoice, the privacy policy
  link Play requires in the app. 210 unit tests.
- **Slice 18 — continue listening** (1.0.0). The reader remembers the last
  text and place on the phone (D-050); Home's card continues with one tap;
  the reader opens at its place; "Back to the previous text" after a
  mistaken "Read something else"; a share QVoice can't read no longer wipes
  the reader. 226 unit tests.
- **Slice 19 — read copied text** (1.0.0). A launcher shortcut that reads
  the clipboard aloud, never a clip marked sensitive unasked (D-051). 229
  unit tests.
- **Slice 20 — test builds next to the Play version** (1.0.0). Debug builds
  are "QVoice debug" (com.riniso.qvoice.debug, D-052); the checker guards
  their copy of the shortcuts and catches deprecated `Locale` constructors
  (the owner's build warned about three in the tests). The release guide
  goes through Play's internal testing first.

## Next (in order; anything waiting for the owner moves up as soon as it arrives)

- **Waiting for the owner:** the Claude GitHub App installed on
  cyber-cyper/qvoice (the account is linked; pushes need the app), then the
  first push and GitHub's build; `store/privacy.html` uploaded to
  zinijo.com/qvoice/; a test of slices 17-20 on the phone; the Play upload
  (internal testing, then the closed test); the screenshots and the
  decisions under "Open decisions".

The plan below comes from Android's core app quality guidelines, Material
3, Play's policies and listing guidance, and what users praise and
complain about in other read-aloud apps (reviews of @Voice, Speechify,
NaturalReader, ElevenReader, Google's Reading mode; checked October 2026).
What users want most from a free, private, offline reader: it never stops
on its own and resumes where they left off (done in slices 10 and 18),
good voices, no ads or subscriptions (QVoice's strongest selling points:
lead the listing with them), then sentence highlighting, text size, and
help when something doesn't work.

**Chunk A — help where people get stuck** (next; no owner input needed)

- **Slice 20 — Help and FAQ, offline, plus a battery check.** A Help
  screen (from About and from the setup card) answering what users of
  third-party engines ask most: making QVoice the preferred engine (Pixel
  and Samsung menu paths), reading that stops with the screen off (battery
  restrictions: set QVoice, and the reading app, to Unrestricted), an app
  that still uses Google's voice (apps that choose their own engine), the
  best voice for TalkBack, whether text leaves the phone, freeing storage,
  the clipboard notice. Send feedback at its end. On Home, a card only
  when Android has QVoice under battery restriction
  (`ActivityManager.isBackgroundRestricted`), with the button to its
  settings; QVoice can't ask for an exemption itself (a Play policy
  restriction), and doesn't need one when unrestricted.
- **Slice 21 — "Preparing the voice…".** Core app quality asks for audio
  within a second of Play or a visible cue; a large voice on a mid-range
  phone can take several seconds for the first sentence. The play button
  shows progress until the first audio, in the reader and in Try it.

**Chunk B — reading comfort**

- **Slice 22 — text size in the reader** (four steps, kept with the
  settings), beyond the phone's own font size; checked at 200% font scale
  (Android 14+), with TalkBack.
- **Slice 23 — sentence by sentence.** Highlight the sentence being read
  inside its paragraph, and skip by sentence; the most praised feature of
  the big readers. Sentences become the unit the reader queues (one ahead,
  as now), the memory keeps the sentence, the notification still counts
  paragraphs.
- **Slice 24 — Quick Settings tile "Read copied text"** (Android 13+ asks
  the user to add it from within the app; Android 14+ rules for starting
  the reader from a tile).

**Fast, stable voice installs** (needs the GitHub repository; unchanged
plan). Mirror the voice archives as zips in QVoice's own GitHub releases and
point the catalogue there: installs in seconds instead of minutes (the M31
took 58 s for Kristin and 294 s for Kokoro in a debug build: bzip2 runs in
Java), and checksums can't change under us. With them, **listen before you
download**: a few seconds of each voice, made with the real engine, hosted
next to the zips and played from the voice library.

**Not planned, on purpose**

- **A bottom navigation bar.** Material 3 keeps it for three to five
  destinations of equal importance shown on every screen; QVoice has two
  tasks (read, choose voices), and the reader's own controls already use
  the bottom edge. Home with its top-bar actions stays; revisit with a
  document library (then a bar on phones, a rail on tablets).
- **Google's in-app review card.** It needs Play's proprietary library and
  suits prompts the app shows by itself; "Rate QVoice" in About links to
  the Play page, as Google advises for a button.
- **Dynamic (wallpaper) colours by default.** The indigo is the brand and
  matches the icon and the store graphics; a "match wallpaper" option can
  come later if users ask.
- **A "What's new" pop-up or an onboarding carousel.** The setup card is
  the whole first-run guide; Play's release notes say what's new.

**Deferred by the owner:** the helper for the Riniso apps ("finalise QVoice
first"); Indian-language voices (left to Google's engine for now).

### After the first release

- **Per-app voices** (Marmalade-style routing by calling app) and voice
  effects. It changes how the engine picks every app's voice, TalkBack's
  included (the per-app voice has to be answered when an app sets its
  language, D-046), so it is best built on a core that has been in users'
  hands for a while.
- **What the first users show us:** Play's pre-launch report, Android
  vitals (crashes, ANRs, excessive wake locks), reviews.
- **Lint as a gate** in the GitHub build, after fixing what its first run
  reports; a debug keystore shared through the repository (D-044).
- **Voice cloning of the user's own voice** (Pocket / ZipVoice), with
  consent checks, live recording only, no celebrity or third-party voices.
- **Helper for the Riniso apps** (deferred by the owner). One Kotlin file
  (plus the `<queries>` entry) the sister apps copy in: detect QVoice, open
  its Play page, bind by package name, choose a voice by language and
  gender, fall back to the system engine. Wire into QTune (English + Tamil)
  and QBrain. *Needs: the file in each app that creates its TextToSpeech.*
- **Fast Indian-language voices** (deferred by the owner, decision (c)
  below). If revisited: Piper medium voices trained on the IIT Madras
  IndicTTS recordings (licence allows commercial use with attribution, to
  be confirmed in writing), a GPU day per voice.
- **Documents:** PDF (Android 15's PdfRenderer reads text) and EPUB, with
  the place kept per document; then a "Continue listening" home-screen
  widget.
- **A short list of recent texts**, only if users ask: it would keep texts
  they shared once on the phone for longer (D-050 keeps one on purpose).

## Before the first Play release

Everything Play asks for is drafted in `store/LISTING.md`, step by step in
`store/RELEASE.md`. What remains needs the owner:

- **The source on GitHub** (https://github.com/cyber-cyper/qvoice exists,
  empty): link GitHub for Claude, or push it with GitHub Desktop
  (RELEASE.md step 1). Then the eSpeak NG, piper-phonemize and sherpa-onnx
  v1.13.8 source archives go into its first release's assets (GPL-3.0 §6;
  see NOTICE.md).
- **The privacy policy page:** upload `store/privacy.html` so it opens at
  https://zinijo.com/qvoice/privacy.html (the address in the app and for
  Play Console).
- **Foreground service declaration** (Play Console → App content →
  Foreground service permissions): "Media playback", for reading aloud with
  the screen off. The text to paste and what the ~30 s video must show are
  in `store/LISTING.md`.
- **Closed test:** personal developer accounts created after 13 Nov 2023
  need 12+ testers opted in for 14 days in a row before production
  (confirmed in Play Console Help, October 2026). The 14 days are the
  longest wait on the way to release, so start them as soon as the source
  is pushed.
- **App signing:** let Google create and manage the app signing key (Play
  App Signing, RELEASE.md).
- Screenshots (store/LISTING.md): at least 4 at 1080 px or more to be
  eligible for Play's recommendations. Check "QVoice" isn't a conflicting
  name on Play.
- versionCode stays 1 for the first upload; raise it only on the owner's say.

## Open decisions (owner)

- **Store listing translations.** Play Console translates the listing for
  free into 100+ languages (Hindi and Tamil included) and, with Gemini,
  the app's own screens into 10 (not Hindi or Tamil). Recommendation: the
  listing only in languages QVoice has voices for, with "the app's screens
  are in English"; not Tamil (no Tamil voice) or Hindi (its voices are slow
  on mid-range phones) until fast voices exist. See store/LISTING.md.

Decided: the repository is github.com/cyber-cyper/qvoice; contact Riniso,
support@zinijo.com; the privacy policy on zinijo.com (D-049); no in-app
translations; Indian-language voices left to Google's engine for now
(option (c) of the earlier choice); the helper for the Riniso apps waits
until QVoice is final; the reader remembers the last text (D-050).

## Parked (reachable later, not forgotten)

- **Stop can't interrupt a sentence.** sherpa-onnx offers no cancel inside a
  generate call, so a Stop (or a voice switch) waits for the chunk being
  generated — up to ~4 s for a 150-character sentence with the built-in voice
  on the M31, ~2 s with Piper. ONNX Runtime has a terminate flag
  (RunOptions) that sherpa-onnx doesn't expose; revisit if upstream adds it.
- **The pause after a very short first sentence** ("Hello!" then a longer
  clause) remains with voices near real time (~0.5 s expected with the
  built-in voice on the M31). Holding playback until the second chunk is
  ready would remove it at the cost of later first audio — wrong for screen
  readers, maybe right for read-aloud (slice 9).
- If DownloadManager stalls big downloads under Android 16's job quotas,
  switch to a user-initiated data transfer job with our own HTTP code.
- Kokoro Japanese needs a Japanese G2P that sherpa-onnx doesn't have
  (espeak-ng can't read kanji); Supertonic covers Japanese meanwhile.
- Separate `:tts` process for crash isolation between UI and engine — only if
  field reports show native crashes; costs multi-process settings handling.
- **Keeping up at high rates.** The automatic default voice is chosen for
  keeping up at normal speed; at rate R a voice needs to generate R/1.5
  times faster than real time (D-041). Make the choice rate-aware if fast
  listeners report pauses.
- **Speed per thread count.** Measured speeds are kept per voice, not per
  CPU-thread setting; after a thread change the smoothing takes a few
  utterances to settle. Fine while the setting is rarely touched.
- The Compose compiler plugin's code generation isn't run in the authoring
  sandbox (it needs the real Compose runtime from Google Maven); type and
  composable checks are (verify.sh step 4).
- NNAPI / XNNPACK execution providers: the sherpa-onnx build contains NNAPI
  code, but TTS models have dynamic shapes, which NNAPI handles poorly, and a
  driver crash would take the engine down. Try only if CPU speed proves
  insufficient on common phones.
