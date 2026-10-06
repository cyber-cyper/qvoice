package com.riniso.qvoice.engine

import com.riniso.qvoice.voices.AssetSource
import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.InstalledVoice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EngineTest {

    private class FakeModel(val name: String, val log: MutableList<String>) : SpeechModel {
        override val sampleRate = 24_000
        override val numSpeakers = 8
        @Volatile var released = false
        override fun generate(text: String, sid: Int, speed: Float, language: String?, opening: Boolean, onChunk: (FloatArray) -> Boolean) {
            check(!released) { "generate on released model $name" }
            onChunk(FloatArray(4))
        }
        override fun release() {
            released = true
            log += "release:$name"
        }
    }

    private fun voice(id: String) =
        InstalledVoice(BundledVoices.KITTEN_NANO_EN.copy(id = id), File(id), ready = true)

    private fun host(
        log: MutableList<String>,
        max: Int = 2,
        maxBytes: Long = Long.MAX_VALUE,
        size: (InstalledVoice) -> Long = { 0L },
    ) = EngineHost(
        loader = { v, variant ->
            log += "load:${v.id}${variant?.let { "|$it" } ?: ""}"
            FakeModel(v.id, log)
        },
        maxLoaded = max,
        maxBytes = maxBytes,
        sizeOf = size,
    )

    @Test
    fun loadsOnceAndCaches() {
        val log = ArrayList<String>()
        val h = host(log)
        h.withModel(voice("a"), null) { }
        h.withModel(voice("a"), null) { }
        assertEquals(listOf("load:a"), log)
        assertTrue(h.isLoaded("a", null))
    }

    @Test
    fun evictsLeastRecentlyUsed() {
        val log = ArrayList<String>()
        val h = host(log, max = 2)
        h.withModel(voice("a"), null) { }
        h.withModel(voice("b"), null) { }
        h.withModel(voice("a"), null) { } // a becomes most recent
        h.withModel(voice("c"), null) { model -> model.generate("x", 0, 1f, null) { true } }
        assertTrue(log.contains("release:b"))
        assertFalse(log.contains("release:a"))
        assertFalse(log.contains("release:c"))
    }

    /**
     * The guarantee EngineHost exists for: another thread loading a model
     * (which may evict) must wait until a running synthesis is finished, so
     * the model in use is never released under it. Proven by mutation: with
     * the block run outside the lock, this test fails.
     */
    @Test
    fun concurrentLoadNeverReleasesTheModelInUse() {
        val log = java.util.Collections.synchronizedList(ArrayList<String>())
        val h = host(log, max = 1)
        val inside = CountDownLatch(1)
        val otherLoadAttempted = CountDownLatch(1)
        var failure: Throwable? = null
        val synth = Thread {
            try {
                h.withModel(voice("a"), null) { model ->
                    inside.countDown()
                    otherLoadAttempted.await(5, TimeUnit.SECONDS)
                    Thread.sleep(200) // give a broken implementation time to evict
                    model.generate("x", 0, 1f, null) { true }
                }
            } catch (t: Throwable) {
                failure = t
            }
        }
        synth.start()
        assertTrue(inside.await(5, TimeUnit.SECONDS))
        val loader = Thread {
            otherLoadAttempted.countDown()
            h.withModel(voice("b"), null) { }
        }
        loader.start()
        synth.join(5_000)
        loader.join(5_000)
        assertEquals(null, failure)
        // Room is made before loading, so a is released before b loads.
        assertEquals(listOf("load:a", "release:a", "load:b"), log.toList())
    }

    /**
     * The CPU-threads setting: a model cached with another thread count is
     * released and loaded again on its next use (under the engine lock, so
     * never while it is speaking).
     */
    @Test
    fun changedLoadSettingsReloadOnNextUse() {
        val log = ArrayList<String>()
        var threads = 4
        val h = EngineHost(
            object : EngineHost.ModelLoader {
                override fun load(voice: InstalledVoice, variant: String?): SpeechModel {
                    log += "load:${voice.id}@$threads"
                    return FakeModel(voice.id, log)
                }

                override val signature: String get() = "threads=$threads"
            },
        )
        h.withModel(voice("a"), null) { }
        h.withModel(voice("a"), null) { }
        threads = 2
        h.withModel(voice("a"), null) { model -> model.generate("x", 0, 1f, null) { true } }
        h.withModel(voice("a"), null) { }
        assertEquals(listOf("load:a@4", "release:a", "load:a@2"), log)
    }

    @Test
    fun byteBudgetMakesRoomBeforeLoading() {
        val log = ArrayList<String>()
        val h = host(log, max = 3, maxBytes = 100, size = { 60L })
        h.withModel(voice("a"), null) { }
        h.withModel(voice("b"), null) { }
        // The peak is one model over the budget, never two.
        assertEquals(listOf("load:a", "release:a", "load:b"), log)
    }

    @Test
    fun kokoroLanguagesShareTheBudget() {
        val log = ArrayList<String>()
        val h = host(log, max = 3, maxBytes = 500, size = { if (it.id == "kokoro") 360L else 60L })
        h.withModel(voice("kokoro"), "en-US") { }
        h.withModel(voice("piper"), null) { } // 420 fits
        h.withModel(voice("kokoro"), "hi-IN") { } // another 360: only the least recent has to go
        assertEquals(listOf("load:kokoro|en-US", "load:piper", "release:kokoro", "load:kokoro|hi-IN"), log)
        assertTrue(h.isLoaded("piper", null))
        assertFalse(h.isLoaded("kokoro", "en-US"))
    }

    @Test
    fun modelBiggerThanTheBudgetStillLoadsAlone() {
        val log = ArrayList<String>()
        val h = host(log, maxBytes = 100, size = { if (it.id == "big") 150L else 10L })
        h.withModel(voice("small"), null) { }
        h.withModel(voice("big"), null) { model -> model.generate("x", 0, 1f, null) { true } }
        assertEquals(listOf("load:small", "release:small", "load:big"), log)
        assertTrue(h.isLoaded("big", null))
        // Releasing a voice returns its bytes to the budget.
        h.release("big")
        h.withModel(voice("small"), null) { }
        h.withModel(voice("other"), null) { }
        assertTrue(h.isLoaded("small", null))
    }

    @Test
    fun budgetScalesWithRamWithinBounds() {
        val mib = 1024L * 1024
        assertEquals(256 * mib, EngineHost.budgetFor(1024 * mib))
        assertEquals(512 * mib, EngineHost.budgetFor(3072 * mib))
        assertEquals(4096 * mib / 6, EngineHost.budgetFor(4096 * mib))
        assertEquals(768 * mib, EngineHost.budgetFor(12288 * mib))
        assertEquals(256 * mib, EngineHost.budgetFor(0)) // RAM unknown
    }

    @Test
    fun footprintCountsTheFilesTheEngineLoads() {
        val dir = java.nio.file.Files.createTempDirectory("qv-footprint").toFile()
        try {
            mapOf("model.onnx" to 100, "voices.bin" to 20, "tokens.txt" to 5, "lexicon-us-en.txt" to 7, "lexicon-gb-en.txt" to 7, "README.md" to 999)
                .forEach { (name, size) -> File(dir, name).writeBytes(ByteArray(size)) }
            val m = BundledVoices.KITTEN_NANO_EN.copy(
                family = com.riniso.qvoice.voices.VoiceFamily.KOKORO,
                files = com.riniso.qvoice.voices.VoiceFiles(model = "model.onnx", tokens = "tokens.txt", voices = "voices.bin"),
                languageConfig = mapOf(
                    "en-US" to com.riniso.qvoice.voices.LanguageConfig("en-us", listOf("lexicon-us-en.txt")),
                    "en-GB" to com.riniso.qvoice.voices.LanguageConfig("en", listOf("lexicon-gb-en.txt", "lexicon-zh.txt")),
                ),
            )
            // README isn't loaded; the missing zh lexicon counts 0.
            assertEquals(139L, ModelFootprint.estimate(InstalledVoice(m, dir, ready = true)))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The built-in voice's files are in the APK, so its size comes from there. */
    @Test
    fun footprintOfABuiltInVoiceComesFromTheApk() {
        val builtIn = BundledVoices.DEFAULT.single()
        val sizes = mapOf("model.fp32.onnx" to 100L, "voices.bin" to 20L, "tokens.txt" to 5L)
            .mapKeys { "${builtIn.assetDir}/${it.key}" }
        val assets = object : AssetSource {
            override fun open(path: String): InputStream = throw IOException("sizes come from the APK index")
            override fun length(path: String): Long = sizes[path] ?: -1L
        }
        val voice = InstalledVoice(builtIn.manifest, File("never-read"), ready = true, assetDir = builtIn.assetDir)
        assertEquals(125L, ModelFootprint.estimate(voice, assets))
        // Without the assets nothing can be measured (the load itself reports that).
        assertEquals(0L, ModelFootprint.estimate(voice))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityIsRejected() {
        host(ArrayList(), max = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroByteBudgetIsRejected() {
        host(ArrayList(), maxBytes = 0)
    }

    @Test
    fun variantsAreSeparateModels() {
        val log = ArrayList<String>()
        val h = host(log)
        h.withModel(voice("k"), "en") { }
        h.withModel(voice("k"), "hi") { }
        assertEquals(listOf("load:k|en", "load:k|hi"), log)
        h.release("k")
        assertTrue(log.containsAll(listOf("release:k", "release:k")))
        assertFalse(h.isLoaded("k", "en"))
    }

    @Test
    fun warmUpAndTrimSkipWhileSynthesizing() {
        val log = ArrayList<String>()
        val h = host(log)
        val inside = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val synth = Thread {
            h.withModel(voice("a"), null) {
                inside.countDown()
                finish.await(5, TimeUnit.SECONDS)
            }
        }
        synth.start()
        assertTrue(inside.await(5, TimeUnit.SECONDS))
        assertFalse("warm-up must not wait behind a synthesis", h.warmUp(voice("b"), null))
        assertFalse("trim must not release a model mid-synthesis", h.releaseIdle())
        finish.countDown()
        synth.join(5_000)
        assertTrue(h.warmUp(voice("b"), null))
        assertTrue(h.releaseIdle())
        assertTrue(log.containsAll(listOf("release:a", "release:b")))
    }

    @Test
    fun loaderFailureBecomesEngineLoadException() {
        val h = EngineHost({ _, _ -> throw IllegalStateException("corrupt model") })
        try {
            h.withModel(voice("a"), null) { }
            fail("expected EngineLoadException")
        } catch (e: EngineLoadException) {
            assertTrue(e.message.orEmpty().contains("corrupt model"))
        }
        assertFalse(h.warmUp(voice("a"), null))
    }

    @Test
    fun pcmEncodingClipsAndRounds() {
        val out = ByteArray(10)
        Pcm16.encode(floatArrayOf(0f, 1f, -1f, 2f, Float.NaN), 0, 5, out)
        assertArrayEquals(
            byteArrayOf(0, 0, 0xFF.toByte(), 0x7F, 0x00, 0x80.toByte(), 0xFF.toByte(), 0x7F, 0, 0),
            out,
        )
        val half = ByteArray(2)
        Pcm16.encode(floatArrayOf(0.5f), 0, 1, half)
        assertEquals(16384, (half[0].toInt() and 0xFF) or (half[1].toInt() shl 8))
    }

    @Test
    fun streamerChunksToBufferLimitAndStopsWhenRefused() {
        val sizes = ArrayList<Int>()
        // Odd limit: rounded down to whole 16-bit samples (6 bytes = 3 samples).
        val streamer = PcmStreamer(7) { _, _, len -> sizes += len; true }
        assertTrue(streamer.write(FloatArray(8)))
        assertEquals(listOf(6, 6, 4), sizes)
        assertEquals(16L, streamer.bytesWritten)

        var calls = 0
        val refusing = PcmStreamer(4) { _, _, _ -> calls++; false }
        assertFalse(refusing.write(FloatArray(10)))
        assertEquals(1, calls)
    }

    /**
     * sherpa-onnx's native code calls GetMethodID(cls, "invoke",
     * "([F)Ljava/lang/Integer;") on the callback's class. A lambda doesn't
     * have that method on Android (slice-2 crash); SherpaChunkCallback must.
     */
    @Test
    fun sherpaCallbackHasTheMethodTheNativeCodeLooksUp() {
        val method = SherpaChunkCallback::class.java.getMethod("invoke", FloatArray::class.java)
        assertEquals(Int::class.javaObjectType, method.returnType) // java.lang.Integer, not int
        val keepGoing = SherpaChunkCallback { true }
        assertEquals(1, method.invoke(keepGoing, FloatArray(4)))
        assertFalse(keepGoing.stopped)
        val stop = SherpaChunkCallback { false }
        assertEquals(0, stop(FloatArray(4)))
        assertTrue(stop.stopped)
    }

    @Test
    fun threadPolicyStaysWithinFastCores() {
        assertEquals(1, ThreadPolicy.defaultThreads(2))
        assertEquals(2, ThreadPolicy.defaultThreads(4))
        assertEquals(4, ThreadPolicy.defaultThreads(8))
    }
}
