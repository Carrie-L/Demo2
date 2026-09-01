package com.carrie.demo.searchtoolswidget.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetEligibilityTest {

    @Test
    fun `sync is allowed only with privacy and at least one widget`() {
        assertFalse(WidgetEligibility.canSync(privacyAccepted = false, widgetCount = 0))
        assertFalse(WidgetEligibility.canSync(privacyAccepted = false, widgetCount = 2))
        assertFalse(WidgetEligibility.canSync(privacyAccepted = true, widgetCount = 0))
        assertTrue(WidgetEligibility.canSync(privacyAccepted = true, widgetCount = 1))
        assertTrue(WidgetEligibility.canSync(privacyAccepted = true, widgetCount = 3))
    }
}

