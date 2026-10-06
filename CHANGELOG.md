# Changelog

Version numbers: since 1.0.0 the versionCode stays 1 until the owner says to
raise it; debug builds show the slice after the version ("1.0.0-slice22").

## 1.0.0 — slice 22: "Preparing the voice…" — 2026-10-06 (versionCode 1)

- **When the voice takes a moment**, it now shows. In the reader, a ring
  turns around the play button and "Preparing the voice…" replaces the
  position until the first sound: after Play, a jump, a speed or voice
  change, and between paragraphs on a slow phone. In Try it, the same line
  appears under Speak. Waits shorter than 0.4 s (the usual gap between
  paragraphs computed ahead) show nothing, so nothing flickers. TalkBack
  hears it on the play button. (Android's core app quality guidelines ask
  for audio within a second of Play, or a visible sign that it's coming;
  a large voice on a mid-range phone can take several seconds.)
- Tests: 233 (was 231), 4 new guards proven by mutation.

## 1.0.0 — slice 21: Help, and a battery warning — 2026-10-06 (versionCode 1)

- **Help**, from Home's top bar (?), from the setup card ("Need help?")
  and from About: answers to what people ask most, one open at a time,
  each with the button that does what it says. Making QVoice the phone's
  voice (with the menu path on Pixel and Samsung phones), having any text
  read aloud, an app that still uses another voice, reading that stops
  with the screen off, the best voice for TalkBack, a language QVoice
  doesn't read, privacy, the clipboard notice, freeing storage; Send
  feedback at the end. Works offline; TalkBack can jump from question to
  question.
- **A battery warning on Home**, only when Android restricts QVoice in the
  background ("Restricted" battery setting, or the restricted standby
  bucket Android puts apps in), the usual reason reading stops with the
  screen off and apps that use QVoice go quiet; it opens QVoice's app
  settings and disappears once lifted.
- Tests: 231 (was 229), 2 new guards proven by mutation.

## 1.0.0 — slice 20: test builds next to the Play version — 2026-10-06 (versionCode 1)

- **Test builds are now a separate app, "QVoice debug"**
  (com.riniso.qvoice.debug), so they install next to the version from
  Google Play instead of clashing with it (different signing keys can't
  update each other, and swapping meant uninstalling, which deletes the
  downloaded voices each time). In Android's text-to-speech settings it is
  "QVoice Text-to-Speech (debug)". Release builds are unchanged:
  com.riniso.qvoice, "QVoice".
- One-time step on the test phone: uninstall the old test build (it was
  com.riniso.qvoice too, and would block the version from Play).
- Debug builds have their own copy of the launcher shortcuts (static
  shortcuts must name the package); the project checker fails if the two
  copies drift apart. "Rate QVoice" always opens QVoice's Play page, also
  from a test build.
- Fixed: three deprecation warnings in the unit tests' compile
  (`Locale("en", "IN")`, deprecated since Java 19); the project checker now
  catches those constructors, which the authoring sandbox's compile can't
  see.
- The release guide tests the Play-signed build on your own phone through
  Play's internal testing (available in minutes) before the closed test.
- **The source is public** at https://github.com/cyber-cyper/qvoice, and
  every push is built there with the real Android tools. The first run was
  green throughout: unit tests, the debug APK, the R8-shrunk release build
  and lint, which is now a gate (errors fail the build).
- **The GPL components' sources** (eSpeak NG, piper-phonemize, sherpa-onnx
  1.13.8) get mirrored into the repository's release `gpl-sources-1.13.8`
  by a workflow that checks them against the hashes sherpa-onnx pins, as
  GPL-3.0 asks for as long as the app is distributed (NOTICE.md). One
  click from the owner starts it (Actions → GPL sources → Run workflow).

## 1.0.0 — slice 19: read copied text — 2026-10-05 (versionCode 1)

- **"Read copied text"**: copy text anywhere, long-press the QVoice icon and
  tap **Read copied text** (or drag it to the home screen, where it is
  called "Clipboard"). The reader opens and reads it at once. Android shows
  its usual "QVoice pasted from your clipboard" notice; the clipboard is
  read only then, and when you tap Paste.
- **Copied passwords stay private:** a clip the copying app marked as
  sensitive (password managers and one-time codes do) isn't read out; the
  reader says why and offers **Read it anyway**.
