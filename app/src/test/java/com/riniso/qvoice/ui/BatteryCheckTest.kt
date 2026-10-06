package com.riniso.qvoice.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BatteryCheck's rule: Home warns only when Android really holds QVoice back. */
class BatteryCheckTest {

    @Test
    fun everyOrdinaryBucketIsFine() {
        // Active, working set, frequent, rare: Android's normal buckets.
        for (bucket in listOf(10, 20, 30, 40)) assertFalse(BatteryCheck.restricted(false, bucket))
        assertFalse(BatteryCheck.restricted(false, null))
    }

    @Test
    fun theRestrictedBucketOrTheUsersRestrictionWarns() {
        assertTrue(BatteryCheck.restricted(false, BatteryCheck.BUCKET_RESTRICTED))
        assertTrue(BatteryCheck.restricted(true, 10))
        assertTrue(BatteryCheck.restricted(true, null))
    }
}
