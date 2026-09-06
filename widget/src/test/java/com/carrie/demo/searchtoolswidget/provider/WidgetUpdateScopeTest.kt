package com.carrie.demo.searchtoolswidget.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUpdateScopeTest {

    @Test
    fun `a widget click targets only the source instance`() {
        assertArrayEquals(intArrayOf(21), WidgetUpdateScope.forClick(21))
    }

    @Test
    fun `an invalid widget id does not target any instance`() {
        assertTrue(WidgetUpdateScope.forClick(0).isEmpty())
        assertTrue(WidgetUpdateScope.forClick(-1).isEmpty())
    }
}
