# Roadmap: QVoice for every common language, and the apps around it

Written 26 November 2026. QVoice phases are numbered **Q**, app phases **A**.
Each phase is one or a few small slices, shipped as its own Play update
(versionCode +1).

## Where QVoice stands

- **31 languages** with Supertonic 3 (one 145 MB download, medium speed).
- **8 languages** with Kokoro (380 MB, slow to start).
- **Fast voices only in English** (built-in, Piper).
- **No Tamil, Malayalam or Telugu**; Hindi only through the two slow
  models.

## What exists to build on (checked 26 Nov 2026)

- **Piper**, the fast voice family QVoice already uses for English, has
  voices in **39 languages**:
  - Arabic, Catalan, Chinese, Czech, Danish, Dutch, English, Farsi, Finnish,
    French, Georgian, German, Greek;
  - **Hindi, Malayalam, Telugu**, Hungarian, Icelandic, Indonesian, Italian,
    Kazakh, Latvian, Luxembourgish, **Nepali**, Norwegian, Polish;
  - Portuguese, Romanian, Russian, Serbian, Slovak, Slovenian, Spanish,
    Swahili, Swedish, Turkish, Ukrainian, Vietnamese, Welsh.

  sherpa-onnx, QVoice's engine, runs them as they are. Each voice has its own
  licence (from its training data), so every one is checked before it goes
  in the catalogue, as QVoice's catalogue rules already require. Some are
  non-commercial and stay out.
- **Tamil:** no ready voice with a licence QVoice can ship. Meta's MMS
  voices cover Tamil but are non-commercial. The large Indian models
  (AI4Bharat's) are too big and slow for phones. The way to a Tamil voice
  of our own is training a Piper voice on a permissively licensed Tamil
  recording set (the IIT Madras IndicTTS data, licence to be confirmed in
  writing): about a GPU-day per voice.

## QVoice phases

| Phase | What | Why |
|---|---|---|
| **Q1** — helpers for the apps | QVoice opens its library on one language (`EXTRA_LANGUAGE`, the contract already in RinisoSpeech 1.1). It reports its chunks as `onRangeStart`, so apps following the voice get progress inside long sentences too. | The two QVoice-side items LifeCue's review asked for. Small. |
| **Q2** — a catalogue that can grow | The voice catalogue moves from inside the app to a JSON in QVoice's own GitHub releases, with hashes, checked like today. The library groups voices by language, with search, "voices for my language" at the top (the phone's languages), and size and speed on each. | With 40+ languages, a new voice must not need an app update, and a flat list of 100 voices isn't usable. The mirror also gives fast, stable downloads (already planned). |
| **Q3** — fast voices for India | Piper **Hindi, Malayalam, Telugu, Nepali** (after the licence check). | Fast Hindi replaces the slow-only Hindi. Malayalam and Telugu are new. It reverses October's "Indian languages to Google" for these languages only: there are now fast voices for them. **Your decision.** |
| **Q4** — fast voices for the world | Piper voices for the most-spoken languages first: Spanish, French, German, Portuguese, Arabic, Indonesian, Russian, Turkish, Vietnamese, Chinese, Italian, Polish, Dutch, Ukrainian. Then the rest of the 39. At least one female and one male voice where they exist. | Every common language gets a voice that starts at once (alarms, TalkBack), not only Supertonic's medium one. |
| **Q5** — store listing in those languages | Play Console's free listing translation, for each language that now has a fast voice. | The earlier rule: translate the listing only where QVoice really speaks the language. |
| **Q6** — Tamil (decide after Q3) | Train a Tamil Piper voice, female and male, if the dataset licence allows. Otherwise Tamil stays with Google. | QTune: Tamil Wisdom's main language. It's the biggest effort on this list, so it goes last and needs your go-ahead. |

Not planned: voice cloning (consent and abuse problems for a free app), and
cloud voices (they would break "offline, nothing leaves the phone").

## App phases

| Phase | App | What | Waits for |
|---|---|---|---|
| **A1** | LifeCue | QVoice live, the one voice choice, a voice per alarm, previews that match the alarm, language check for user cues (LifeCue Chunk A, versionCode 30) | RinisoSpeech 1.1.0 (done) |
| **A2** | QTune: Quote Ringtones | The integration plus ringtone rendering with QVoice voices | A1 tested on the phone (the helper's first field test) |
| **A3** | QTune: Tamil Wisdom | English via QVoice, Tamil via Google, the split per quote | A2 |
| **A4** | LifeCue | The alarm voice made in advance (Chunk B, BL-035) | its BL-507 phone checks |
| **A5** | all three | "Get a voice for this language" opens QVoice on that language; ranges from QVoice | Q1 |
| **A6** | all three | Nothing to build: Q3 and Q4 voices reach the apps by themselves (Automatic picks them up), so release notes only | Q3, Q4 |

**Order across both:** A1 → Q1 → A2 → Q2 → A3 → Q3 → Q4/A4 → Q5 → Q6.
Q1 is small and makes A2 and A3 better. Q2 must come before the big voice
additions.

## Decisions for you

1. **Q3: fast Hindi, Malayalam and Telugu voices in QVoice**, after a licence
   check. *Recommended: yes.* It changes October's decision only where fast
   voices now exist. Tamil stays with Google until Q6.
2. **Q2: move the catalogue online** (still checked by hashes, and still no
   data about the user). *Recommended: yes, before adding voices.* The
   alternative is an app update for every new voice.
3. **Q6: a Tamil voice of our own:** decide after Q3. *Recommended:* ask
   IIT Madras about the dataset licence now, because the answer takes time.