- Nothing on the clipboard: the reader says so instead of opening empty.
- The Paste button no longer opens a copied file's content on the main
  thread (it only takes text, or the text of copied web content).
- Tests: 229 (was 226), the "never read a sensitive clip" rule proven by
  mutation.

## 1.0.0 — slice 18: continue listening — 2026-10-05 (versionCode 1)

- **The reader remembers the last text and your place in it**, so after
  QVoice was closed, swiped away, killed in the background or the phone
  restarted, it is still there, at the paragraph where you stopped. Home's
  Read aloud card says **Continue listening** and starts reading from there
  with one tap (**Start listening** for a text not begun, **Listen again**
  for a finished one).
- Kept only on this phone, in QVoice's private storage, never in a backup;
  only the latest text. A new text replaces it; **Read something else**
  deletes it, and the paste field then offers **Back to the previous text**
  until something new is read, in case of a mistaken tap.
- **The reader opens at its place**, not at the top of the text (it used to
  start at the top unless it was reading).
- **A share QVoice can't read no longer wipes the reader:** a web address
  alone or a file that isn't text now shows its explanation above the text
  being read (with Dismiss), and the reading goes on.
- The privacy policy and About's privacy note say what is remembered and
  how to delete it.
- Tests: 226 (was 210), 8 new guards proven by mutation.

## 1.0.0 — slice 17: contact, privacy policy, feedback — 2026-10-05 (versionCode 1)

- **About** gained **Send feedback** (a new email to support@zinijo.com in
  your mail app, with the app and Android versions, the phone model and the
  preferred engine filled in below your message, never any text QVoice has
  read; you see all of it before sending), **Rate QVoice** (QVoice's page
  in the Play Store app) and **Read the privacy policy** (Play asks for the
  policy inside the app as well as on the listing).
- **The privacy policy page** is final: `store/privacy.html` (was
  `store/privacy-policy.html`), for https://zinijo.com/qvoice/privacy.html,
  next to QTune's. Published by Riniso, effective 5 October 2026, with what
  Play asks a policy to contain: developer and contact, data handled,
  retention and deletion, security, children, changes.
- **The source address is set:** https://github.com/cyber-cyper/qvoice
  (`qvoice.sourceUrl`), so release builds are no longer refused and About
  shows it. The GitHub build's release check uses it instead of a
  placeholder.
- The listing, the release guide and the README use the real addresses;
  the release guide gained the privacy page upload, the app signing choice
  and the advertising ID answer. `docs/PRIVACY.md` now holds the facts
  behind the policy and the Data safety answers rather than a second copy
  of the policy.
- Tests: 210 (was 205); the feedback details' engine naming proven by
  mutation.

## 1.0.0 — slice 16: the reader takes text files — 2026-10-05 (versionCode 1)

