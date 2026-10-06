#!/usr/bin/env python3
"""
Static checks for QVoice that run without Gradle.

Run from the project root:   python tools/check_project.py
Exit code 0 = clean, 1 = errors found. Warnings never fail the run.

Each check exists because the class of error it catches can't be seen until a
Gradle build (or a phone) fails otherwise:

  1. brace/paren/bracket balance in every .kt file (string- and comment-aware)
  2. every XML file is well-formed
  3. every R.string.x used in Kotlin exists in res/values/strings.xml, and
     stringResource(...) passes as many arguments as the string has placeholders
  4. every class named in AndroidManifest.xml / xml/tts_engine.xml exists
  5. every `import com.riniso.qvoice.X` points at a declared top-level name
     (in the Kotlin sources, or a public class of the vendored Java ones)
  6. every "licenses/..." or "bundled/..." asset path used in Kotlin exists
     (a file, or a non-empty folder: a built-in voice's asset folder)
  7. unused imports (warning only)
  8. string resources obey aapt2's escaping rules (an unescaped apostrophe
     passes XML parsing but fails mergeDebugResources — slice 2 shipped four)
  9. no `lateinit` in the TextToSpeechService subclass: the framework's
     onCreate() calls onLoadLanguage() before ours can assign anything
     (slice 1 crashed on open because of exactly that)
 10. no lambda passed to sherpa-onnx's generateWith*Callback: its native code
     needs invoke([F)Ljava/lang/Integer;, which only a real class has
     (slice 2 crashed on the first "Speak" because of exactly that)
 11. the TextToSpeechService subclass calls a model's generate() only inside
     a ReadAhead `produce = { ... }` lambda: generating on the framework's
     thread paces generation to playback and leaves a gap before every
     sentence (0.2.2 on the Galaxy M31; see engine/ReadAhead.kt)
 12. no java.util.Locale constructor outside TtsLocales.legacyLocale: they
     are deprecated since Java 19, so the owner's Gradle build (JDK 21)
     warns on every one in the unit tests, while the sandbox compiles them
     against android-35's undeprecated Locale and can't see it (slice 16's
     ReaderTest did exactly that); use Locale.forLanguageTag
 13. the debug build's launcher shortcuts (src/debug/res/xml/shortcuts.xml)
     match the main ones in everything but the package they name: debug
     builds are a separate app (applicationIdSuffix ".debug"), and a copy
     that drifted would ship test builds with missing or different shortcuts
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")
PKG = "com.riniso.qvoice"

errors: list[str] = []
warnings: list[str] = []


def rel(p: str) -> str:
    return os.path.relpath(p, ROOT)


def kotlin_files(*roots: str) -> list[str]:
    out = []
    for r in roots:
        for dirpath, _, files in os.walk(r):
            out += [os.path.join(dirpath, f) for f in files if f.endswith(".kt")]
    return sorted(out)


def strip_code(src: str) -> str:
    """Replaces comments and string/char literal contents with spaces, keeping
    newlines and ${...} template expressions, so brace counting sees code only."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            depth = 1
            i += 2
            while i < n and depth:
                if src.startswith("/*", i):
                    depth += 1
                    i += 2
                elif src.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    out.append("\n" if src[i] == "\n" else " ")
                    i += 1
            continue
        if src.startswith('"""', i):
            i += 3
            out.append('""')
            while i < n and not src.startswith('"""', i):
                if src.startswith("${", i):
                    i = copy_template(src, i, out)
                    continue
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            i += 3
            continue
        if c == '"':
            i += 1
            out.append('""')
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 2
                    continue
                if src.startswith("${", i):
                    i = copy_template(src, i, out)
                    continue
                i += 1
            i += 1
            continue
        if c == "'":
            j = i + 1
            if j < n and src[j] == "\\":
                j += 2
                while j < n and src[j] != "'":
                    j += 1
            else:
                j += 1
            if j < n and src[j] == "'":
                out.append("' '")
                i = j + 1
                continue
        out.append(c)
        i += 1
    return "".join(out)


def copy_template(src: str, i: int, out: list) -> int:
    """Copies a ${...} template (which is code) and returns the index after it."""
    depth = 0
    j = i + 1
    while j < len(src):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                out.append(strip_code(src[i + 2:j]))
                return j + 1
        j += 1
    return j


