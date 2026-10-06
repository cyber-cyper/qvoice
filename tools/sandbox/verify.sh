#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Verification used by the AUTHORING environment (no Android SDK, no Gradle,
# no Google Maven). You don't need to run this — use `gradle test` and
# `python tools/check_project.py`. Kept so any future session can rebuild the
# same checks from scratch.
#
# What it does:
#   0. compiles the vendored Java (Sonic) with javac, lint warnings as errors;
#   1. compiles every non-Compose Kotlin file with kotlinc 2.2.10 (the Kotlin
#      AGP 9.2 bundles) against the public android-35 API and the real
#      sherpa-onnx 1.13.8 classes, warnings as errors;
#   2. compiles and runs the unit tests (JUnit 4 stand-in + JSON-java);
#   3. runs tools/check_project.py;
#   4. type-checks the Compose screens against stubs of the real Compose API
#      (tools/sandbox/compose-stubs), then runs the Compose compiler plugin's
#      checks on them.
#
# Toolchain sources (all reachable from the sandbox):
#   kotlinc   https://github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip
#   android   https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar
#   sherpa    classes.jar inside app/libs/sherpa-onnx-1.13.8.aar
#   org.json  JSON-java sources, https://raw.githubusercontent.com/stleary/JSON-java/master/src/main/java/org/json/
#   JUnit     tools/sandbox/junitstub (same signatures as JUnit 4's Test/Assert)
#   commons   commons-compress 1.27.1 + commons-io/lang3/codec jars (Maven Central)
# ---------------------------------------------------------------------------
set -euo pipefail
T=${QVOICE_TOOLS:-/tmp/claude-0/tools}
B=${QVOICE_BUILD:-/tmp/claude-0/build}
KC=$T/kc2210/kotlinc/bin/kotlinc
# Apache Commons Compress 1.27.1 and its three runtime dependencies (the
# versions Gradle resolves may be newer patch releases; the API used is stable).
CC="$T/cc/commons-compress-1.27.1.jar:$T/cc/commons-io-2.18.0.jar:$T/cc/commons-lang3-3.17.0.jar:$T/cc/commons-codec-1.17.2.jar"
CP_MAIN="$T/android-35.jar:$T/sherpa-onnx-1.13.8-classes.jar:$T/kc2210/kotlinc/lib/kotlinx-coroutines-core-jvm.jar:$CC:$B/r:$B/java"
rm -rf "$B/main" "$B/test" "$B/r" "$B/java" && mkdir -p "$B/main" "$B/test" "$B/r" "$B/java"
# Vendored Java sources (Sonic) first: the Kotlin code calls them.
javac -Xlint:all -Werror -d "$B/java" $(find app/src/main/java -name "*.java")
# Stand-in for the R class AGP generates: one int per resource name, enough
# to type-check non-Compose code that uses R.string.* etc.
python3 - "$B/r" <<'PY'
import os, re, sys, xml.etree.ElementTree as ET
out = sys.argv[1]; res = "app/src/main/res"; kinds = {}
for d in os.listdir(res):
    kind = d.split("-")[0]
    for f in os.listdir(os.path.join(res, d)):
        if kind == "values":
            for el in ET.parse(os.path.join(res, d, f)).getroot():
                if el.tag in ("string", "color", "style", "dimen", "bool", "integer", "plurals"):
                    kinds.setdefault(el.tag, set()).add(el.get("name").replace(".", "_"))
        else:
            kinds.setdefault(kind, set()).add(os.path.splitext(f)[0])
os.makedirs(os.path.join(out, "com/riniso/qvoice"), exist_ok=True)
with open(os.path.join(out, "com/riniso/qvoice/R.java"), "w") as fh:
    fh.write("package com.riniso.qvoice;\npublic final class R {\n")
    n = 0x7f000000
    for kind, names in sorted(kinds.items()):
        fh.write(f"  public static final class {kind} {{\n")
        for name in sorted(names):
            n += 1; fh.write(f"    public static final int {name} = {n};\n")
        fh.write("  }\n")
    fh.write("}\n")
PY
javac -d "$B/r" "$B/r/com/riniso/qvoice/R.java"
"$KC" -jvm-target 17 -no-reflect -Werror -classpath "$CP_MAIN" -d "$B/main" \
  $(find app/src/main/java -name "*.kt" -not -path "*/ui/*") \
  app/src/main/java/com/riniso/qvoice/ui/TtsClient.kt \
  app/src/main/java/com/riniso/qvoice/ui/HomeViewModel.kt \
  app/src/main/java/com/riniso/qvoice/ui/VoicesViewModel.kt \
  app/src/main/java/com/riniso/qvoice/ui/ReadableWidth.kt \
  app/src/main/java/com/riniso/qvoice/ui/Links.kt \
  app/src/main/java/com/riniso/qvoice/ui/ClipboardText.kt \
  app/src/main/java/com/riniso/qvoice/ui/BatteryCheck.kt \
  $(find tools/sandbox/stubs -name "*.kt")