- **Share a text file to QVoice** (a .txt from Files, a mail attachment, a
  notes app's export) and the reader reads it. Until now only shared text
  was read, and a shared file opened an empty reader. The file's own text
  is read, not the line some apps send along with it (often just the file
  name); UTF-8 and the UTF-16 Windows Notepad saves as "Unicode" are both
  understood, and a file longer than the reader takes is cut with the usual
  note.
- A file that isn't text (a PDF or a picture sent as text) or can't be
  opened brings up the paste field with a short explanation instead of
  reading noise.
- **Home's Read aloud card shows what the reader holds:** its opening words,
  where it is ("Reading · paragraph 3 of 12", "Paused …", "Finished") and
  **Back to the reader**. With nothing loaded it stays as it was.
- The privacy policy now names shared text files too; like all shared text
  they are only held in memory, never stored.
- Tests: 205 (was 199), 4 new guards proven by mutation.

## 1.0.0 — slice 15: shortcuts, big screens, the release guide — 2026-10-05 (versionCode 1)

- **Launcher shortcuts:** long-press the QVoice icon for **Read aloud**
  (the reader, with what it is reading or the paste field) and **Voices**
  (the voice library). Their icons are the app's indigo on its light tint.
- **Tablets, foldables and landscape:** content now stops at a readable
  width (640 dp, about 70 characters a line) and stays centred, instead of
  stretching cards and the reader's lines across the whole screen. Phones in
  portrait look exactly as before.
- **Fixed in landscape:** with three-button navigation on the side, the
  edge of the content could sit under the navigation bar (the side margins
  ignored it); every screen now keeps clear of side system bars.
- **`store/RELEASE.md`:** the first Play upload step by step: publishing the
  source (or letting me do it), the upload key and signed bundle in Android
  Studio, Play Console in order, the 12-tester closed test, production.
- **`docs/TESTING.md`** opens with the exact PowerShell commands for
  testing any new zip; the README uses the same.
- Tests: 199 (was 196), 2 new guards proven by mutation. The Compose stubs
  gained `BoxWithConstraints`, `calculateStartPadding`/`EndPadding` and
  `LocalLayoutDirection`, checked against the published API files.

## 1.0.0 — slice 14: headings for TalkBack, a release check on GitHub — 2026-09-30 (versionCode 1)

- **Headings for screen readers.** Every section title (Home's cards,
  "Language", "Voices", the voice library's sections and packs, About's
  sections) is now marked as a heading, so TalkBack users can jump from
  section to section (TalkBack's reading controls → Headings) instead of
  swiping through every item. Nothing changes on screen.
- **The GitHub build also makes a release build**, unsigned and never
  distributed: R8 shrinking, resource shrinking and lint's release checks
  run on every push, so a missing keep rule or a release-only lint error
  shows up there rather than when you generate the signed bundle for Play.
- Tests: 196 (unchanged). The Compose stubs gained `heading()`, checked
  against the published API file like the others.

## 1.0.0 — slice 13: the voice, right in the reader — 2026-09-30 (versionCode 1)

- **Voice button in the reader** (the speaking head at the top): it lists
  every installed voice for the language being read (all its accents: an
  English paragraph on an Indian phone can use any English voice), laid out
  as on Home, with each voice's speed on this phone and the one reading now
  selected. Pick another and the paragraph starts over in it, so you hear
  the difference at once.
- The choice is that language's default voice, the same setting as Home's
  "Make default", and the dialog says so: one voice per language for every
  app, rather than a second, reader-only setting to keep track of.
- When no installed voice reads the paragraph (a script no voice knows), the
  button explains that and offers the voice library.
- Fixed on the way: after a default voice changed, the reader kept using
  the previous one until the language changed. Android's TextToSpeech asks
  the engine for a language's default voice once, when the language is set,
  and then names that voice in every request; the reader now sets the
  language again after a change.
- Home's voice list and the reader share their pieces (initial, gender,
  speed label), and "Make default" lives in one place (QVoiceSettings).
- Tests: 196 (was 193), 4 new guards proven by mutation.

## 1.0.0 — slice 12: sleep timer — 2026-09-30 (versionCode 1)

- **Sleep timer in the reader:** the moon at the top offers 15, 30, 45 or 60
  minutes. When the time is up, the reader finishes the paragraph it is
  reading and pauses there (never mid-sentence), so Play later goes on with
  the next one. While it runs, the moon shows the time reading stops
  ("23:45", or "11:45 PM" as your phone shows times) and so does the
  notification ("Paragraph 3 of 12 · stops at 23:45"); the menu turns it off.
  After it stops, the paused session clears itself 10 minutes later, as any
  pause does: nothing is left on the lock screen by morning.
- The time runs whether reading or paused, like an alarm; a timer that ran
  out while paused doesn't stop the next Play.
- In the timer's last two minutes paragraphs are no longer queued ahead
  (otherwise the next one would start before the reader could stop), so
  there may be a short pause between paragraphs then.
- Tests: 193 (was 190), 6 new guards proven by mutation.

## 1.0.0 — slice 11: repository and build kit — 2026-09-30 (versionCode 1)

No change to the app itself; this prepares the public repository.

- **One script for every binary file:** `tools/get-binaries.ps1` puts the
  sherpa-onnx AAR, the built-in voice and the eSpeak NG data in place and
  checks each one's SHA-256, downloading only what is missing or wrong. It
  replaces `tools/get-bundled-voice.ps1` (which only handled the voice; the
  old copy in your folder can be deleted). The eSpeak NG data had no script
  at all until now: it is the `espeak-ng-data` folder of the voice's own
  sherpa-onnx archive, zipped, and the script checks the zip's content
  rather than its bytes, because zip tools compress differently (verified:
  the zip sent with slice 1 is that folder exactly).
- **Ready for git:** `.gitignore` keeps the ~110 MB of binary files, Python
  caches, `local.properties` and keystores out; `.gitattributes` keeps shell
  scripts in LF.
- **A GitHub build** (`.github/workflows/build.yml`): every push and pull
  request is built with the real Android tools (unit tests, the Compose
  compiler, a debug APK to download, lint reported but not yet enforced),
  with the binary files fetched by the same script and cached.
- README: "Binary files" rewritten around the script, and "Publishing the
  source": the steps to create the repository and push.
- Checked in the sandbox: the script on project copies with PowerShell 7.4
  (everything missing, everything present, a truncated eSpeak NG zip, the
  old voice zip, a damaged old zip and a wrong download checksum refused
  with nothing changed, extra files removed); the zip it makes read by
  `java.util.zip` like the app does, same 355 files; the workflow with
  actionlint and every action input against the action's own definition.

## 1.0.0 — slice 10: listening with the screen off — 2026-09-30 (versionCode 1)

- **Reading goes on with the screen off or in another app.** Lock the phone,
  go back to the app the text came from, or switch to anything else: the
  reader keeps reading. The screen no longer stays on while it reads; your
  usual screen timeout applies.
- **A media notification and lock screen controls:** previous paragraph,
  play/pause, next paragraph and Stop, in the notification, in Quick
  Settings' player and on the lock screen. It shows "Read aloud" and where
  the reader is ("Paragraph 3 of 12", or why it paused), never any of the
  text: a lock screen is public, and the text may be a private message.
  Tapping it opens the reader.
- **Headset and Bluetooth buttons** play, pause and skip (a double press on a
  one-button headset skips to the next paragraph on most phones), like in
  any audio app.
- **Closing the reader while it reads** leaves the reading going (the
  notification stops it); closing it while paused ends the listening
  session. A pause keeps the controls for 10 minutes, so Play on the lock
  screen or the headset picks up where you left off; after that the session
  ends by itself (the text stays in the reader). Swiping QVoice away in
  Recents does the same: reading goes on, a pause ends.
- **One reader.** The reader now opens in its own task, so the notification
  and Home always bring back the same reader, and leaving it for the app the
  text came from no longer leaves it stacked on top of that app (it has its
  own card in Recents instead).
- The phone stays awake while the reader reads, so slow voices and high
  speeds (where the voice computes more slowly than the audio plays) don't
  stall with the screen off; released at every pause.
- New permissions, all without a prompt: foreground service (media
  playback) and wake lock. Play Console needs a foreground-service
  declaration at release; the text for it is in store/LISTING.md.
- Tests: 190 (was 186), 6 new guards proven by mutation. The notification
  and media session are a mirror of the reader state (NowPlaying, unit
  tested); the service itself is compile-checked against the Android API.

## 1.0.0 — slice 9: read aloud — 2026-09-30 (versionCode 1)

- **"Read aloud" in every app.** Select text anywhere (a browser, a chat, an
  e-mail) and choose **Read aloud** in the selection menu, or share text to
  QVoice: a reader opens and reads it. Home has a card that opens the reader
  with a paste field, for text copied earlier.
- **The reader** shows the text in paragraphs, highlights the one being read
  and keeps it in view; tap any paragraph to read from there. At the bottom:
  play/pause, previous/next paragraph, the position ("3 of 12") and the
  speed (0.75× to 3×, remembered). Long paragraphs are divided at sentence
  ends; a whole book shared at once is cut at 100,000 characters (with a
  note). A shared web address alone (Chrome shares pages that way) gets a
  friendly hint instead of a read-out URL.
- **Each paragraph in its own language.** A Hindi paragraph in an English
  text is read by an installed Hindi voice; text no installed voice can read
  stops the reader with the reason, a link to Voices and a Skip button.
- **Behaves like an audio app:** pauses for phone calls and other apps'
  sound (and resumes after a call), pauses when headphones are unplugged, and
  follows the media volume.
- **TalkBack-friendly:** with a screen reader on, the reader speaks short
  pieces one at a time, because Android's TTS service makes every other
  app — TalkBack included — wait while one utterance plays.
- For now reading needs the reader on screen (the screen stays on while it
  reads); listening with the screen off, with a media notification and lock
  screen controls, is the next slice (done in slice 10).
- The privacy policy says what happens to shared text (read on the phone,
  never stored) and that the clipboard is read only on Paste.
- Tests: 186 (was 165), 6 new guards proven by mutation. TextChunker gained
  `group()` (whole sentences, no fast-start cut) for the reader.

## 1.0.0 — slice 8: fast speech and pitch — 2026-09-30 (versionCode 1)

- **Speech rates up to 6×, and clearer when fast.** Until now the rate went
  to the voice model alone, capped at 3×. Past about 1.5× neural voices blur
  syllables, and they stop getting faster: asked for 3×, the Piper voices
  gave 2× and the built-in voice 2.6×. Now the model speaks up to 1.5× and a
  pitch-preserving time-stretch (Sonic, an algorithm made for fast TTS
  listening) does the rest, so the rate you ask for is the rate you get.
  Measured with Whisper as the listener on ten standard test sentences, the
  built-in voice at 2.5× went from 29% of words misheard to 14%, and at 3×
  from 54% to 30% (docs/ARCHITECTURE.md, D-041). Screen-reader users who
  listen at 2-4× gain the most.
- **Pitch works.** Pitch requests (Android's and TalkBack's pitch settings,
  apps that change pitch) used to be ignored; they now move the voice's pitch
  without changing its speed.
- **Try it** has a pitch slider, the speed slider goes to 3×, and TalkBack
  reads both as "Speed 1.25x" rather than a percentage.
- The model still does the first 1.5× itself, so fast speech stays cheap to
  make: by its measured speed, the built-in voice should keep up at 2× on
  the Galaxy M31.
- The per-utterance log line adds "speed 3.00x (model 1.50x, stretch
  2.00x), pitch 1.00x" when either isn't normal.
