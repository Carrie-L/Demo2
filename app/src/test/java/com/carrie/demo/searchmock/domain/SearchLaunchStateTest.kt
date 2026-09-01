package com.carrie.demo.searchmock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLaunchStateTest {

    @Test
    fun `widget keyword is shown and app hint rotation is frozen`() {
        val state = SearchLaunchState.resolve(
            keyword = "露营帐篷怎么选",
            freezeHintRotation = true,
        )

        assertEquals("露营帐篷怎么选", state.initialKeyword)
        assertFalse(state.shouldRotateHints)
    }

    @Test
    fun `normal app entry keeps hint rotation enabled`() {
        val state = SearchLaunchState.resolve(keyword = null, freezeHintRotation = false)

        assertEquals("", state.initialKeyword)
        assertTrue(state.shouldRotateHints)
    }

    @Test
    fun `fallback text is never frozen as a real keyword`() {
        val state = SearchLaunchState.resolve(keyword = "   ", freezeHintRotation = true)

        assertEquals("", state.initialKeyword)
        assertTrue(state.shouldRotateHints)
    }
}