def check_balance(files: list[str]) -> None:
    pairs = {")": "(", "]": "[", "}": "{"}
    for f in files:
        code = strip_code(open(f, encoding="utf-8").read())
        stack = []
        line = 1
        for ch in code:
            if ch == "\n":
                line += 1
            elif ch in "([{":
                stack.append((ch, line))
            elif ch in ")]}":
                if not stack or stack[-1][0] != pairs[ch]:
                    errors.append(f"{rel(f)}:{line}: unmatched '{ch}'")
                    break
                stack.pop()
        else:
            if stack:
                ch, ln = stack[-1]
                errors.append(f"{rel(f)}:{ln}: '{ch}' never closed")


def check_xml() -> None:
    for dirpath, _, fs in os.walk(os.path.join(ROOT, "app", "src")):
        for f in fs:
            if f.endswith(".xml"):
                p = os.path.join(dirpath, f)
                try:
                    ET.parse(p)
                except ET.ParseError as e:
                    errors.append(f"{rel(p)}: XML error: {e}")


def string_resources() -> dict[str, str]:
    p = os.path.join(APP, "res", "values", "strings.xml")
    tree = ET.parse(p)
    return {e.get("name"): "".join(e.itertext()) for e in tree.getroot().findall("string")}


def android_string_problems(text: str) -> list[str]:
    """What aapt2 rejects in a <string>/<item> text (XML entities already decoded)."""
    problems = []
    lead = text.lstrip()[:1]
    if lead in ("@", "?") and not re.match(r"\s*[@?](android:)?(string|attr)/\w+\s*$", text):
        problems.append("starts with @ or ? (a resource reference unless written \\@ / \\?)")
    in_quotes = False
    i = 0
    while i < len(text):
        c = text[i]
        if c == "\\":
            nxt = text[i + 1:i + 2]
            if nxt == "u":
                digits = text[i + 2:i + 6]
                if len(digits) < 4 or any(d not in "0123456789abcdefABCDEF" for d in digits):
                    problems.append("invalid \\u escape (needs 4 hex digits)")
                i += 6
                continue
            if nxt == "":
                problems.append("ends with a lone backslash")
            i += 2
            continue
        if c == '"':
            in_quotes = not in_quotes
        elif c == "'" and not in_quotes:
            problems.append("unescaped apostrophe (write \\')")
        i += 1
    return problems


def check_string_escapes() -> None:
    res = os.path.join(APP, "res")
    for d in sorted(os.listdir(res)):
        if not d.startswith("values"):
            continue
        for f in sorted(os.listdir(os.path.join(res, d))):
            p = os.path.join(res, d, f)
            try:
                root = ET.parse(p).getroot()
            except ET.ParseError:
                continue  # reported by check_xml
            texts = [(e.get("name"), e) for e in root.findall("string")]
            for group in root.findall("plurals") + root.findall("string-array"):
                texts += [(f"{group.get('name')}/{item.get('quantity') or 'item'}", item) for item in group.findall("item")]
            for name, element in texts:
                for problem in sorted(set(android_string_problems("".join(element.itertext())))):
                    errors.append(f"{rel(p)}: string '{name}': {problem}")


def check_tts_service_init(files: list[str]) -> None:
    for f in files:
        code = strip_code(open(f, encoding="utf-8").read())
        if re.search(r":\s*TextToSpeechService\s*\(\s*\)", code) and re.search(r"\blateinit\s+var\b", code):
            errors.append(
                f"{rel(f)}: lateinit property in a TextToSpeechService subclass — the framework's "
                "onCreate() calls onLoadLanguage() before the subclass can assign it; use `by lazy`"
            )


def check_jni_callbacks(files: list[str]) -> None:
    call = re.compile(r"\bgenerateWith\w*Callback\s*\(")
    for f in files:
        code = strip_code(open(f, encoding="utf-8").read())
        for m in call.finditer(code):
            args = call_arguments(code, m.end() - 1)
            rest = code[m.end() - 1:]
            depth, end = 0, 0
            for i, ch in enumerate(rest):
                depth += ch in "([{"
                depth -= ch in ")]}"
                if depth == 0:
                    end = i
                    break
            trailing = re.match(r"\s*\{", rest[end + 1:])
            if trailing or any(a.lstrip().startswith("{") for a in args):
                line = code[: m.start()].count("\n") + 1
                errors.append(
                    f"{rel(f)}:{line}: lambda passed to a sherpa-onnx generate callback — "
                    "use SherpaChunkCallback (native code needs invoke([F)Ljava/lang/Integer;)"
                )