- Sonic (Apache-2.0, Bill Cox) is credited on the About screen and in
  NOTICE.md; the vendored copy notes its two changes (overshooting samples
  are clipped instead of wrapping into clicks; a debug print removed).
- Tests: 165 (was 154), 4 new guards proven by mutation.

## 1.0.0 — slice 7: storage diet — 2026-09-30 (versionCode 1)

- **The built-in voice is read straight from the APK.** Until now QVoice
  unpacked its built-in voice (60 MB) into its own storage on first run — a
  second copy of bytes the APK already holds. sherpa-onnx now reads the
  model, speaker table and tokens from the APK through Android's
  AssetManager, the way sherpa-onnx's own Android engine does. **About 55 MB
  less storage on every phone**, and a shorter wait before the very first
  word after install: only eSpeak NG's pronunciation data (19 MB) is still
  copied, because espeak-ng opens its files by path.
- Updating cleans up: the copy earlier versions made is deleted at the first
  start (the log says how much was freed).
- The model files are stored uncompressed in the APK, so each load copies
  them straight out of the memory-mapped APK instead of first inflating
  57 MB; start-up logs that this is so. The APK grows by 5 MB (the zip it
  replaces was 55 MB); the Play download size stays the same.
- **Nets for a crash sherpa-onnx can't catch.** Reading from the APK,
  sherpa-onnx ends the process if a file is missing and has no error
  handling for a damaged model. So QVoice checks every file exists before
  the native load (a missing one fails that one request, cleanly), the build
  refuses to run with a missing or truncated voice file, and a unit test
  checks the files match what the code asks for.
