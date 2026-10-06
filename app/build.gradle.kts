plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// GPL-3.0 guard. The sherpa-onnx native library compiles in espeak-ng
// (GPL-3.0-or-later), so a distributed QVoice binary is a GPL combined work
// and must point users at its complete source. Refuse to produce a release
// artifact until gradle.properties names that source repository. Debug
// builds (and Android Studio sync) are unaffected.
// ---------------------------------------------------------------------------
val sourceUrl: String = providers.gradleProperty("qvoice.sourceUrl").orNull?.trim().orEmpty()
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (buildingRelease && sourceUrl.isEmpty()) {
    throw GradleException(
        "qvoice.sourceUrl is empty in gradle.properties. Publish the QVoice source " +
            "(e.g. a public GitHub repo) and set its URL before building a release: " +
            "the app bundles GPL-3.0 code (espeak-ng) and must offer its source.",
    )
}

// ---------------------------------------------------------------------------
// Binary files are kept out of the source, zip and git alike (~110 MB that
// comes from its publishers and rarely changes); tools/get-binaries.ps1
// fetches them and checks each one's SHA-256. Fail with a clear message
// instead of a cryptic "could not resolve", a missing-asset crash at
// runtime, or a bloated APK.
// ---------------------------------------------------------------------------
val builtInVoice = "src/main/assets/bundled/kitten-nano-en"
// Path -> exact size in bytes (null: any size, for files the script checks
// by other means, and text files whose line endings a copy may change).
val requiredBinaries: Map<String, Long?> = mapOf(
    "libs/sherpa-onnx-1.13.8.aar" to null,
    "src/main/assets/bundled/espeak-ng-data.zip" to null,
    // The built-in voice, read straight from the APK since 1.0.0. Put there by
    // tools/get-binaries.ps1, which checks every file's SHA-256. The
    // sizes catch a truncated copy: sherpa-onnx has no error handling when it
    // reads from the APK, so a damaged model would crash the app, not fail.
    "$builtInVoice/model.fp32.onnx" to 56_768_002L,
    "$builtInVoice/voices.bin" to 3_276_800L,
    "$builtInVoice/tokens.txt" to null,
    "$builtInVoice/LICENSE" to null,
)
val badBinaries = requiredBinaries.filter { (path, size) ->
    val f = file(path)
    !f.isFile || (size != null && f.length() != size)
}.keys
// Zips of the built-in voice from earlier slices: the build would pack them
// into the APK unused (55 MB). The script deletes them.
val obsoleteBinaries = listOf(
    "src/main/assets/bundled/kitten-nano-en-v0_8-fp32.zip",
    "src/main/assets/bundled/kitten-nano-en-v0_8-int8.zip",
).filter { file(it).exists() }
if (badBinaries.isNotEmpty() || obsoleteBinaries.isNotEmpty()) {
    throw GradleException(
        buildString {
            if (badBinaries.isNotEmpty()) append("Missing or damaged binary file(s) under app/: $badBinaries. ")
            if (obsoleteBinaries.isNotEmpty()) append("Obsolete file(s) under app/ that would bloat the APK: $obsoleteBinaries. ")
            append("See README.md, section 'Binary files'. To put them all in place, run from the project folder: ")
            append("powershell -ExecutionPolicy Bypass -File tools\\get-binaries.ps1")
        },
    )
}

android {
    namespace = "com.riniso.qvoice"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riniso.qvoice"
        minSdk = 26
        targetSdk = 36
        // Owner's rule: versionCode stays 1 (the first Play upload) until the
        // owner says to increase it. Play refuses a second upload with the same
        // code, so every later upload needs that go-ahead first.
        versionCode = 1
        // What Play and the About screen show. Debug builds add the slice
        // (buildTypes.debug below), so a test build is never mistaken for it.
        versionName = "1.0.0"

        buildConfigField("String", "SOURCE_CODE_URL", "\"$sourceUrl\"")

        // 32-bit x86 is dropped on purpose: it only exists on very old
        // emulators and costs ~31 MB of native code per universal APK.
        // x86_64 stays so the app still runs on current emulators/Chromebooks.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            // Which development slice a test build is; release builds say "1.0.0".
            versionNameSuffix = "-slice20"
            // A separate app (com.riniso.qvoice.debug, "QVoice debug"), so test
            // builds install next to the version from Google Play instead of
            // clashing with it: different signing keys can't update each other,
            // and swapping would mean uninstalling, which deletes downloaded
            // voices every time. Its own copy of the launcher shortcuts names
            // this package (src/debug/res/xml/shortcuts.xml).
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        // Built-in Kotlin takes its JVM target from targetCompatibility.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Stored in the APK as they are, not deflated:
        // - .onnx, .bin: the built-in voice's model and speaker table, which
        //   sherpa-onnx reads from the APK at every load. Stored, they are
        //   copied straight out of the memory-mapped APK; deflated, each load
        //   would first inflate them in full (57 MB more memory, and a delay
        //   before the first word). Costs 5 MB of APK size (fp32 weights barely
        //   compress) and nothing in download size (Play compresses downloads).
        // - .zip: the eSpeak NG data, already compressed.
        // AGP applies the same list to app bundles.
        noCompress += listOf("onnx", "bin", "zip")
    }

    packaging {
        jniLibs {
            // The sherpa-onnx AAR carries its C and C++ API libraries too.
            // Only libsherpa-onnx-jni.so is loaded (System.loadLibrary in
            // OfflineTts), and it links nothing but libonnxruntime.so
            // (checked with readelf). Dropping them saves ~4.9 MB per ABI.
            excludes += setOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
        resources {
            // Commons Compress, IO, Lang and Codec each carry a Java 9 module
            // descriptor at this path; four copies collide when the APK's Java
            // resources are merged, and ART never reads them.
            excludes += "META-INF/versions/9/module-info.class"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // sherpa-onnx 1.13.8 (Apache-2.0; statically contains espeak-ng GPL-3.0
    // and ONNX Runtime MIT). Vendored because it is not on Maven Central.
    // Upstream: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
    // sha256 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // android.jar's org.json is a stub on the JVM; tests need the real one.
    testImplementation(libs.org.json)
}
