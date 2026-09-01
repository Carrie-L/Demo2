package com.carrie.demo.searchtoolswidget.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyPolicyTest {

    @Test
    fun `missing and non positive values use sixty minutes`() {
        assertEquals(60L, FrequencyPolicy.sanitizeMinutes(null))
        assertEquals(60L, FrequencyPolicy.sanitizeMinutes(0))
        assertEquals(60L, FrequencyPolicy.sanitizeMinutes(-5))
    }

    @Test
    fun `positive values below WorkManager minimum clamp to fifteen`() {
        assertEquals(15L, FrequencyPolicy.sanitizeMinutes(1))
        assertEquals(15L, FrequencyPolicy.sanitizeMinutes(14))
    }

    @Test
    fun `valid values are preserved`() {
        assertEquals(15L, FrequencyPolicy.sanitizeMinutes(15))
        assertEquals(60L, FrequencyPolicy.sanitizeMinutes(60))
        assertEquals(120L, FrequencyPolicy.sanitizeMinutes(120))
    }
}

