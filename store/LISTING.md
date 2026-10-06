# Google Play listing — QVoice

Everything Play Console asks for, ready to paste. Limits are Play's; counts
are checked. Contact: Riniso, support@zinijo.com. Source:
https://github.com/cyber-cyper/qvoice. Privacy policy:
https://zinijo.com/qvoice/privacy.html.

## Main store listing

**App name** (30 max): `QVoice Text To Speech Offline` — 29, as set by the owner.
(Play's metadata policy bans words like "best", "free", "#1" and emoji in the
title; this one is fine. The launcher shows the short name "QVoice".)

**Short description** (80 max, 72 used):

```
Natural-sounding voices that work offline in every app. Private, no ads.
```

**Full description** (4000 max, about 2,750 used):

```
QVoice gives your phone natural-sounding voices that work completely offline. Set it as your phone's text-to-speech engine and every app that reads aloud — screen readers, e-book and news readers, maps, translators, learning apps — speaks with a QVoice voice.

Why QVoice
• Works offline: speech is made on your phone, so it works anywhere, even without a connection.
• Private by design: your text never leaves your device. No account, no ads, no tracking, no analytics.
• Natural voices: modern neural voices instead of robotic ones.
• Ready at once: 8 English voices (4 female, 4 male) are built in — nothing to download to get started.
• Fast where it matters: speech starts in a fraction of a second, keeps up with long texts, and stops the moment you ask.
• Knows your phone: QVoice measures how fast each voice runs on your phone and tells you before you download one that would be too slow.
• Made for fast listening: speech rates up to 6× for screen-reader users, with the voice's natural pitch kept; pitch can be changed too.
• Read aloud anywhere: select text in any app and choose Read aloud, share text or a text file to QVoice, or copy text and tap "Read copied text" on QVoice's icon, and it reads it to you sentence by sentence, marking each one as it goes. Skip back a sentence, make the text bigger, keep listening with the screen off, with controls on the lock screen and on your headset, a sleep timer for bedtime listening, and it picks up where you stopped.

More voices to download (free)
• Fast English voices (US and UK) that suit any phone.
• Kokoro: very natural voices in English, Hindi, Spanish, French, Italian, Portuguese and Chinese (best on recent, faster phones).
• Supertonic: ten voices speaking 31 languages, including Hindi, Arabic, Japanese, Korean, Russian and most European languages (best on recent, faster phones).
Voices download from GitHub over Wi-Fi or, if you allow it, mobile data, and you can delete them at any time.

How to use it
1. Open QVoice and try the voices.
2. Tap "Open text-to-speech settings" and choose QVoice as the preferred engine.
3. That's it — apps that read aloud now use QVoice. Pick a default voice per language in QVoice.

Free and open source
QVoice is free software under the GNU General Public License v3. There are no ads and no in-app purchases. Source code: https://github.com/cyber-cyper/qvoice

Credits
Speech runtime: sherpa-onnx (Apache-2.0) with ONNX Runtime (MIT) and eSpeak NG (GPL-3.0); fast speech by Sonic (Apache-2.0). Voices: KittenTTS (Apache-2.0), Piper voices by Bryce Beattie (MIT, public-domain recordings), Kokoro by hexgrad (Apache-2.0), Supertonic by Supertone (OpenRAIL-M, accepted in the app before download).

Questions or ideas: support@zinijo.com
```

Claims check: every line above is true of the current build ("Read aloud
anywhere" since slice 9, with the screen off and lock screen and headset
controls since slice 10, the sleep timer since slice 12, text files since
slice 16, "picks up where you stopped" since slice 18, "Read copied text"
since slice 19, sentence by sentence and text size since slices 23-24). "Knows your
phone" ships in 1.0.0 (slice 5), "fast listening" in slice 8 (rates up to
6×; it says "natural pitch kept", not "clear at any speed": at 3×+ any voice
gets hard to follow for untrained listeners). Keep "31 languages" tied to
Supertonic — only it has them.

**App category:** Tools. (Alternatives: Productivity; Education only if the
learning-app angle becomes the main message.)

**Contact details:** email `support@zinijo.com`; website: zinijo.com if it
has (or will have) a page for QVoice, otherwise
`https://github.com/cyber-cyper/qvoice`; phone: leave empty.

## Graphics

| Asset | File | Play's rule |
|---|---|---|
| App icon | `store/play-icon-512.png` | 512 × 512, 32-bit PNG, ≤ 1 MB, full square (Play rounds the corners) |
| Feature graphic | `store/feature-graphic-1024x500.png` | 1024 × 500, JPEG or 24-bit PNG, no transparency |
| Phone screenshots | steps below | 2–8, 16:9 or 9:16, 320–3840 px per side; at least 4 at 1080 px or more to be eligible for Play's recommendations |

Both images are generated from `tools/brand/logo-master.png` by
`tools/brand/make_brand_assets.py`.

### Taking the screenshots

Suggested set, strongest first (the first ones are what most people see):
1) the reader reading an article (the highlighted paragraph), 2) Home with
the setup card, 3) the voice list with speeds, 4) the lock screen with the
QVoice player, 5) the voice library, 6) the same Home in dark mode. Google's
guidance: show the real app, keep any caption text to a small part of the
image (under about 20%), no "best" or "#1", nothing that dates quickly.
Phone screenshots of the M31 (1080 × 2400) meet the size rule as they are. Phone connected, QVoice open on the screen you want, then
in PowerShell (a direct `>` redirect corrupts PNGs in Windows PowerShell,
hence the pull):

