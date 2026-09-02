package com.carrie.demo.searchtoolswidget.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintPositionPolicyTest {

    @Test
    fun `click advances to the next stable position`() {
        assertEquals(1, HintPositionPolicy.next(clickedPosition = 0, poolSize = 4))
        assertEquals(3, HintPositionPolicy.next(clickedPosition = 2, poolSize = 4))
    }

    @Test
    fun `last position wraps to the first`() {
        assertEquals(0, HintPositionPolicy.next(clickedPosition = 3, poolSize = 4))
    }

    @Test
    fun `stale position falls back to the first record of the current pool`() {
        assertEquals(0, HintPositionPolicy.next(clickedPosition = -1, poolSize = 4))
        assertEquals(0, HintPositionPolicy.next(clickedPosition = 5, poolSize = 4))
    }

    @Test
    fun `empty pool has no target position`() {
        assertNull(HintPositionPolicy.next(clickedPosition = 0, poolSize = 0))
    }
}
