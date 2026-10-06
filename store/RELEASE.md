# Releasing QVoice on Google Play, step by step

The path, in order. Steps 1 and 2 are done once; the closed test (step 5)
is the longest wait (14 days), so it pays to start it early. Every text to
paste is in `store/LISTING.md`. Play Console's labels change now and then;
if a button is named a little differently, the order still holds.

| Step | Who | Time |
|---|---|---|
| 1. Publish the source (GPL) | done: github.com/cyber-cyper/qvoice | — |
| 2. Upload key and signed bundle | you, in Android Studio | 10 minutes |
| 3. Play Console: the app, its listing, the forms | you | 1 hour |
| 4. Internal test: the Play build on your phone | you | 30 minutes |
| 5. Closed test: 12 testers, 14 days | you and your testers | 14 days |
| 6. Production | you | 1 day of review |

Version numbers (D-037): every upload to Play needs a versionCode Play
hasn't seen, so each one goes up by exactly 1, with the versionName's last
number. 1 (1.0.0) is the first upload, in closed testing since 6 October
2026; the next is 2 (1.0.1). The same bundle can move between tracks
(internal → closed → production) without a new upload or number. Tell me
when you upload, and I raise the number for the one after.

## 1. Publish the source (done)

The app contains eSpeak NG (GPL-3.0), so the source of every build on Play
must be public, and release builds refuse to run until `qvoice.sourceUrl`
names it (`app/build.gradle.kts`). Done in slice 20: the source is at
https://github.com/cyber-cyper/qvoice (`qvoice.sourceUrl`), and GitHub builds
and tests every push. I push every slice there (one commit per zip); you
keep extracting zips as usual.

**One click from you, once, before the first upload:** the copies of the
GPL components' sources (eSpeak NG, piper-phonemize, sherpa-onnx 1.13.8)
that must stay available next to the app. This session may push code but
not create releases, so the release is yours to start:

1. github.com/cyber-cyper/qvoice → **Actions** → **GPL sources** (left).
2. **Run workflow** → **Run workflow** (branch main).
3. After about a minute: **Releases** (right side of the repository's main
   page) shows "Sources of the GPL components (sherpa-onnx 1.13.8)" with
   four files.

If GitHub ever refuses Claude's pushes, the Claude GitHub App needs
access: https://github.com/apps/claude/installations/select_target →
**cyber-cyper** → **Only select repositories** → **qvoice** → **Install**.

**Or yourself, with GitHub Desktop** (no command line). Do this in a fresh
copy of the latest zip, not in the folder you build in: unpacking zips over
a folder leaves files that later slices deleted, and they would be
published too.

1. Install GitHub Desktop (desktop.github.com) and sign in as `cyber-cyper`.
2. Unpack the latest zip to a new folder, e.g. `C:\Users\Public\qvoice-git`.
3. **File → Add local repository** → choose `C:\Users\Public\qvoice-git\QVoice`
   → when it says this isn't a repository, click **create a repository** →
   leave README, Git ignore and License at None → **Create repository**.
   (QVoice's own `.gitignore` keeps the binary files, `local.properties`,
   build output and keystores out.)
4. **Repository → Repository settings → Remote**: set it to
   `https://github.com/cyber-cyper/qvoice.git` → **Save**.
5. **Publish branch** (top bar).

## 2. Upload key and signed bundle (Android Studio)

The same way you build QTune and QBrain releases.

1. Android Studio → **File → Open** →
   `C:\Users\Public\QVoice-slice02-fix1-src\QVoice` → wait until the sync
   at the bottom finishes.
2. **Build → Generate Signed App Bundle or APK** → **Android App Bundle** →
   **Next**.
3. **Key store path → Create new**:
   - path: a folder **outside** the project, for example
     `C:\Users\Public\keys\qvoice-upload.jks` (the project is about to be
     public; a key inside it could end up on GitHub);
   - passwords: strong, and written down somewhere safe;
   - alias `upload`, validity `30` years, your name and organisation →
     **OK**.

   (Reusing the upload key of your other apps is allowed too; a key per app
   keeps a lost or leaked key from touching the others.)
4. **Next** → build variant **release** → **Create**.
5. The bundle is `app\release\app-release.aab` (Android Studio offers to
   show it). Keep the `.jks` file and its passwords backed up: Play's app
   signing key stays with Google, but every later upload needs this upload
   key (a lost one can be reset through Play support, which takes days).

## 3. Play Console: the app, its listing, the forms

1. play.google.com/console → **Create app**:
   - App name: `QVoice Text To Speech Offline`
   - Default language: English (United States)
   - App or game: **App**; Free or paid: **Free**
   - tick the two declarations → **Create app**.
