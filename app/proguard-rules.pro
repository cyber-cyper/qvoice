# ---------------------------------------------------------------------------
# sherpa-onnx JNI bindings
# ---------------------------------------------------------------------------
# The native code reads the Kotlin config classes' fields BY NAME through JNI
# (OfflineTtsConfig.maxNumSentences, OfflineTtsKittenModelConfig.voices, ...).
# If R8 renames or strips any of them the release build crashes with
# NoSuchFieldError / SIGABRT inside the .so, and the stack trace never
# mentions R8. Keep the whole package exactly as shipped.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# The streaming callback: native code looks the method up by name and JNI
# signature, invoke([F)Ljava/lang/Integer;, on the callback's own class (see
# SherpaChunkCallback). R8 must neither rename nor remove it, nor fold it
# into the erased invoke(Object) bridge.
-keepclassmembers class com.riniso.qvoice.engine.SherpaChunkCallback {
    public java.lang.Integer invoke(float[]);
}

# ---------------------------------------------------------------------------
# Apache Commons Compress (+ commons-io, commons-lang3, commons-codec)
# ---------------------------------------------------------------------------
# QVoice only uses the bzip2 and tar classes, which need none of these.
# Commons Compress references its OPTIONAL codecs (xz, zstd, brotli, pack200's
# ASM) and commons-lang3 two JDK types Android doesn't have; R8 fails a
# release build on missing classes unless told they are expected.
# Found by scanning every class's constant pool against android-35.jar.
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn java.lang.reflect.AnnotatedType
