package com.carrie.demo.searchtoolswidget.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRouteTest {

    @Test
    fun `search surface opens activation page with keyword and frozen app hints`() {
        val route = WidgetRoute.resolve(WidgetAction.SEARCH_ACTIVATE, "露营天气")

        assertEquals("/search/activation", route.targetRoutePath)
        assertEquals("露营天气", route.keyword)
        assertTrue(route.freezeHintRotation)
    }

    @Test
    fun `blank search surface never freezes fallback as keyword`() {
        val route = WidgetRoute.resolve(WidgetAction.SEARCH_ACTIVATE, "   ")

        assertEquals("", route.keyword)
        assertFalse(route.freezeHintRotation)
    }

    @Test
    fun `search button opens result page with current keyword`() {
        val route = WidgetRoute.resolve(WidgetAction.SEARCH_SUBMIT, "Android Widget")

        assertEquals("/search/result", route.targetRoutePath)
        assertEquals("Android Widget", route.keyword)
        assertFalse(route.freezeHintRotation)
    }

    @Test
    fun `four tools resolve to four different pages`() {
        val targets = listOf(
            WidgetAction.FAVORITES,
            WidgetAction.HISTORY,
            WidgetAction.WEATHER,
            WidgetAction.SETTINGS,
        ).map { WidgetRoute.resolve(it, null).targetRoutePath }

        assertEquals(
            listOf(
                "/tools/favorites",
                "/tools/history",
                "/tools/weather",
                "/tools/settings",
            ),
            targets,
        )
    }
}
