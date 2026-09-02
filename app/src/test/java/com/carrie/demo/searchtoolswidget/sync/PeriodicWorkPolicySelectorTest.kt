package com.carrie.demo.searchtoolswidget.sync

import androidx.work.ExistingPeriodicWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodicWorkPolicySelectorTest {

    @Test
    fun `same frequency keeps the existing periodic schedule`() {
        assertEquals(
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkPolicySelector.select(storedMinutes = 60L, requestedMinutes = 60L),
        )
    }

    @Test
    fun `changed frequency updates the existing periodic schedule`() {
        assertEquals(
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkPolicySelector.select(storedMinutes = 60L, requestedMinutes = 30L),
        )
    }
}