def check_read_ahead(files: list[str]) -> None:
    call = re.compile(r"\.generate\s*\(")
    inside_produce = re.compile(r"produce\s*=\s*\{[^{}]*$")
    for f in files:
        code = strip_code(open(f, encoding="utf-8").read())
        if not re.search(r":\s*TextToSpeechService\s*\(\s*\)", code):
            continue
        for m in call.finditer(code):
            if not inside_produce.search(code[max(0, m.start() - 160): m.start()]):
                line = code[: m.start()].count("\n") + 1
                errors.append(
                    f"{rel(f)}:{line}: generate() called outside ReadAhead's produce lambda — "
                    "generation would wait for playback and leave a gap before every sentence"
                )


def check_locale_constructors(files: list[str]) -> None:
    ctor = re.compile(r"(?<![\w.])Locale\s*\(")
    for f in files:
        code = strip_code(open(f, encoding="utf-8").read())
        for m in ctor.finditer(code):
            line_start = code.rfind("\n", 0, m.start()) + 1
            line_text = code[line_start: code.find("\n", m.start())]
            # The one sanctioned use: TtsLocales.legacyLocale wraps it (see its comment).
            if "fun legacyLocale" in line_text:
                continue
            line = code[: m.start()].count("\n") + 1
            errors.append(
                f"{rel(f)}:{line}: Locale(...) constructor — deprecated since Java 19 "
                "(Gradle's unit-test compile warns); use Locale.forLanguageTag(\"xx-YY\")"
            )


def check_debug_shortcuts() -> None:
    main_xml = os.path.join(APP, "res", "xml", "shortcuts.xml")
    debug_xml = os.path.join(ROOT, "app", "src", "debug", "res", "xml", "shortcuts.xml")
    if not os.path.exists(debug_xml):
        return
    android = "{http://schemas.android.com/apk/res/android}"

    def shape(path: str, package: str) -> list:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            return []  # reported by check_xml
        out = []
        for shortcut in root:
            intents = []
            for intent in shortcut.iter("intent"):
                attrs = dict(intent.attrib)
                if attrs.get(android + "targetPackage") != package:
                    errors.append(f"{rel(path)}: shortcut intent names {attrs.get(android + 'targetPackage')!r}, expected {package!r}")
                attrs.pop(android + "targetPackage", None)
                extras = sorted(tuple(sorted(e.attrib.items())) for e in intent.iter("extra"))
                intents.append((sorted(attrs.items()), extras))
            out.append((sorted(shortcut.attrib.items()), intents))
        return out

    if shape(main_xml, PKG) != shape(debug_xml, PKG + ".debug"):
        errors.append(
            f"{rel(debug_xml)}: differs from {rel(main_xml)} in more than the package; "
            "change both files together"
        )


def placeholder_count(text: str) -> int:
    positional = {int(m) for m in re.findall(r"%(\d+)\$[sdf]", text)}
    return max(positional) if positional else len(re.findall(r"%[sdf]", text))


def call_arguments(code: str, start: int) -> list[str]:
    """Top-level comma-separated arguments of the call whose '(' is at start."""
    depth = 0
    args, cur = [], []
    for ch in code[start:]:
        if ch in "([{":
            depth += 1
            if depth == 1:
                continue
        elif ch in ")]}":
            depth -= 1
            if depth == 0:
                args.append("".join(cur).strip())
                return [a for a in args if a]
        if depth == 1 and ch == ",":
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    return args


