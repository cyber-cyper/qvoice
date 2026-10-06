package com.riniso.qvoice.ui

import com.riniso.qvoice.ui.VoicesViewModel.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class VoicesViewModelTest {

    private fun ask(slow: Boolean = false, licence: Boolean = false, space: Boolean = true, metered: Boolean = false) =
        VoicesViewModel.nextQuestion(slow, licence, { space }, { metered })

    @Test
    fun questionsComeInOrder() {
        // Slow first: no point accepting a licence for a voice you then skip.
        assertEquals(Question.SLOW, ask(slow = true, licence = true, space = false, metered = true))
        assertEquals(Question.LICENCE, ask(licence = true, space = false, metered = true))
        assertEquals(Question.SPACE, ask(space = false, metered = true))
        assertEquals(Question.MOBILE_DATA, ask(metered = true))
        assertEquals(Question.NONE, ask())
    }

    @Test
    fun spaceAndNetworkAreOnlyCheckedWhenReached() {
        val q = VoicesViewModel.nextQuestion(true, false, { fail("space checked"); true }, { fail("network checked"); false })
        assertEquals(Question.SLOW, q)
    }
}
