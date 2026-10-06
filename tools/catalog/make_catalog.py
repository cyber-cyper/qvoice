#!/usr/bin/env python3
"""
Builds app/src/main/assets/catalog.json — the voices QVoice offers for download.

Every measured number (archive size, sha256, top-level folder, installed size)
comes from the real archive, never from typing: run

    python tools/catalog/make_catalog.py --archives DIR

where DIR holds the downloaded .tar.bz2 files named as on
https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models . For every
voice the script also checks, inside the archive:

  * every file the manifest names exists (what the app checks after install);
  * the bundled espeak-ng-data is byte-identical to the copy QVoice ships
    (app/src/main/assets/bundled/espeak-ng-data.zip) when the voice is marked
    espeakData "shared" — otherwise sharing it would change pronunciation.

What it cannot check is how a voice sounds: that is the sandbox validation
(ASR round trip, pitch, speed) recorded in docs/CATALOGUE.md. Add a voice here
only after it passed that and its licence was checked.

Standard library only.
"""
import argparse
import hashlib
import json
import os
import sys
import tarfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "catalog.json")
SHARED_ESPEAK_ZIP = os.path.join(ROOT, "app", "src", "main", "assets", "bundled", "espeak-ng-data.zip")
BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

# Bump whenever the catalogue content changes.
CATALOG_VERSION = 2

# speedHint: how fast a voice runs relative to the built-in voice (Kitten nano
# fp32 = 1.0), measured on a Galaxy M31 (Exynos 9611, 4 threads) in the
# 0.2.2/0.3.0 phone logs: built-in 1.5x real time, Piper medium 2.8x, Kokoro
# 0.25x. Supertonic wasn't measured on the phone: estimated from a same-run
# sandbox benchmark (0.56x the built-in voice there) scaled by the phone/sandbox
# gap the measured voices showed (larger models lose more on a phone) -> 0.45.
# The app predicts "speed on this phone" = built-in speed x hint until the
# voice has spoken there and been measured (engine/SpeedBook.kt).

# ---------------------------------------------------------------------------
# Kokoro v1.0 multi-language (Apache-2.0, hexgrad). Speaker ids are the order
# of the model's speaker_names metadata (checked against model.onnx).
# Grades are the author's "overall grade" from Kokoro-82M/VOICES.md; Spanish
# and Brazilian Portuguese are ungraded there.
# ---------------------------------------------------------------------------
KOKORO_SPEAKERS = (
    "af_alloy af_aoede af_bella af_heart af_jessica af_kore af_nicole af_nova af_river af_sarah af_sky "
    "am_adam am_echo am_eric am_fenrir am_liam am_michael am_onyx am_puck am_santa "
    "bf_alice bf_emma bf_isabella bf_lily bm_daniel bm_fable bm_george bm_lewis "
    "ef_dora em_alex ff_siwis hf_alpha hf_beta hm_omega hm_psi if_sara im_nicola "
    "jf_alpha jf_gongitsune jf_nezumi jf_tebukuro jm_kumo pf_dora pm_alex pm_santa "
    "zf_xiaobei zf_xiaoni zf_xiaoxiao zf_xiaoyi zm_yunjian zm_yunxi zm_yunxia zm_yunyang em_santa"
).split()
KOKORO_GRADES = {
    "af_heart": "A", "af_alloy": "C", "af_aoede": "C+", "af_bella": "A-", "af_jessica": "D", "af_kore": "C+",
    "af_nicole": "B-", "af_nova": "C", "af_river": "D", "af_sarah": "C+", "af_sky": "C-", "am_adam": "F+",
    "am_echo": "D", "am_eric": "D", "am_fenrir": "C+", "am_liam": "D", "am_michael": "C+", "am_onyx": "D",
    "am_puck": "C+", "am_santa": "D-", "bf_alice": "D", "bf_emma": "B-", "bf_isabella": "C", "bf_lily": "D",
    "bm_daniel": "D", "bm_fable": "C", "bm_george": "C", "bm_lewis": "D+", "jf_alpha": "C+",
    "jf_gongitsune": "C", "jf_nezumi": "C-", "jf_tebukuro": "C", "jm_kumo": "C-", "zf_xiaobei": "D",
    "zf_xiaoni": "D", "zf_xiaoxiao": "D", "zf_xiaoyi": "D", "zm_yunjian": "D", "zm_yunxi": "D",
    "zm_yunxia": "D", "zm_yunyang": "D", "ff_siwis": "B-", "hf_alpha": "C", "hf_beta": "C", "hm_omega": "C",
    "hm_psi": "C", "if_sara": "C", "im_nicola": "C",
}
# First letter of a speaker name -> (BCP-47 tag, espeak-ng voice, extra load settings).
# Japanese ("j") is left out: sherpa-onnx phonemizes Kokoro's Japanese with
# espeak-ng, which cannot read kanji — the ASR round trip came back garbled
# (Kokoro's own pipeline uses a Japanese G2P that sherpa-onnx lacks).
US_LEXICON = ["lexicon-us-en.txt"]
KOKORO_LANGUAGES = {
    "a": ("en-US", "en-us", {"lexicons": US_LEXICON}),
    "b": ("en-GB", "en", {"lexicons": ["lexicon-gb-en.txt"]}),
    "e": ("es", "es", {"lexicons": US_LEXICON}),
    "f": ("fr-FR", "fr", {"lexicons": US_LEXICON}),
    "h": ("hi-IN", "hi", {"lexicons": US_LEXICON}),
    "i": ("it-IT", "it", {"lexicons": US_LEXICON}),
    "p": ("pt-BR", "pt-br", {"lexicons": US_LEXICON}),
    # Chinese only: jieba dictionary and the number/date/phone rules (which
    # would turn English digits into Chinese numerals).
    "z": ("zh-CN", "cmn", {"lexicons": US_LEXICON + ["lexicon-zh.txt"], "dictDir": "dict",
                           "ruleFsts": ["date-zh.fst", "number-zh.fst", "phone-zh.fst"]}),
}


