package com.carrie.demo.searchtoolswidget.domain

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HintPoolDecisionTest {

    private val first = WidgetHintRecord(id = 1L, keyword = "露营装备", sortOrder = 10)
    private val second = WidgetHintRecord(id = 2L, keyword = "周末天气", sortOrder = 20)

    @Test
    fun `same complete ordered records keep current pool`() {
        val result = HintPoolDecider.decide(
            oldPool = listOf(first, second),
            newPool = listOf(first.copy(), second.copy()),
        )

        assertSame(HintPoolDecision.Unchanged, result)
    }

    @Test
    fun `any record field change replaces pool`() {
        val changed = second.copy(sortOrder = 21)

        val result = HintPoolDecider.decide(listOf(first, second), listOf(first, changed))

        assertEquals(HintPoolDecision.Replace(listOf(first, changed)), result)
    }

    @Test
    fun `record order change replaces pool`() {
        val reordered = listOf(second, first)

        val result = HintPoolDecider.decide(listOf(first, second), reordered)

        assertEquals(HintPoolDecision.Replace(reordered), result)
    }

    @Test
    fun `duplicates are preserved without widget side deduplication`() {
        val duplicates = listOf(first, first.copy())

        val result = HintPoolDecider.decide(emptyList(), duplicates)

        assertEquals(HintPoolDecision.Replace(duplicates), result)
    }

    @Test
    fun `successful empty result clears a non empty pool`() {
        val result = HintPoolDecider.decide(listOf(first), emptyList())

        assertEquals(HintPoolDecision.Replace(emptyList()), result)
    }
}