- The build also refuses to run while the old voice zips are still in
  `assets/` (they would add 55 MB to the APK, unused).
  `tools/get-bundled-voice.ps1` now puts the four plain files in place —
  from the old zip when it's there (no download), otherwise from GitHub —
  checks each file's SHA-256, and deletes the old zips.
- Built-in voices are listed first, then downloads (as before in practice:
  "Kitten" sorted first by name).
- **Small UI fixes** found by the new screen check (below): with the keyboard
  open, Home no longer leaves an extra navigation-bar-high gap above it (and
  the activity now asks for `adjustResize`, which keyboard insets need on
  some phones); opening the voice library from Android's TTS settings no
  longer draws Home for a frame first.
- **The screens are now compiled before packaging too**, against stubs of
  the real Compose API made from its published signature files (sandbox
  only; `tools/sandbox/compose-stubs`). Slices 4-6 passed it unchanged.
- Tests: 154 (was 147), 8 new guards proven by mutation; the install tests
  no longer leave temporary folders behind on the build machine.

## 1.0.0 — slice 6: premium look and first-run setup — 2026-09-29 (versionCode 1)

- **First-run setup card.** Until QVoice is the phone's preferred engine,
  Home opens with a card in the logo's colours: "Make QVoice your phone's
  voice", what it does, the one step ("tap Preferred engine and choose QVoice
  Text-to-Speech") and the button. That card is the whole first-run guide —
  no carousel to swipe through. Once QVoice is the engine it becomes a small
  confirmation with a link to the settings.