2. **Grow users → Store presence → Main store listing**: short and full
   description, then **Graphics**: app icon `store/play-icon-512.png`,
   feature graphic `store/feature-graphic-1024x500.png`, 2 to 8 phone
   screenshots (how to take them: `store/LISTING.md`, "Taking the
   screenshots") → **Save**.
3. **Store settings**: category **Tools**, email `support@zinijo.com`
   → **Save**.
4. **Policy and programs → App content**: fill in each item from
   `store/LISTING.md` ("App content"): privacy policy URL, ads (none), app
   access (all available), content rating questionnaire, target audience
   (13+), data safety (no data collected), advertising ID (no),
   government apps, financial features, health, news, and **Foreground
   service permissions** (media playback, the two texts and the video
   link).

## 4. Internal test: the Play build on your own phone

The bundle Play delivers is the first release build (R8 shrinks and renames
code only there), so try exactly that before testers get it. Internal
testing has no review wait: the build is available within minutes.

1. **Test and release → Testing → Internal testing** → **Testers** →
   **Create email list** with your own Google account → **Save**.
2. **Create new release** → upload `app-release.aab` → for the app signing
   key choose **Let Google create and manage** (see "App signing" below) →
   release notes from `store/LISTING.md` → **Next** → **Save and publish**.
   If Play lists something still missing (a declaration under App
   content), fill it in and come back.
3. **Testers** tab → **Copy link** → open it on the phone → **Accept
   invitation** → install **QVoice** from Play. It sits next to "QVoice
   debug" (your test builds; the old test build must be uninstalled first,
   docs/TESTING.md, slice 20).
4. Ten minutes on the phone: Try it speaks; make QVoice (not QVoice debug)
   the preferred engine and let TalkBack or another app speak; the reader
   reads a shared text with the screen off and the lock screen controls
   work; Read copied text; download one small voice (Kristin, 64 MB) and
   speak with it. Anything wrong: the logcat commands in docs/TESTING.md
   (filter `QVoice`), and send them to me.
5. **Testing → Pre-launch report** (after an hour or so): Google's test
   phones' crashes and accessibility findings; send me anything it lists.

## 5. Closed test (12 testers, 14 days)

Personal developer accounts created after 13 November 2023 need at least 12
testers who stay opted in to a closed test for 14 days in a row before
production can be requested ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465)).

1. **Test and release → Testing → Closed testing** → **Create track** (or
   use the ready-made one) → **Testers** → **Create email list** → add at
   least 12 Google account emails (family, friends, your other apps'
   testers; a few spares help, since someone who opts out restarts their
   14 days) → **Save**.
2. **Internal testing → Releases** → the release → **Promote release →
   Closed testing** (the same bundle, no new upload) → **Save and
   publish** (or **Start rollout to Closed testing**).
3. Once the review passes (a few hours to a few days), copy the **opt-in
   link** from the track's Testers tab and send it to the testers. Each
   opens it, taps **Become a tester**, then installs QVoice from Play and
   keeps it installed.
4. What testers report goes into the production application (step 6),
   so ask them for a sentence or two on what they tried.

## 6. Production

After 14 days with 12 or more testers opted in: **Dashboard → Apply for
production** → answer the questions about the test (what was tested, what
feedback came in and what changed). Once approved: **Production → Create new
release** → **Add from library** (the same bundle) → **Next** → **Save and
publish**.

## The privacy policy page

Upload `store/privacy.html` to your site so that it opens at
**https://zinijo.com/qvoice/privacy.html**, the same way as QTune's
(zinijo.com/qtune/privacy.html). That address is already in the app (About →
Read the privacy policy) and goes into Play Console (step 3). Open it in a
browser once to check before you paste it into Play Console.

Why there rather than GitHub Pages: an address inside an installed app can't
be changed later, and a domain you own outlives any repository or account
name.

## App signing (step 4, when Play asks)

Choose **Let Google create and manage my app signing key** (Play App
Signing, the default). Google keeps the key that signs what users install;
you keep only the upload key from step 2, which Play support can reset if
it is ever lost. The one thing this rules out: APKs of the same app
signed by you (from GitHub, say) can't update a Play install. QVoice ships
only through Play, and the GPL needs the source, not APKs, so nothing is
lost.

## What I need from you for this

1. The privacy policy page uploaded (above).
2. A word when the bundle is uploaded (which zip it was built from).
3. Whether the Riniso developer account was created before or after
   13 November 2023 (after: the closed test in step 5 is required).
