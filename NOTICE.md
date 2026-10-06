# NOTICE

QVoice — Copyright (C) 2026 Riniso.
Licensed under the GNU General Public License, version 3 or (at your option)
any later version. See [LICENSE](LICENSE).

The in-app **About → Open-source components** screen shows the same list,
with each full licence text (bundled under `app/src/main/assets/licenses/`).

## Components shipped in the app

| Component | What it does | Licence | Where |
|---|---|---|---|
| sherpa-onnx 1.13.8 (Xiaomi Corporation and the k2-fsa contributors) | Speech runtime (Kotlin API + JNI) | Apache-2.0 | `app/libs/sherpa-onnx-1.13.8.aar` |
| eSpeak NG (Jonathan Duddington, Reece H. Dunn and contributors) | Turns text into phonemes; compiled into `libsherpa-onnx-jni.so` | **GPL-3.0-or-later** | inside the AAR |
| eSpeak NG data | Pronunciation dictionaries and rules | **GPL-3.0-or-later** | `assets/bundled/espeak-ng-data.zip` |
| piper-phonemize (Michael Hansen) | Phoneme-to-token glue; compiled into the JNI library | MIT | inside the AAR |
| ONNX Runtime (Microsoft Corporation) | Neural network inference | MIT | `libonnxruntime.so` inside the AAR |
| KittenTTS nano 0.8, fp32 (KittenML) | The built-in English voice | Apache-2.0 | `assets/bundled/kitten-nano-en/` (with its LICENSE) |
| Sonic (Bill Cox) | Time-stretch and pitch for fast speech; vendored with small changes noted at the top of the file | Apache-2.0 | `engine/sonic/Sonic.java` |
| AndroidX, Jetpack Compose, Kotlin (Google LLC, JetBrains s.r.o.) | App framework | Apache-2.0 | Gradle dependencies |
| Apache Commons Compress, IO, Lang, Codec (The Apache Software Foundation) | Unpacks downloaded voices (bzip2, tar) | Apache-2.0 | Gradle dependencies |

## Corresponding source for the GPL parts (GPL-3.0 §6)

The QVoice APK/AAB contains object code built from GPL-3.0 sources. Their
complete corresponding source is:

- **QVoice itself** — this repository, at the tag of the release
  (URL: `qvoice.sourceUrl` in `gradle.properties`, shown in the app).
- **sherpa-onnx** (build scripts that produce the JNI library):
  https://github.com/k2-fsa/sherpa-onnx at tag `v1.13.8`.
  AAR from https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8,
  sha256 `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
- **eSpeak NG** as built by sherpa-onnx 1.13.8
  (`cmake/espeak-ng-for-piper.cmake`):
  https://github.com/csukuangfj/espeak-ng/archive/ed530aa113046142eb5115cf2fc9157854d0ffe1.zip
  (SHA256 `e4e262cbe34f7fe21f91f1ba3397f2728e1f30eafbae7853f2b753a9ed13f0dd`).
- **piper-phonemize** as built by sherpa-onnx 1.13.8
  (`cmake/piper-phonemize.cmake`):
  https://github.com/csukuangfj/piper-phonemize/archive/f3ff95afc03640bc1399e113e83361192a2fafb4.zip
  (SHA256 `d9cca4e2bdc7d6dd8dffb96a4668283dbd3f77a9c194a3e530c1e8eba9406a5d`).

GPL-3.0 requires that this source stay available for as long as the binary is
distributed. Before the first public release, mirror the two archives above
and the sherpa-onnx source tarball into the QVoice repository's release
assets, so availability doesn't depend on third-party hosting (tracked in
docs/BACKLOG.md).

## The built-in voice

The built-in voice (id `kitten-nano-en`) is `kitten-nano-en-v0_8-fp32`, the
sherpa-onnx packaging of KittenML's kitten-tts-nano-0.8-fp32
(https://github.com/KittenML/KittenTTS), Apache-2.0, downloaded from
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-fp32.tar.bz2
(sha256 `16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e`).
Versions 0.1-0.2 shipped the int8 packaging of the same model
(`kitten-nano-en-v0_8-int8`, sha256
`6fa5be852612ce761094ba74ee6123b4fc4acfefa79bf64dc63acae4a83af2fd`).
The model, voices.bin, tokens.txt and the upstream LICENSE file are shipped
unmodified; the espeak-ng-data folder from the same bundle is shipped once as
the shared data set.

## Voices users can download

These are not in the APK: the voice library downloads them from the
sherpa-onnx `tts-models` release on GitHub when a user asks, and shows each
one's licence (texts in `app/src/main/assets/licenses/`). Details and
validation: docs/CATALOGUE.md.

| Voice | Rights holder | Licence |
|---|---|---|
| Kokoro v1.0 (Kokoro-82M) | hexgrad | Apache-2.0 |
| Supertonic 3 | Supertone Inc. | Model: OpenRAIL-M (use restrictions in its Attachment A; users accept before downloading). Code: MIT |
| Piper voices LJ, Kristin, Norman, John, Cori | Bryce Beattie (training); published in rhasspy/piper-voices | MIT. Recordings: LJ Speech and LibriVox, public domain |

Kokoro and the Piper voices phonemize with eSpeak NG and reuse QVoice's
shared eSpeak NG data (GPL-3.0-or-later; each archive's copy was checked
byte-identical to it and is not installed again). Supertonic doesn't use
eSpeak NG.

## Design sources

QVoice's code was written for this project. Its design draws on reading
these projects, whose licences allow it (QVoice being GPL-3.0 is compatible
with all three):

- **HayaiTTS** (GPL-3.0, Ahmed Mohamed) — sherpa-onnx configuration per model
  family; clamping speaker ids before they reach native code.
- **Marmalade TTS** (MIT, marmalade-tts contributors) — TTS engine
  registration requirements, framework locale-code handling, hot-path
  caching, the Kitten speaker order and names.
- **NekoSpeak** (MIT, NekoSpeak Team) — the case for streaming synthesis
  and an engine lock that prevents release during generation.
- **AudioLab** (MIT, Arthur Breton) — model-manager ideas for later slices.
