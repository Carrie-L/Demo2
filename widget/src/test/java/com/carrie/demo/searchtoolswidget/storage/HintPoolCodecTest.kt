package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class HintPoolCodecTest {
    private val codec = HintPoolCodec()

    @Test
    fun `record list survives json round trip with order and duplicates`() {
        val records = listOf(
            WidgetHintRecord(1L, "重复暗词", 10),
            WidgetHintRecord(2L, "重复暗词", 20),
            WidgetHintRecord(3L, "第三条", 30),
        )

        assertEquals(records, codec.decodeOrEmpty(codec.encode(records)))
    }

    @Test
    fun `missing empty and broken json decode as empty pool`() {
        assertEquals(emptyList<WidgetHintRecord>(), codec.decodeOrEmpty(null))
        assertEquals(emptyList<WidgetHintRecord>(), codec.decodeOrEmpty(""))
        assertEquals(emptyList<WidgetHintRecord>(), codec.decodeOrEmpty("{broken"))
    }
}

