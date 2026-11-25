// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Riniso. Unit tests of SpeechRouting (JUnit 4). Copy into
// app/src/test/java/com/riniso/speech/ next to the helper.
package com.riniso.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechRoutingTest {

    private fun v(
        name: String,
        language: String = "en",
        country: String = "US",
        gender: String? = null,
        latency: Int = 300,
        network: Boolean = false,
        notInstalled: Boolean = false,
    ) = SpeechRouting.VoiceInfo(
        name, language, country,
        gender?.let { setOf(SpeechRouting.GENDER_FEATURE_PREFIX + it) }.orEmpty(),
        latency, network, notInstalled,
    )

    private val kitten = listOf(
        v("kitten-nano-en#rosie", gender = "female"),
        v("kitten-nano-en#bruno", gender = "male"),
    )
    private val kokoro = listOf(
        v("kokoro-multi-lang-v1_0#af_heart", gender = "female", latency = 400),
        v("kokoro-multi-lang-v1_0#am_adam", gender = "male", latency = 400),
        v("kokoro-multi-lang-v1_0#bf_emma", country = "GB", gender = "female", latency = 400),
    )

    @Test
    fun theEngineFollowsTheChoiceInstallAndLanguage() {
        val qv = SpeechRouting.Engine.QVOICE
        val sys = SpeechRouting.Engine.SYSTEM
        assertEquals(qv, SpeechRouting.engineFor(EngineChoice.AUTOMATIC, true, true))
        assertEquals("not known yet: try QVoice, it refuses what it can't read", qv, SpeechRouting.engineFor(EngineChoice.AUTOMATIC, true, null))
        assertEquals("Tamil", sys, SpeechRouting.engineFor(EngineChoice.AUTOMATIC, true, false))
        assertEquals(sys, SpeechRouting.engineFor(EngineChoice.AUTOMATIC, false, true))
        assertEquals(sys, SpeechRouting.engineFor(EngineChoice.SYSTEM, true, true))
    }

    @Test
    fun withoutWishesTheEngineChooses() {
        assertNull(SpeechRouting.pickVoice(kitten, "en", "US", VoiceGender.ANY, null, "kitten-nano-en#rosie", fastStart = false))
    }

    @Test
    fun aPickedVoiceWinsWhileItExists() {
        assertEquals("kitten-nano-en#bruno", SpeechRouting.pickVoice(kitten, "en", "US", VoiceGender.FEMALE, "kitten-nano-en#bruno", null, false))
        // Deleted meanwhile: back to the other rules.
        assertNull(SpeechRouting.pickVoice(kitten, "en", "US", VoiceGender.ANY, "gone#x", null, false))
    }

    @Test
    fun aGenderWishKeepsTheDefaultsModel() {
        val all = kokoro + kitten
        assertEquals("kokoro-multi-lang-v1_0#am_adam", SpeechRouting.pickVoice(all, "en", "US", VoiceGender.MALE, null, "kokoro-multi-lang-v1_0#af_heart", false))
        assertEquals("the default already fits", "kitten-nano-en#rosie", SpeechRouting.pickVoice(all, "en", "US", VoiceGender.FEMALE, null, "kitten-nano-en#rosie", false))
        // No voice of that gender for the language: the engine's choice.
        assertNull(SpeechRouting.pickVoice(listOf(v("a#x", gender = "female")), "en", "US", VoiceGender.MALE, null, "a#x", false))
    }

    @Test
    fun theUsersRegionComesFirst() {
        assertEquals("kokoro-multi-lang-v1_0#bf_emma", SpeechRouting.pickVoice(kokoro, "en", "GB", VoiceGender.FEMALE, null, null, false))
    }

    @Test
    fun fastStartTradesASlowVoiceForAFastOne() {
        val all = kokoro + kitten
        assertEquals("kitten-nano-en#bruno", SpeechRouting.pickVoice(all, "en", "US", VoiceGender.MALE, null, "kokoro-multi-lang-v1_0#af_heart", fastStart = true))
        assertEquals("kitten-nano-en#rosie", SpeechRouting.pickVoice(all, "en", "US", VoiceGender.ANY, null, "kokoro-multi-lang-v1_0#af_heart", fastStart = true))
        // Already fast: nothing changes.
        assertNull(SpeechRouting.pickVoice(all, "en", "US", VoiceGender.ANY, null, "kitten-nano-en#rosie", fastStart = true))
        // Only slow voices: keep them.
        assertNull(SpeechRouting.pickVoice(kokoro, "en", "US", VoiceGender.ANY, null, "kokoro-multi-lang-v1_0#af_heart", fastStart = true))
    }

    @Test
    fun networkAndMissingVoicesAndOtherLanguagesAreNeverPicked() {
        val voices = listOf(
            v("net#f", gender = "female", network = true),
            v("missing#f", gender = "female", notInstalled = true),
            v("tamil#f", language = "ta", country = "IN", gender = "female"),
        )
        assertNull(SpeechRouting.pickVoice(voices, "en", "US", VoiceGender.FEMALE, "net#f", null, false))
    }

    @Test
    fun labelsAndModelsComeFromQVoiceNames() {
        assertEquals("Rosie", SpeechRouting.labelOf("kitten-nano-en#rosie"))
        assertEquals("Heart", SpeechRouting.labelOf("kokoro-multi-lang-v1_0#af_heart"))
        assertEquals("Kristin", SpeechRouting.labelOf("vits-piper-en_US-kristin-medium#kristin"))
        assertEquals("F1", SpeechRouting.labelOf("sherpa-onnx-supertonic-3-tts-int8-2026-05-11#f1@hi-IN"))
        assertEquals("en-us-x-iob-local", SpeechRouting.labelOf("en-us-x-iob-local"))
        assertEquals("kokoro-multi-lang-v1_0", SpeechRouting.modelOf("kokoro-multi-lang-v1_0#af_heart"))
        assertEquals("vits-piper-en_US-kristin-medium", SpeechRouting.modelOf("vits-piper-en_US-kristin-medium"))
    }
}