def check_strings(files: list[str]) -> None:
    strings = string_resources()
    used = set()
    for f in files:
        raw = open(f, encoding="utf-8").read()
        code = strip_code(raw)
        for name in re.findall(r"\bR\.string\.(\w+)", code):
            used.add(name)
            if name not in strings:
                errors.append(f"{rel(f)}: R.string.{name} is not in strings.xml")
        for m in re.finditer(r"\b(stringResource|getString)\s*\(", code):
            args = call_arguments(code, m.end() - 1)
            if not args:
                continue
            ref = re.fullmatch(r"R\.string\.(\w+)", args[0])
            if not ref or ref.group(1) not in strings:
                continue
            want = placeholder_count(strings[ref.group(1)])
            got = len(args) - 1
            if want != got:
                line = code[: m.start()].count("\n") + 1
                errors.append(
                    f"{rel(f)}:{line}: {ref.group(1)} has {want} placeholder(s) but {got} argument(s) are passed"
                )
    # Strings the manifest and XML resources use (@string/name) count as used,
    # and a reference there to a string that doesn't exist is an error.
    for root, _, names in os.walk(APP):
        for fn in names:
            if not fn.endswith(".xml") or fn == "strings.xml":
                continue
            path = os.path.join(root, fn)
            for name in re.findall(r"@string/(\w+)", open(path, encoding="utf-8").read()):
                used.add(name)
                if name not in strings:
                    errors.append(f"{rel(path)}: @string/{name} is not in strings.xml")
    for name in sorted(set(strings) - used):
        warnings.append(f"strings.xml: '{name}' is never used")


DECL_PATTERN = (
    r"(?:(?:public|internal|private|protected|override|data|sealed|enum|abstract|open|inline|value|annotation|fun|const)\s+)*"
    r"(?:class|interface|object|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)"
)


def declared_names(files: list[str]) -> dict[str, set[str]]:
    """package -> top-level names declared in it."""
    decl: dict[str, set[str]] = {}
    top = re.compile(r"^" + DECL_PATTERN, re.M)
    for f in files:
        src = open(f, encoding="utf-8").read()
        pkg = re.search(r"^package\s+([\w.]+)", src, re.M)
        if not pkg:
            continue
        names = decl.setdefault(pkg.group(1), set())
        names.update(top.findall(strip_code(src)))
    return decl


def java_declared_names(root: str) -> dict[str, set[str]]:
    """package -> public top-level classes of the (vendored) Java sources."""
    decl: dict[str, set[str]] = {}
    for dirpath, _, files in os.walk(root):
        for f in files:
            if not f.endswith(".java"):
                continue
            src = open(os.path.join(dirpath, f), encoding="utf-8").read()
            pkg = re.search(r"^package\s+([\w.]+)\s*;", src, re.M)
            if pkg:
                names = decl.setdefault(pkg.group(1), set())
                names.update(re.findall(r"^public\s+(?:final\s+|abstract\s+)*(?:class|interface|enum)\s+(\w+)", src, re.M))
    return decl


def nested_names(files: list[str]) -> dict[str, set[str]]:
    """package -> names declared at any depth (members, nested classes, enum entries excluded)."""
    out: dict[str, set[str]] = {}
    anywhere = re.compile(r"^\s*" + DECL_PATTERN, re.M)
    for f in files:
        src = open(f, encoding="utf-8").read()
        pkg = re.search(r"^package\s+([\w.]+)", src, re.M)
        if pkg:
            out.setdefault(pkg.group(1), set()).update(anywhere.findall(strip_code(src)))
    return out


def check_manifest(decl: dict[str, set[str]]) -> None:
    ns = "{http://schemas.android.com/apk/res/android}"
    manifest = os.path.join(APP, "AndroidManifest.xml")
    names = []
    # Unparsable files are already reported by check_xml; skip them here
    # rather than crashing the whole run.
    try:
        for el in ET.parse(manifest).getroot().iter():
            if el.tag in ("application", "activity", "service", "receiver", "provider") and el.get(ns + "name"):
                names.append(el.get(ns + "name"))
    except ET.ParseError:
        return
    try:
        tts = ET.parse(os.path.join(APP, "res", "xml", "tts_engine.xml")).getroot()
        if tts.get(ns + "settingsActivity"):
            names.append(tts.get(ns + "settingsActivity"))
    except ET.ParseError:
        pass
    for n in names:
        fq = PKG + n if n.startswith(".") else n
        pkg, _, cls = fq.rpartition(".")
        if not fq.startswith(PKG):
            continue
        if cls not in decl.get(pkg, set()):
            errors.append(f"manifest/tts_engine.xml names {fq}, which is not declared in the sources")
    res = os.path.join(APP, "res")
    for ref in re.findall(r"@(xml|mipmap|drawable|string|style|color)/([\w.]+)", open(manifest).read()):
        kind, name = ref
        if kind == "string":
            if name not in string_resources():
                errors.append(f"manifest references @string/{name}, missing")
            continue
        found = False
        for d in os.listdir(res):
            if d.split("-")[0] == ("values" if kind in ("style", "color") else kind):
                folder = os.path.join(res, d)
                for f in os.listdir(folder):
                    if kind in ("style", "color"):
                        if f"name=\"{name}\"" in open(os.path.join(folder, f), encoding="utf-8").read():
                            found = True
                    elif os.path.splitext(f)[0] == name:
                        found = True
        if not found:
            errors.append(f"manifest references @{kind}/{name}, missing")


