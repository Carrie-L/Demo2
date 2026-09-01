package com.carrie.demo.searchtoolswidget.sync

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.storage.WidgetStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HintSyncEngineTest {
    private val first = WidgetHintRecord(1L, "露营", 10)
    private val second = WidgetHintRecord(2L, "天气", 20)

    @Test
    fun `same pool only updates successful refresh time`() {
        val store = FakeStore(pool = listOf(first, second))
        val engine = HintSyncEngine(
            source = { listOf(first.copy(), second.copy()) },
            store = store,
            nowMillis = { 1_234L },
        )

        assertEquals(HintSyncOutcome.Unchanged, engine.sync())
        assertFalse(store.didReplacePool)
        assertEquals(1_234L, store.lastSuccessfulRefreshAtMillis())
    }

    @Test
    fun `different complete pool replaces old pool and reports changed`() {
        val store = FakeStore(pool = listOf(first))
        val changed = listOf(second, first)
        val engine = HintSyncEngine(
            source = { changed },
            store = store,
            nowMillis = { 2_345L },
        )

        assertEquals(HintSyncOutcome.Changed, engine.sync())
        assertEquals(changed, store.readPool())
        assertEquals(2_345L, store.lastSuccessfulRefreshAtMillis())
    }

    @Test
    fun `successful empty result clears old pool`() {
        val store = FakeStore(pool = listOf(first))
        val engine = HintSyncEngine(
            source = { emptyList() },
            store = store,
            nowMillis = { 3_456L },
        )

        assertEquals(HintSyncOutcome.Changed, engine.sync())
        assertEquals(emptyList<WidgetHintRecord>(), store.readPool())
    }

    @Test
    fun `database failure keeps pool and success time untouched`() {
        val store = FakeStore(pool = listOf(first), lastSuccess = 99L)
        val engine = HintSyncEngine(
            source = { error("database unavailable") },
            store = store,
            nowMillis = { 4_567L },
        )

        assertEquals(HintSyncOutcome.Failed, engine.sync())
        assertEquals(listOf(first), store.readPool())
        assertEquals(99L, store.lastSuccessfulRefreshAtMillis())
    }

    @Test
    fun `diagnostic timestamp failure does not hide a committed pool change`() {
        val store = FakeStore(pool = listOf(first), failTimestampWrite = true)
        val changed = listOf(second)
        val engine = HintSyncEngine(
            source = { changed },
            store = store,
            nowMillis = { 5_678L },
        )

        assertEquals(HintSyncOutcome.Changed, engine.sync())
        assertEquals(changed, store.readPool())
    }

    private class FakeStore(
        pool: List<WidgetHintRecord> = emptyList(),
        private var lastSuccess: Long = 0L,
        private val failTimestampWrite: Boolean = false,
    ) : WidgetStateStore {
        private var records = pool
        var didReplacePool = false
            private set

        override fun readPool(): List<WidgetHintRecord> = records

        override fun replacePool(pool: List<WidgetHintRecord>) {
            records = pool
            didReplacePool = true
        }

        override fun clearPool() {
            replacePool(emptyList())
        }

        override fun updateLastSuccessfulRefreshAt(timestampMillis: Long) {
            if (failTimestampWrite) error("timestamp storage unavailable")
            lastSuccess = timestampMillis
        }

        override fun lastSuccessfulRefreshAtMillis(): Long = lastSuccess

        override fun setPrivacyAllowed(allowed: Boolean) = Unit

        override fun isPrivacyAllowed(): Boolean = true

        override fun setFrequencyMinutes(minutes: Long) = Unit

        override fun frequencyMinutes(): Long = 60L
    }
}