def grade_quality(grade):
    if grade is None:
        return "normal"
    if grade.startswith("A"):
        return "very_high"
    if grade in ("B+", "B", "B-", "C+"):
        return "high"
    if grade.startswith("C"):
        return "normal"
    return "low"


def display_name(key):
    return key.split("_", 1)[1].capitalize()


# Speakers left out after the sandbox validation (docs/CATALOGUE.md).
KOKORO_EXCLUDED = {
    "zf_xiaoni": "ASR round trip garbled (CER 0.67; the other Chinese voices 0-0.2)",
}


def kokoro():
    speakers = []
    for sid, key in enumerate(KOKORO_SPEAKERS):
        lang = KOKORO_LANGUAGES.get(key[0])
        if lang is None or key in KOKORO_EXCLUDED:
            continue
        speakers.append({
            "sid": sid, "key": key, "name": display_name(key),
            "gender": "female" if key[1] == "f" else "male",
            "languages": [lang[0]],
            "quality": grade_quality(KOKORO_GRADES.get(key)),
        })
    # Order in the voice list: best grade first within each language (ungraded
    # counts as C), then speaker id. Among equal qualities the first one is
    # what QVoice picks as a language's default, so af_heart (A) leads.
    grades = ["A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "D-", "F+"]
    speakers.sort(key=lambda s: (list(KOKORO_LANGUAGES).index(s["key"][0]),
                                 grades.index(KOKORO_GRADES.get(s["key"], "C")), s["sid"]))
    language_config = {}
    for tag, espeak, extra in KOKORO_LANGUAGES.values():
        cfg = {"espeakVoice": espeak}
        cfg.update(extra)
        language_config[tag] = cfg
    return {
        "archive": "kokoro-multi-lang-v1_0",
        "manifest": {
            "schema": 1, "id": "kokoro-multi-lang-v1_0", "family": "kokoro", "name": "Kokoro", "version": 1,
            "sampleRate": 24000, "languages": [v[0] for v in KOKORO_LANGUAGES.values()], "quality": "high",
            "files": {"model": "model.onnx", "tokens": "tokens.txt", "voices": "voices.bin"},
            "espeakData": "shared", "speakers": speakers, "defaultSpeaker": "af_heart",
            "license": {"name": "Apache-2.0", "holder": "hexgrad (Kokoro-82M)",
                        "url": "https://huggingface.co/hexgrad/Kokoro-82M"},
            "languageConfig": language_config,
        },
        "summary": "Natural, expressive voices in English (US and UK), Hindi, Spanish, French, Italian, "
                   "Portuguese (Brazil) and Chinese. Needs a recent phone.",
        "speedHint": 0.17,
    }


# ---------------------------------------------------------------------------
# Supertonic 3 (Supertone Inc.; model weights under OpenRAIL-M, which users
# must accept: it carries use restrictions). 10 voice styles, 31 languages,
# language chosen per request. Styles 0-4 female, 5-9 male (checked by pitch).
# Region tags only where a language is spoken mainly in one country.
# ---------------------------------------------------------------------------
SUPERTONIC_LANGUAGES = [
    "en", "ko-KR", "ja-JP", "ar", "bg-BG", "cs-CZ", "da-DK", "de", "el-GR", "es", "et-EE", "fi-FI",
    "fr", "hi-IN", "hr-HR", "hu-HU", "id-ID", "it-IT", "lt-LT", "lv-LV", "nl", "pl-PL", "pt", "ro-RO",
    "ru", "sk-SK", "sl-SI", "sv-SE", "tr-TR", "uk-UA", "vi-VN",
]


def supertonic():
    archive = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
    speakers = [{"sid": i, "key": f"f{i + 1}", "name": f"F{i + 1}", "gender": "female"} for i in range(5)]
    speakers += [{"sid": 5 + i, "key": f"m{i + 1}", "name": f"M{i + 1}", "gender": "male"} for i in range(5)]
    return {
        "archive": archive,
        "manifest": {
            "schema": 1, "id": archive, "family": "supertonic", "name": "Supertonic 3", "version": 1,
            "sampleRate": 44100, "languages": SUPERTONIC_LANGUAGES, "quality": "high",
            # Supertonic loads its seven files by role ("extra"); "model" only
            # satisfies the manifest schema and names the largest of them.
            "files": {
                "model": "vector_estimator.int8.onnx",
                "extra": {
                    "duration_predictor": "duration_predictor.int8.onnx",
                    "text_encoder": "text_encoder.int8.onnx",
                    "vector_estimator": "vector_estimator.int8.onnx",
                    "vocoder": "vocoder.int8.onnx",
                    "tts_json": "tts.json",
                    "unicode_indexer": "unicode_indexer.bin",
                    "voice_style": "voice.bin",
                },
            },
            "espeakData": "none", "speakers": speakers, "defaultSpeaker": "f1",
            "license": {"name": "OpenRAIL-M", "holder": "Supertone Inc.",
                        "url": "https://huggingface.co/Supertone/supertonic-3"},
        },
        "summary": "Ten voices that speak 31 languages, including Hindi, Arabic, Korean, Japanese, "
                   "Russian and most European languages.",
        "acceptance": "openrail-m",
        "speedHint": 0.45,
    }


# ---------------------------------------------------------------------------
# Piper voices by Bryce Beattie: trained on public-domain recordings
# (LibriVox; LJ Speech), published in rhasspy/piper-voices (MIT). Only voices
# trained from scratch or from each other — never the "lessac"-based ones,
# whose Blizzard 2013 data is licensed for research only.
# ---------------------------------------------------------------------------
def piper(archive, key, name, gender, tag, recordings):
    return {
        "archive": archive,
        "manifest": {
            "schema": 1, "id": archive, "family": "vits", "name": f"{name} (Piper)", "version": 1,
            "sampleRate": 22050, "languages": [tag], "quality": "normal",
            "files": {"model": archive.replace("vits-piper-", "") + ".onnx", "tokens": "tokens.txt"},
            "espeakData": "shared",
            "speakers": [{"sid": 0, "key": key, "name": name, "gender": gender}],
            "defaultSpeaker": key,
            "license": {"name": "MIT", "holder": "Bryce Beattie · rhasspy/piper-voices",
                        "url": "https://huggingface.co/rhasspy/piper-voices"},
        },
        "summary": f"Clear, fast voice that suits any phone. Public-domain recordings ({recordings}).",
        "speedHint": 1.8,
    }


VOICES = [
    kokoro(),
    supertonic(),
    piper("vits-piper-en_US-ljspeech-medium", "lj", "LJ", "female", "en-US", "LJ Speech"),
    piper("vits-piper-en_US-kristin-medium", "kristin", "Kristin", "female", "en-US", "LibriVox"),
    piper("vits-piper-en_US-norman-medium", "norman", "Norman", "male", "en-US", "LibriVox"),
    piper("vits-piper-en_US-john-medium", "john", "John", "male", "en-US", "LibriVox"),
    piper("vits-piper-en_GB-cori-medium", "cori", "Cori", "female", "en-GB", "LibriVox"),
]


def required_files(m):
    f = m["files"]
    out = [f["model"]] + [f[k] for k in ("tokens", "voices", "vocoder", "dictDir") if k in f]
    out += f.get("lexicons", []) + f.get("ruleFsts", []) + list(f.get("extra", {}).values())
    for cfg in m.get("languageConfig", {}).values():
        out += cfg.get("lexicons", []) + ([cfg["dictDir"]] if "dictDir" in cfg else []) + cfg.get("ruleFsts", [])
    return sorted(set(out))


def shared_espeak_hashes():
    with zipfile.ZipFile(SHARED_ESPEAK_ZIP) as z:
        return {i.filename: hashlib.sha256(z.read(i)).hexdigest() for i in z.infolist() if not i.is_dir()}


def measure(path, voice, shared):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for block in iter(lambda: fh.read(1 << 20), b""):
            h.update(block)
    m = voice["manifest"]
    names, installed, espeak = set(), 0, {}
    tops = set()
    with tarfile.open(path, "r:bz2") as t:
        for member in t:
            name = member.name[2:] if member.name.startswith("./") else member.name
            tops.add(name.split("/")[0])
            if len(tops) != 1:
                sys.exit(f"{voice['archive']}: expected one top-level folder, found {sorted(tops)}")
            rel = name.split("/", 1)[1] if "/" in name else ""
            if not rel:
                continue
            if member.issym() or member.islnk():
                sys.exit(f"{voice['archive']}: contains a link ({name}); the installer skips links")
            names.add(rel.rstrip("/"))
            if member.isfile():
                if rel.startswith("espeak-ng-data/"):
                    espeak[rel[len("espeak-ng-data/"):]] = hashlib.sha256(t.extractfile(member).read()).hexdigest()
                    if m["espeakData"] == "shared":
                        continue
                installed += member.size
    missing = [f for f in required_files(m) if f not in names]
    if missing:
        sys.exit(f"{voice['archive']}: archive lacks {missing}")
    if m["espeakData"] == "shared" and espeak != shared:
        diff = sorted(set(espeak.items()) ^ set(shared.items()))[:5]
        sys.exit(f"{voice['archive']}: espeak-ng-data differs from the shared copy, e.g. {diff}")
    return {"sha256": h.hexdigest(), "size": os.path.getsize(path), "root": tops.pop(), "installed": installed}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--archives", required=True, help="folder with the downloaded .tar.bz2 files")
    args = ap.parse_args()
    shared = shared_espeak_hashes()
    entries = []
    for voice in VOICES:
        path = os.path.join(args.archives, voice["archive"] + ".tar.bz2")
        if not os.path.isfile(path):
            sys.exit(f"missing {path}")
        got = measure(path, voice, shared)
        entry = {
            "manifest": voice["manifest"],
            "download": {"url": BASE_URL + voice["archive"] + ".tar.bz2", "sha256": got["sha256"],
                         "size": got["size"], "format": "tar.bz2", "root": got["root"]},
            "installedSize": got["installed"],
            "summary": voice["summary"],
            "speedHint": voice["speedHint"],
        }
        if voice.get("acceptance"):
            entry["acceptance"] = voice["acceptance"]
        entries.append(entry)
        print(f"{voice['archive']:48} {got['size'] / 1e6:7.1f} MB download, {got['installed'] / 1e6:7.1f} MB installed, "
              f"{len(voice['manifest'].get('speakers', []))} speakers, sha256 {got['sha256'][:12]}")
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"schema": 1, "version": CATALOG_VERSION, "voices": entries}, fh, indent=1, ensure_ascii=False)
        fh.write("\n")
    print(f"wrote {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