def check_imports(files: list[str], decl: dict[str, set[str]]) -> None:
    nested = nested_names(files)
    for f in files:
        src = open(f, encoding="utf-8").read()
        code = strip_code(src)
        body = re.sub(r"^\s*(import|package)\s+.*$", "", code, flags=re.M)
        for m in re.finditer(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", src, re.M):
            full, alias = m.group(1), m.group(2)
            simple = alias or full.rsplit(".", 1)[-1]
            if full.startswith(PKG + "."):
                pkg, _, name = full.rpartition(".")
                # "import pkg.Outer.member": Outer declared in pkg, member inside it.
                outer_pkg, _, outer = pkg.rpartition(".")
                member_import = outer in decl.get(outer_pkg, set()) and name in nested.get(outer_pkg, set())
                if name != "R" and name != "BuildConfig" and name not in decl.get(pkg, set()) and not member_import:
                    errors.append(f"{rel(f)}: import {full} — no such declaration in the sources")
            if simple in ("getValue", "setValue", "provideDelegate"):
                continue  # delegate operators are used implicitly by `by`
            name = re.escape(simple)
            if simple[0].islower():
                # A function/extension import only counts when it is called,
                # assigned or accessed — a lambda parameter with the same name
                # (Scaffold's `padding ->`) must not hide an unused import.
                # An extension property import (viewModelScope) is used as a
                # receiver instead: "name.member".
                used = re.search(r"(\b" + name + r"\s*(\(|\{|<|=(?![=>]))|::" + name + r"\b|\." + name + r"\b)", body) or (
                    simple.endswith("Scope") and re.search(r"\b" + name + r"\s*\.", body)
                )
            else:
                used = re.search(r"\b" + name + r"\b", body)
            if not used:
                warnings.append(f"{rel(f)}: unused import {full}")


def check_assets(files: list[str]) -> None:
    assets = os.path.join(APP, "assets")
    for f in files:
        src = open(f, encoding="utf-8").read()
        for path in re.findall(r"\"((?:licenses|bundled)/[\w.\-/]+)\"", src):
            full = os.path.join(assets, path)
            if not (os.path.isfile(full) or (os.path.isdir(full) and os.listdir(full))):
                errors.append(f"{rel(f)}: asset '{path}' does not exist")


def main() -> int:
    main_kt = kotlin_files(os.path.join(APP, "java"))
    test_kt = kotlin_files(os.path.join(ROOT, "app", "src", "test"))
    decl = declared_names(main_kt + test_kt)
    for pkg, names in java_declared_names(os.path.join(APP, "java")).items():
        decl.setdefault(pkg, set()).update(names)
    check_balance(main_kt + test_kt)
    check_xml()
    check_string_escapes()
    check_strings(main_kt)
    check_tts_service_init(main_kt)
    check_jni_callbacks(main_kt)
    check_read_ahead(main_kt)
    check_locale_constructors(main_kt + test_kt)
    check_debug_shortcuts()
    check_manifest(decl)
    check_imports(main_kt + test_kt, decl)
    check_assets(main_kt)
    for w in warnings:
        print("warning:", w)
    for e in errors:
        print("ERROR:", e)
    print(f"check_project: {len(main_kt) + len(test_kt)} Kotlin files, {len(errors)} error(s), {len(warnings)} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
