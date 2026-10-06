#!/usr/bin/env python3
"""
Checks the Compose stubs against the real API signature files: every stub
function must match a published signature by name and parameter names, in
order. A stub that drifts from the real API could hide an error or invent
one. Usage (from the project root):

    bash tools/sandbox/compose-stubs/fetch_api.sh /tmp/qvoice-api
    python3 tools/sandbox/compose-stubs/check_stubs.py /tmp/qvoice-api
"""
import re, glob, os, sys
A = sys.argv[1] if len(sys.argv) > 1 else "api"
STUBS = os.path.dirname(os.path.abspath(__file__))
DUMPS = {
 "m3.kt": ["m3.txt"],
 "foundation.kt": ["foundation.txt"], "foundation_selection.kt": ["foundation.txt"], "foundation_shape.kt": ["foundation.txt"],
 "foundation_lazy.kt": ["foundation.txt"], "foundation_layout.kt": ["layout.txt"],
 "runtime.kt": ["runtime.txt"], "saveable.kt": ["saveable.txt"],
 "lifecycle_compose.kt": ["lifecycle-runtime-compose.txt"], "lifecycle_vm_compose.kt": ["vmcompose.txt"],
 "activity.kt": ["activity.txt"], "activity_compose.kt": ["activity-compose.txt"],
}
for f in glob.glob(os.path.join(STUBS, "ui_*.kt")):
    DUMPS[os.path.basename(f)] = ["ui.txt", "ui-graphics.txt", "ui-unit.txt", "ui-geometry.txt", "ui-text.txt"]

def split_top(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch in "<([": depth += 1
        elif ch in ">)]": depth -= 1
        if ch == "," and depth == 0: out.append(cur); cur = ""
        else: cur += ch
    if cur.strip(): out.append(cur)
    return [p.strip() for p in out]

def dump_methods(files):
    ms = {}
    for fn in files:
        for line in open(f"{A}/{fn}"):
            if "@BytecodeOnly" in line: continue
            m = re.match(r"\s*(?:method|ctor)\s+(.*?)\s*(\w+)\((.*)\);\s*$", line)
            if not m: continue
            head, name, params = m.groups()
            names = []
            for p in split_top(params):
                p = re.sub(r"@[\w.]+(\([^)]*\))?\s*", "", p).strip()
                toks = p.split()
                if toks and toks[0] == "optional": toks = toks[1:]
                # a named param is "Type name"; a receiver is just "Type"
                if len(toks) >= 2 and re.match(r"^[a-z]\w*$", toks[-1]): names.append(toks[-1])
            ms.setdefault(name, []).append((names, "@Deprecated" in head, "ExperimentalMaterial3Api" in head, line.strip()[:160]))
    return ms

def stub_funs(path):
    src = open(path).read().replace("->", " ARROW ")
    for m in re.finditer(r"\bfun\s+(?:<[^>]*>\s*)?(?:([\w.<>?]+)\.)?(\w+)\s*\(", src):
        start = m.end(); depth = 1; i = start
        while depth:
            c = src[i]; depth += (c in "([<") - (c in ")]>"); i += 1
        params = src[start:i-1]
        names = []
        for p in split_top(params):
            p = re.sub(r"@\w+(\([^)]*\))?\s*", "", p).strip()
            p = re.sub(r"^(vararg|noinline|crossinline)\s+", "", p)
            mm = re.match(r"^(\w+)\s*:", p)
            if mm: names.append(mm.group(1))
        yield m.group(2), names, src.count("\n", 0, m.start()) + 1

JVM_NAMES = {"enableEdgeToEdge": "enable"}  # EdgeToEdge.kt: @JvmName("enable")

bad = 0; total = 0
for stub, dumps in sorted(DUMPS.items()):
    ms = dump_methods(dumps)
    for name, names, line in stub_funs(os.path.join(STUBS, stub)):
        if name in ("TODO", "invoke", "set", "then", "compareTo", "plus", "minus", "times", "calculateLeftPadding", "calculateTopPadding", "calculateRightPadding", "calculateBottomPadding", "filter", "onCreate", "onNewIntent", "structuralEqualityPolicy", "autoSaver"): continue
        total += 1
        # API files list a function under its JVM name when it has @JvmName.
        cands = ms.get(name, []) + ms.get(JVM_NAMES.get(name, ""), [])
        exact = [c for c in cands if c[0] == names]
        if not exact:
            bad += 1
            print(f"NO EXACT MATCH  {stub}:{line} {name}({', '.join(names)})")
            for c in cands[:6]: print(f"      dump: ({', '.join(c[0])}) dep={c[1]} exp={c[2]}")
print(f"checked {total} stub functions, {bad} without an exact parameter-name match")
sys.exit(1 if bad else 0)