- **Voice list redesigned.** Each voice: its initial in a colour for female /
  male, the name with a Default badge, gender · language · pack, and its
  speed on this phone ("Fast on this phone"). The card itself is the
  selection; "Make default" appears only on the selected voice, so the list
  stays calm. Speeds refresh after each utterance.
- Store listing: how to take the phone screenshots.

## 1.0.0 — slice 5: speed on this phone — 2026-09-29 (versionCode 1)

The 0.3.0 log showed Kokoro at a quarter of real time on the Galaxy M31
(pausing after every sentence) while Piper ran at 2.8× and the built-in
voice at 1.5×. QVoice now knows each voice's speed on the phone it runs on.

- **Measured speed per voice.** Every utterance of 1.5 s or more updates that
  voice's speed on this phone (smoothed, stored on the phone only). If the
  built-in voice hasn't spoken yet, opening the voice library measures it
  silently once (about 4 s of work in the background).
- **Voice library shows it:** "Fast on this phone · 2.8× real time", "Keeps up
  on this phone", or "Slow on this phone … pauses between sentences" —
  measured once a voice has spoken here, "Expected to …" before download
  (the built-in voice's speed × the catalogue's relative speed, measured on
  the M31).
- **Asks before a slow download:** "Slow on this phone — Kokoro is expected to
  run at about 0.3× real time… Download anyway?" (asked before the licence
  question; never for updates).
- **The automatic default voice keeps up.** For each language QVoice now
  prefers a voice that keeps up on this phone over a higher-quality one that
  doesn't; a slow voice still speaks when it is the only one, and a voice
  you chose with "Make default" always wins. On the M31, installing Kokoro
  no longer makes every app stutter.
