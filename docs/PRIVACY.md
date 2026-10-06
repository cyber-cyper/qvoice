# QVoice and privacy: the facts behind the policy

The policy itself is `store/privacy.html`, published at
**https://zinijo.com/qvoice/privacy.html** (the address in the app's About
screen, `ui/Links.kt`, and in Play Console → App content → Privacy policy).
This file keeps the facts it rests on and the Data safety answers, so a
change to the app can be checked against both. When one of these facts
changes, update `store/privacy.html` (and its effective date), this file and
the About screen's privacy text together, then upload the page again.

## What the app does with data

| Data | Where it goes | Kept |
|---|---|---|
| Text other apps send the engine (TalkBack, readers, maps) | turned into audio in memory | never |
| Text in the reader (Read aloud, Share, .txt files, Paste, Read copied text) | shown and read on the phone | the last text and the place in it (paragraph, sentence) only, in `noBackupFilesDir/reader/` (`last.txt`, `place.txt`; slices 18, 24): replaced by the next text, deleted by "Read something else", clearing storage or uninstalling |
| Clipboard | read only on Paste or the Read copied text shortcut; the shortcut skips clips marked sensitive (slice 19) | as reader text |
| Notification, lock screen, media session | position only ("Paragraph 3 of 12") | while the session lasts |
| Settings (`qvoice_settings`) | the phone; Android backup if on | until cleared |
| Device-specific settings (`qvoice_device`: threads, measured speeds), download state (`qvoice_downloads`) | the phone, never backed up | until cleared |
| Voice downloads | from GitHub over HTTPS via DownloadManager; GitHub sees IP and request | until deleted |
| Feedback email | the user's mail app, shown before sending: versions, phone model, preferred engine, never reader text | at support, deleted on request |

Never: accounts, analytics, ads, advertising ID, crash reporting, logs of
spoken text, a server of our own. Backups contain `qvoice_settings.xml` only
(`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, opt-in
lists).

## Play Console: Data safety answers

- Does your app collect or share any of the required user data types?
  **No.** Play's definition: data processed only on the device and not sent
  off it is not "collected", so the reader's remembered text doesn't count.
  (With "No", Play shows "No data collected" and "No data shared with third
  parties".)
- Voice downloads are fetched from GitHub at the user's request, with no
  user data sent; Play's guidance treats a transfer the user starts and
  expects as neither collection nor sharing by the app.
- Is data encrypted in transit? Not applicable (no data collected); the
  downloads use HTTPS.
- Can users request deletion? Not applicable (nothing is collected).
- Advertising ID: **No** (no library adds the AD_ID permission; check the
  merged manifest after any new dependency).
