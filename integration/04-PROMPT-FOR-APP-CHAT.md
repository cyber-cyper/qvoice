# Prompt to paste into each app's chat

Attach **QVoice-Integration-Handover.zip** and the app's **latest source
zip**, then paste the text below. Replace `[APP]` with the app's name:
"LifeCue", "QTune: Quote Ringtones" or "QTune: Tamil Wisdom".

---

I'm adding QVoice, my offline text-to-speech engine app (com.riniso.qvoice,
on Google Play), to [APP], so that it can use QVoice's voices. The attached
QVoice-Integration-Handover.zip has everything about QVoice and the shared
helper RinisoSpeech. Please work through it in three steps.

**Step 1: read.** Read the handover zip:
- README.md
- 01-QVOICE-FACTS.md
- 02-INTEGRATION-GUIDE.md
- the brief for [APP] in 03-APP-BRIEFS/
- the code in code/

Then read [APP]'s actual source: every place it uses TextToSpeech, its
speech/voice settings, its manifest, its CHANGELOG, its docs, and its
versionCode and versionName.

**Step 2: handover.** Write [APP]'s handover using
05-HANDOVER-BACK-TEMPLATE.md, filled in from the code (not from memory).
Show it to me. Stop there only if it lists something QVoice itself must
change. Otherwise continue.

**Step 3: integrate,** as one small slice with this project's usual rules:
1. Read the code before changing it.
2. Copy RinisoSpeech unchanged into com.riniso.speech, with its test.
3. Add the `<queries>` entry.
4. Route every TextToSpeech use through RinisoSpeech, as the brief says.
5. Add the "Voice engine" setting and the dismissible "Get QVoice" card.
6. Update CHANGELOG.md and docs/TESTING.md, and record the reasoning in
   code comments.
7. Raise the versionCode by exactly 1.
8. Verify:
   - brace balance;
   - XML validity;
   - every string resolving, in every language the app has;
   - a diff against the previous zip.
9. Package a zip and give me the complete PowerShell test block, starting
   from Expand-Archive of the zip in my Downloads folder.

Push back where the brief doesn't fit [APP]'s real code, and suggest
something better if you see it. Ask me only when a decision is genuinely
mine, with the options and your recommendation.

---

## When to come back to the QVoice chat

Bring an app's handover back to the QVoice chat only when the app needs
something from QVoice itself, for example:
- a deep link "download a voice for language X";
- a new voice or a language;
- a change to the shared helper (it changes there first, then gets copied to
  every app);
- a QVoice bug found while testing.
