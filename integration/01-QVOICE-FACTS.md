# QVoice: the facts an app can rely on

Source: the QVoice repository (github.com/cyber-cyper/qvoice) at version
1.0.1 (versionCode 2). On Google Play:
https://play.google.com/store/apps/details?id=com.riniso.qvoice

## Identity

- Package: `com.riniso.qvoice`. Publisher: Riniso, support@zinijo.com.
- A standard Android text-to-speech engine (`TextToSpeechService`). Any app
  reaches it with `TextToSpeech(context, listener, "com.riniso.qvoice")`, or
  through the phone's default engine if the user made QVoice the default.
- **All speech is made on the phone.** QVoice has no account, no analytics
  and no server. Its only network use is voice downloads from GitHub,
  started by the user. The engine never stores the text it speaks for other
  apps.
- GPL-3.0, no ads, minSdk 26, targetSdk 36.

## Voices

| Voice (model id) | Languages | Speakers | Speed on a mid-range phone | Size |
|---|---|---|---|---|
| `kitten-nano-en` (built in) | en-US | 8: rosie, kiki, bella, luna (female); bruno, hugo, jasper, leo (male) | fast | in the app |
| `vits-piper-en_US-ljspeech-medium` / `-kristin-` / `-norman-` / `-john-`, `vits-piper-en_GB-cori-medium` | en-US, en-GB | one each (lj, kristin, cori female; norman, john male) | fast (about 1.8× real time) | ~64 MB each |
| `kokoro-multi-lang-v1_0` | en-US, en-GB, es, fr-FR, hi-IN, it-IT, pt-BR, zh-CN | 48 | slow (~0.2× on a Galaxy M31: starts late) | ~380 MB |
| `sherpa-onnx-supertonic-3-tts-int8-2026-05-11` | 31 languages incl. en, hi-IN, es, fr, de, ar, ja, ko, ru, … | 10 (f1–f5, m1–m5) | medium (~0.45×) | ~145 MB |

Only the built-in voice is there for sure. All others are optional
downloads, made by the user in QVoice. **No Tamil, Malayalam, Telugu,
Kannada, Bengali or Marathi voice exists**; those are left to Google's
engine (a decision taken in October 2026).

## How the engine answers

- **Voice names are public and stable:**
  - `<model>` for a voice with a single speaker;
  - `<model>#<speaker>` for one speaker in one language, e.g.
    `kitten-nano-en#rosie`;
  - `<model>#<speaker>@<lang>` for a speaker offered in several languages,
    e.g. `sherpa-onnx-supertonic-3-tts-int8-2026-05-11#f1@hi-IN`.

  An app may store a name and pass it back with `setVoice`. QVoice never
  renames a shipped voice.
- **Gender:** each voice whose gender is known carries the feature
  `qvoice.gender.female` or `qvoice.gender.male` in `Voice.getFeatures()`.
- **Latency:** `Voice.getLatency()` is `LATENCY_NORMAL` for the built-in
  and Piper voices, and `LATENCY_HIGH` for Kokoro and Supertonic. Use it to
  pick fast voices for alarms.
- **Network:** every voice reports `isNetworkConnectionRequired() == false`.
- **`isLanguageAvailable(locale)`:** returns `LANG_NOT_SUPPORTED` when no
  installed voice speaks the language (e.g. `ta-IN`). In that case use
  another engine.
- **Default voice per language:** the user's choice in QVoice, otherwise the
  best voice that keeps up on that phone. `setLanguage(locale)` picks it;
  calling `setLanguage` again picks up a default the user changed meanwhile.
- **Refusal:** text in a script QVoice can't read (Tamil text sent with an
  English locale, say) is refused with an error callback, not spoken as
  gibberish. The app should repeat the request on the system engine
  (RinisoSpeech does this).
- **Rendering to a file:** `synthesizeToFile` works like any engine (the
  framework writes the WAV). Sample rates differ by voice: 24,000 Hz
  (built-in, Kokoro), 22,050 Hz (Piper), 44,100 Hz (Supertonic). Read the
  rate from the WAV header and never assume one.
- **Rate and pitch:** speech rates up to 6× are supported, with pitch.
- **Audio attributes, focus and ducking:** handled by Android's TTS framework
  for every engine, as the app sets them with `setAudioAttributes`.
- **Standard intents:**
  - `ACTION_INSTALL_TTS_DATA` with `setPackage("com.riniso.qvoice")`
    opens QVoice's voice library;
  - `ACTION_CHECK_TTS_DATA` and `GET_SAMPLE_TEXT` are answered;
  - QVoice also adds "Read aloud" to every app's text-selection menu.
- **First use:** the first sentence after QVoice's process starts loads the
  model. That takes about 0.3 s for the built-in voice and seconds for
  Kokoro. Call `setLanguage` early (RinisoSpeech's `warmUp`) where timing
  matters.

## What QVoice does not do (yet)

- No Indian-language voices other than Hindi (Kokoro and Supertonic, which
  are slow on mid-range phones).
- No API beyond Android's TextToSpeech. No deep link "download a voice for
  language X" yet: the voice library opens on its full list. If an app needs
  that, ask for it in the QVoice chat. It would use the standard
  `ACTION_INSTALL_TTS_DATA` plus an extra.