```powershell
$adb = "C:\Users\Public\sdk\platform-tools\adb.exe"
& $adb shell screencap -p /sdcard/qv-1.png
& $adb pull /sdcard/qv-1.png "$env:USERPROFILE\Desktop\"
```

Repeat with qv-2, qv-3… Plain screenshots are allowed as they are; send
them to me and I'll add a short caption band in the logo's style (same size
rules), which usually converts better.

## App content (Policy → App content)

- **Privacy policy:** `https://zinijo.com/qvoice/privacy.html` (upload
  `store/privacy.html` there first). Required for every app, even one that
  collects nothing; QVoice also links it from About, as Play requires.
- **Ads:** No, the app does not contain ads.
- **App access:** All functionality is available without special access
  (no login).
- **Content rating** (IARC questionnaire): category "Utility, Productivity,
  Communication, or Other"; answer No to violence, sexuality, language,
  controlled substances, gambling, user interaction/sharing, location
  sharing and digital purchases. Expected: Everyone / PEGI 3.
- **Target audience:** 13 and over (13–15, 16–17, 18+). QVoice isn't made
  for children; choosing under-13 ages would put it under the Families
  policy and teacher-approved review for no benefit. (QBrain being a kids'
  app doesn't change this: QVoice is a separate utility.)
- **Data safety:** "Does your app collect or share any of the required user
  data types?" → **No**. Encryption in transit / deletion requests: not
  applicable. Reason: text is processed on the phone; voice downloads are
  fetched from GitHub at the user's request with no user data sent.
- **Advertising ID:** No, the app doesn't use an advertising ID.
- **Foreground service permissions** (asked because QVoice targets Android
  14+ and declares `FOREGROUND_SERVICE_MEDIA_PLAYBACK`): tick **Media
  playback** and paste the two texts below. The video: a ~30 s screen
  recording (the phone's own screen recorder is fine), uploaded unlisted to
  YouTube or as a Google Drive link anyone can view, showing: text selected
  in Chrome → Read aloud → the reader reading → Home button and screen off
  (it goes on reading) → the lock screen player → pause and Stop in the
  notification.

  Feature description:

  ```
  Read aloud: QVoice reads text the user selected or shared aloud with its own offline voices. The media playback foreground service keeps the reading going when the user turns the screen off or switches to another app, and shows the media notification with play/pause, previous/next paragraph and stop, also on the lock screen and for headset buttons. It starts only when the user presses play and ends when the user stops, closes the reader while paused, or 10 minutes after a pause.
  ```

  Impact if the task is deferred or interrupted:

  ```
  Reading aloud is audio the user is listening to at that moment. If the service were deferred or interrupted, the reading would stop in the middle of the text as soon as the screen turned off or the user opened another app, and the lock screen and headset controls would stop working.
  ```
- **Government app / Financial features / Health / News:** No / None / No / No.

## Release notes for 1.0.0 (500 max)

```
First release. Offline text to speech with 8 built-in English voices, fast English voices to download, and Kokoro and Supertonic voices in Hindi and 30 other languages. Read aloud: select, share or copy text in any app and QVoice reads it to you, even with the screen off, and picks up where you stopped. QVoice checks each voice's speed on your phone before you download it and speaks at up to 6× for fast listening. No ads, no tracking.
```

## Store listing translations

Play Console → Grow users → Translations offers two free machine
translations (checked October 2026):

- **Store listing** (title, short and full description): 100+ languages,
  Hindi and Tamil included. Listings that aren't translated are already
  shown to other languages through Google Translate.
- **App strings** (the app's own screens, added to the bundle by Play):
  Gemini, free, but only 10 languages (Arabic, French, German, Indonesian,
  Italian, Japanese, Portuguese (Brazil), Spanish (Latin America and Spain),
  Thai). Not Hindi or Tamil.

Recommendation: translate the listing only into languages QVoice has
voices for, and say in it that the app's screens are in English. A Tamil
listing would bring Tamil readers to an app with no Tamil voice, and Hindi
voices are still slow on mid-range phones; both wait for fast Indian
voices (docs/BACKLOG.md). Keep the name "QVoice" untranslated.

## Release notes for 1.0.1 (500 max)

```
The reader now follows the voice sentence by sentence: the sentence being read is marked, and Previous/Next skip one sentence. Text size in the reader's menu. Help is built in (tap ? on the home screen), QVoice warns when battery saving would stop it reading with the screen off, and shows "Preparing the voice…" while a voice gets ready.
```

## Before the first upload

- Source pushed to https://github.com/cyber-cyper/qvoice (`qvoice.sourceUrl`
  in `gradle.properties` is set; release builds are refused without it:
  GPL-3.0 needs the source offer). See store/RELEASE.md, step 1.
- `store/privacy.html` uploaded to https://zinijo.com/qvoice/privacy.html.
- Personal developer accounts created after 13 November 2023 must run a
  closed test with at least 12 testers opted in for 14 days in a row before
  they can apply for production (confirmed in Play Console Help, October
  2026).
- Upload an Android App Bundle (Build → Generate Signed App Bundle), with
  Play App Signing.
- Fill in the foreground service declaration (App content, above) before
  the first upload that includes slice 10; Play rejects the bundle without it.