"$KC" -jvm-target 17 -no-reflect -Werror -Xfriend-paths="$B/main" \
  -classpath "$T/orgjson/org-json.jar:$B/main:$T/junitstub/junit-stub.jar:$CP_MAIN" \
  -d "$B/test" $(find app/src/test/java -name "*.kt")
java -cp "$T/orgjson/org-json.jar:$B/test:$B/main:$B/java:$T/junitstub/junit-stub.jar:$T/kc2210/kotlinc/lib/kotlin-stdlib.jar:$T/sherpa-onnx-1.13.8-classes.jar:$T/kc2210/kotlinc/lib/kotlinx-coroutines-core-jvm.jar:$CC" runner.Run "$B/test"
python3 tools/check_project.py

# 4. The Compose screens, against stubs made from the real API signature files
#    (tools/sandbox/compose-stubs/README.md): the only compile they get here,
#    since the real Compose libraries are on Google Maven.
#    a) all sources with the stubs, warnings as errors: types, names, overloads,
#       opt-ins, exhaustive whens, R references;
#    b) again with the Compose compiler plugin, for its own checks ("@Composable
#       invocations can only happen from ..."). The plugin's code generation
#       needs the real runtime and stops after those checks; that stop is expected.
rm -rf "$B/cstubs" "$B/ui" "$B/ui2" && mkdir -p "$B/cstubs" "$B/ui" "$B/ui2"
VERSION_NAME=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)$(sed -n 's/^ *versionNameSuffix = "\(.*\)".*/\1/p' app/build.gradle.kts)
cat > "$B/r/com/riniso/qvoice/BuildConfig.java" <<JAVA
package com.riniso.qvoice;
public final class BuildConfig {
  public static final boolean DEBUG = true;
  public static final String APPLICATION_ID = "com.riniso.qvoice";
  public static final String BUILD_TYPE = "debug";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "$VERSION_NAME";
  public static final String SOURCE_CODE_URL = "";
}
JAVA
javac -d "$B/r" "$B/r/com/riniso/qvoice/BuildConfig.java"
"$KC" -jvm-target 17 -no-reflect -classpath "$T/android-35.jar:$T/kc2210/kotlinc/lib/kotlinx-coroutines-core-jvm.jar" \
  -d "$B/cstubs" tools/sandbox/compose-stubs/*.kt $(find tools/sandbox/stubs/androidx -name "*.kt")
"$KC" -jvm-target 17 -no-reflect -Werror -classpath "$CP_MAIN:$B/cstubs" -d "$B/ui" $(find app/src/main/java -name "*.kt")
set +e
PLUGIN_LOG=$("$KC" -jvm-target 17 -no-reflect -Xplugin="$T/kc2210/kotlinc/lib/compose-compiler-plugin.jar" \
  -classpath "$CP_MAIN:$B/cstubs" -d "$B/ui2" $(find app/src/main/java -name "*.kt") 2>&1)
set -e
if printf '%s\n' "$PLUGIN_LOG" | grep -E "\.kt:[0-9]+:[0-9]+: (error|warning):"; then
  echo "Compose compiler plugin reported the problems above"; exit 1
fi
if ! printf '%s\n' "$PLUGIN_LOG" | grep -q "IncompatibleComposeRuntimeVersionException"; then
  printf '%s\n' "$PLUGIN_LOG" | grep -v JAVA_TOOL_OPTIONS | head -20
  echo "Compose compiler plugin: unexpected result (see above)"; exit 1
fi
echo "Compose screens: type-checked against the API stubs, plugin checks passed"

# Optional: install every REAL catalogue archive with the app's installer and
# compare with the archive contents (needs the downloaded .tar.bz2 files).
if [ -n "${QVOICE_ARCHIVES:-}" ]; then
  mkdir -p "$B/realinstall"
  "$KC" -jvm-target 17 -no-reflect -Werror -classpath "$B/main:$T/orgjson/org-json.jar:$CC:$T/android-35.jar" \
    -d "$B/realinstall" tools/sandbox/realinstall/RealInstall.kt
  java -cp "$B/realinstall:$B/main:$T/orgjson/org-json.jar:$CC:$T/kc2210/kotlinc/lib/kotlin-stdlib.jar" \
    RealInstallKt app/src/main/assets/catalog.json "$QVOICE_ARCHIVES" "$B/realinstall-work"
fi
