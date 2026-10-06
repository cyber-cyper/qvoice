package com.riniso.qvoice.service

/**
 * What Android's TTS settings "Play" button and QVoice's own preview read
 * aloud. Kept short and simple so it sounds right in every voice; the app name
 * is transliterated in non-Latin scripts because a Tamil or Hindi voice would
 * otherwise spell out the Latin letters.
 */
object SampleTexts {
    private val samples = mapOf(
        "en" to "Hello! This is QVoice, a natural-sounding voice that works offline.",
        "hi" to "नमस्ते, यह क्यूवॉइस की आवाज़ है।",
        "ta" to "வணக்கம், இது க்யூவாய்ஸ் குரல்.",
        "ml" to "നമസ്കാരം, ഇത് ക്യൂവോയ്സ് ശബ്ദമാണ്.",
        "es" to "Hola, esta es la voz de QVoice.",
        "fr" to "Bonjour, voici la voix de QVoice.",
        "de" to "Hallo, das ist die Stimme von QVoice.",
        "it" to "Ciao, questa è la voce di QVoice.",
        "pt" to "Olá, esta é a voz do QVoice.",
        // Voice-library languages (Kokoro, Supertonic): a plain greeting,
        // without the app name, which these voices would spell letter by letter.
        "ar" to "أهلاً وسهلاً، كيف يمكنني مساعدتك؟",
        "bg" to "Здравейте! Приятно ми е да се запознаем.",
        "cs" to "Dobrý den, těší mě.",
        "da" to "Hej, rart at møde dig.",
        "el" to "Γεια σας, χάρηκα για τη γνωριμία.",
        "et" to "Tere, meeldiv tutvuda.",
        "fi" to "Hei, hauska tavata.",
        "hr" to "Dobar dan, drago mi je.",
        "hu" to "Jó napot, örülök, hogy megismerhetem.",
        "id" to "Halo, senang bertemu dengan Anda.",
        "ja" to "こんにちは、はじめまして。",
        "ko" to "안녕하세요, 만나서 반갑습니다.",
        "lt" to "Labas, malonu susipažinti.",
        "lv" to "Sveiki, prieks iepazīties.",
        "nl" to "Hallo, leuk je te ontmoeten.",
        "pl" to "Dzień dobry, miło mi.",
        "ro" to "Bună ziua, mă bucur să vă cunosc.",
        "ru" to "Здравствуйте! Приятно познакомиться.",
        "sk" to "Dobrý deň, teší ma.",
        "sl" to "Dober dan, me veseli.",
        "sv" to "Hej, trevligt att träffas.",
        "tr" to "Merhaba, tanıştığımıza memnun oldum.",
        "uk" to "Добрий день! Приємно познайомитися.",
        "vi" to "Xin chào, rất vui được gặp bạn.",
        "zh" to "你好，很高兴认识你。",
    )

    /** Languages with their own sample (the rest get English). */
    val languages: Set<String> get() = samples.keys

    fun forLanguage(language: String?): String =
        samples[TtsLocales.toIso2Language(language)] ?: samples.getValue("en")
}