- Catalogue version 2: each voice carries its relative speed (Piper 1.8,
  Kokoro 0.17, Supertonic 0.45 — the last estimated; it will be replaced by
  the phone's own measurement once used).

## 1.0.0 — slice 4: release basics and the new logo — 2026-09-29 (versionCode 1)

- **New logo** (the owner's artwork) as a proper adaptive launcher icon: the
  glowing Q, speaker and waves lifted off their background onto a matching
  navy gradient, sized for every launcher mask (circle, squircle, rounded
  square), plus a monochrome layer for Android 13+ themed icons. The logo
  also appears in the app bar and on the About screen.
- **Brand colours in the app**, from the logo (indigo, violet, cyan, deep
  navy), light and dark, on every Android version (the wallpaper-based
  colours of Android 12+ are no longer used). Every text colour meets WCAG
  AA contrast.
- **Version 1.0.0, versionCode 1** for the first Play upload; it stays 1
  until the owner says otherwise. Debug builds carry the slice ("1.0.0-slice06").
- **Names:** the launcher keeps the short "QVoice"; Android's text-to-speech
  settings now list the engine as "QVoice Text-to-Speech". The Play listing
  title is "QVoice Text To Speech Offline".
- **Play Store kit** (`store/`): 512 px icon, 1024 × 500 feature graphic,
  the listing text (short and full description, release notes, category,
  content rating, target audience, Data safety answers) and a publishable
  privacy policy page. All graphics are generated from the logo by
  `tools/brand/make_brand_assets.py`.
- Checked: all native libraries are 16 KB page aligned (Play's requirement
  for apps targeting Android 15+); edge-to-edge and predictive back were
  already in place.
- Static checker: strings used by the manifest and XML resources count as
  used, and a missing one is an error.

## 0.3.0-slice03 — 2026-09-29 (versionCode 5)

Smooth speech on real phones. The 0.2.2 log from the Galaxy M31 showed the
built-in voice generating at ~0.8x real time (a 5-second silence after
"Hello!" in the sample, and Stop waiting for the sentence in progress) while
Kristin ran at ~5x real time but still paused before long sentences.

- **Built-in voice: the full-precision build of Kitten nano** (same 8
  voices). The int8 build's compressed layers run slowly on processors
  without int8 dot-product instructions, the M31's included; the fp32 build
  is 2.7x faster in the same test. APK +31 MB. The voice's id is now
  `kitten-nano-en` (names like `kitten-nano-en#rosie`); names saved with the
  old id (`kitten-nano-en-v0_8-int8#…`) still work, and the old copy on the
  phone is deleted at start-up. **One-time step:** run
  `tools/get-bundled-voice.ps1` to fetch the new voice file (see TESTING.md).
- **Generation runs ahead of playback.** The model now generates on its own
  thread while the framework plays; before, Android's half-second playback
  buffer paced generation, so every sentence that took longer than that to
  generate left a gap. Expected on the M31: no gaps with Kristin, one short
  pause after "Hello!" with the built-in voice.
- **Faster first sound, faster Stop.** Every voice family is now fed in
  chunks: the first two sentences one at a time, a long opening sentence cut
  at its first comma, and no single chunk over 150 characters (220 for
  Supertonic). A Stop takes effect after the chunk being generated.
- **Real speed in the log and on screen.** Each utterance logs how long the
  model was ready after, the first audio, and "generated X ms of audio in Y
  ms (Z× real time, N threads)" — generating time only; 0.2.2's "total"
  included playback and made Piper look five times slower than it is — plus,
  after a Stop, how long it took. "Try it" shows the same speed after
  speaking.
- **CPU threads** (under Try it): Automatic, or 1-4 (6 and 8 on phones with
  that many cores). Automatic now never uses more threads than the phone
  has fast cores (2 on the common 2 fast + 6 slow design; the M31 keeps 4).
  A change applies from the next Speak (the voice reloads once). Stored per
  phone, not backed up.
- The log starts with one line naming the processor layout and the
  automatic thread count.
- Static checker rule 11: the engine may call a model's generate() only
  inside ReadAhead. 136 unit tests (25 new).

## 0.2.2-slice02 — 2026-09-29 (versionCode 4)

- **Crash on the first "Speak" (found on the Galaxy M31 with 0.2.1).**
  sherpa-onnx's native code finds the audio callback's method by its JNI
  signature, `invoke([F)Ljava/lang/Integer;`. QVoice passed a Kotlin lambda;
  Kotlin 2 compiles lambdas with invokedynamic and D8 turns them into classes
  that only have the erased `invoke(Object)`, so the lookup failed, a
  NoSuchMethodError was left pending and ART aborted the app. The callback is
  now a real class (`SherpaChunkCallback`) with exactly that method; a unit
  test checks the signature, R8 keeps it, and tools/check_project.py rejects
  a lambda passed to sherpa-onnx's generate calls.
- Confirmed on the phone with 0.2.1: the app opens (the crash-on-open fix
  works) and the bundled voice's model loads.
- The install log line now says how long the install took
  ("Install <voice>: INSTALLED in N ms"), so a pasted log answers it.

## 0.2.1-slice02 — 2026-09-29 (versionCode 3)

Fixes from the first builds on a phone (Samsung Galaxy M31, Android 12).

- **Crash on open (slice 1 and 2).** Android's TextToSpeechService.onCreate()
  calls onLoadLanguage() for the default language before returning, and
  QVoice's engine only set up its object graph after super.onCreate(). The
  app's own screen binds the engine as it opens, so the app died at once.
  The graph is now lazy and the engine prepares its state before calling
  super.onCreate(). tools/check_project.py rejects `lateinit` in the engine
  class so this can't come back.
- **Build failure (slice 2): "Invalid unicode escape sequence".** Four new
  strings had unescaped apostrophes (the editing script swallowed the
  backslashes). Fixed, and tools/check_project.py now checks strings the way
  the Android resource compiler does (apostrophes, \u escapes, leading @/?).
- **Android Studio sync failure.** Without a wrapper file Studio created one
  with Gradle 9.3.0, which AGP 9.2.1 rejects. The project now includes
  gradle/wrapper/gradle-wrapper.properties pinned to 9.4.1 (command-line
  builds with the standalone Gradle are unaffected).
- Start-up steps in the background each catch their own failure, so one bad
  step can't kill the app; the engine no longer waits for the voice scan on
  the main thread while it is being created.
- A failed install now always drops its download (before, an Error — not an
  Exception — would have made every refresh retry it).
- The four Apache Commons jars each carry META-INF/versions/9/module-info.class;
  excluded from packaging so the APK merge doesn't fail on the duplicates.
- No more Locale-constructor deprecation warnings (one suppressed helper).

## 0.2.0-slice02 — 2026-09-28 (versionCode 2)

Download more voices: a voice library with seven validated voice packs,
including Hindi and 30 other languages.

- **Voice library screen** ("Get more voices" on Home; also opened by
  "Install voice data" in Android's TTS settings): voices on the phone and
  voices to download, with languages, number of voices, download and
  installed size and licence; progress while downloading and installing;
  Update, Delete, Cancel, Try again.
- **Catalogue** (`assets/catalog.json`, generated from the real archives by
  `tools/catalog/make_catalog.py`): Kokoro v1.0 (48 voices; English US/UK,
  Hindi, Spanish, French, Italian, Portuguese, Chinese), Supertonic 3 (10
  voices, 31 languages), and five fast Piper English voices (LJ, Kristin,
  Norman, John, Cori). Every voice checked for licence, intelligibility
  (speech-recognition round trip per language and speaker) and pitch; see
  docs/CATALOGUE.md for results and for what was left out and why.
- Downloads through Android's DownloadManager (resumes, notification
  progress); over 50 MB asks before using mobile data; checks free space
  first.
- Installs verify size and SHA-256 before unpacking, never create links from
  an archive, and swap the folder in atomically: a failed update keeps the
  old voice. Runs as a background job, so leaving the app doesn't stop it.
- Supertonic's licence (OpenRAIL-M) is shown and accepted before download.
- **Multi-language voices**: per-speaker languages, per-language loading for
  Kokoro (fixes: British English needs espeak voice "en"; Chinese number
  rules only for Chinese — they read English digits in Chinese), language
  per request for Supertonic with sentence chunking so speech starts fast.
- Engine memory budget (a sixth of RAM, 256-768 MB): big models are
  unloaded before another loads, instead of piling up (two Kokoro languages
  would be ~740 MB).
- Home: voices grouped by language (chips), "Make default" per language and
  region.
- Sample sentences for all 32 catalogue languages.
- Permissions: INTERNET and ACCESS_NETWORK_STATE (voice downloads only).
- New dependency: Apache Commons Compress 1.27.1 (bzip2/tar).
- Tests: 109 unit tests (53 in slice 1), all real catalogue archives
  installed with the app's installer in the sandbox.

## 0.1.0-slice01 — 2026-09-28 (versionCode 1)

First slice: QVoice works as a system text-to-speech engine, offline, with a
built-in English voice.

- System TTS engine (`QVoiceTtsService`): language negotiation with the
  framework's ISO-639-2 codes, one framework voice per speaker with stable
  names (`<voiceId>#<speaker>`), default voice per language, streaming one
  sentence at a time, immediate stop, refusal (not gibberish) when asked to
  read a script no installed voice can read.
- Built-in voice: KittenTTS nano 0.8 int8, 8 speakers (Rosie, Bruno, Kiki,
  Hugo, Bella, Jasper, Luna, Leo), copied out of the APK on first use.
- Shared eSpeak NG data set for all voices.
- Engine model cache that never releases a model mid-synthesis; releases
  memory when idle or when Android asks.
- Android settings integration: CHECK_TTS_DATA, GET_SAMPLE_TEXT,
  INSTALL_TTS_DATA, settings cog.
- Home screen: engine status, "Try it" with speed and timing, voice list with
  samples and "Make default". About screen with GPL notice, source link and
  every component's licence.
- No permissions. Settings backed up; voices not.
- Release builds blocked until `qvoice.sourceUrl` is set (GPL-3.0).
- Tooling: `tools/check_project.py`; 53 unit tests.
- The three binary files (sherpa-onnx AAR, two bundled-voice zips) are
  delivered outside the source zip; the build names any that are missing.
