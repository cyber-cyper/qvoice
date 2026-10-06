package com.riniso.qvoice.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links and the feedback email's details: what support gets, and nothing else. */
class LinksTest {

    private fun body(engine: String? = "com.google.android.tts", manufacturer: String = "samsung", model: String = "SM-M315F") =
        FeedbackEmail.body(
            versionName = "1.0.0",
            versionCode = 1,
            androidRelease = "12",
            sdkInt = 31,
            manufacturer = manufacturer,
            model = model,
            preferredEngine = engine,
            ownPackage = "com.riniso.qvoice",
        )

    @Test
    fun theDetailsSitBelowRoomForTheMessage() {
        val text = body()
        // The user writes above the details: the body starts with empty lines.
        assertTrue(text.startsWith("\n\n" + FeedbackEmail.DIVIDER + "\n"))
        assertEquals(
            listOf(
                "QVoice 1.0.0 (1)",
                "Android 12 (API 31)",
                "Phone: Samsung SM-M315F",
                "Preferred engine: com.google.android.tts",
            ),
            text.substringAfter(FeedbackEmail.DIVIDER + "\n").trimEnd().lines(),
        )
    }

    @Test
    fun theEngineIsNamedPlainlyWhenItIsQVoiceOrUnknown() {
        assertTrue(body(engine = "com.riniso.qvoice").contains("Preferred engine: QVoice\n"))
        assertTrue(body(engine = " com.riniso.qvoice ").contains("Preferred engine: QVoice\n"))
        assertTrue(body(engine = null).contains("Preferred engine: unknown\n"))
        assertTrue(body(engine = "  ").contains("Preferred engine: unknown\n"))
    }

    @Test
    fun theMakerAppearsOnce() {
        assertEquals("Samsung SM-M315F", FeedbackEmail.deviceName("samsung", "SM-M315F"))
        assertEquals("Google Pixel 8", FeedbackEmail.deviceName("Google", "Pixel 8"))
        assertEquals("HMD Global Nokia 5.4", FeedbackEmail.deviceName("HMD Global", "Nokia 5.4"))
        assertEquals("OnePlus CPH2449", FeedbackEmail.deviceName("OnePlus", "CPH2449"))
        // A model name that already starts with the maker isn't doubled.
        assertEquals("motorola edge 40", FeedbackEmail.deviceName("motorola", "motorola edge 40"))
        assertEquals("Xiaomi", FeedbackEmail.deviceName("xiaomi", " "))
        assertEquals("M2101K6G", FeedbackEmail.deviceName("", "M2101K6G"))
    }

    @Test
    fun theDetailsNeverCarryMoreThanTheirFourLines() {
        // A guard for later edits: anything added here reaches every email.
        val details = body().substringAfter(FeedbackEmail.DIVIDER + "\n").trimEnd().lines()
        assertEquals(4, details.size)
        assertFalse(body().contains("null"))
    }

    @Test
    fun linksAreHttpsAndPointAtQVoice() {
        assertEquals("https://play.google.com/store/apps/details?id=com.riniso.qvoice", Links.playListing(Links.PLAY_APP_ID))
        assertTrue(Links.PRIVACY_POLICY.startsWith("https://"))
        assertTrue(Links.PRIVACY_POLICY.endsWith("/qvoice/privacy.html"))
        assertTrue(Links.SUPPORT_EMAIL.matches(Regex("[^@\\s]+@[^@\\s]+\\.[a-z]+")))
    }
}
