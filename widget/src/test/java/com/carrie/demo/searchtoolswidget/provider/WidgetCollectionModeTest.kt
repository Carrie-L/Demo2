package com.carrie.demo.searchtoolswidget.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCollectionModeTest {

    @Test
    fun `API 29 and 30 use the legacy remote service`() {
        assertEquals(
            WidgetCollectionMode.LEGACY_REMOTE_SERVICE,
            WidgetCollectionMode.forSdk(29),
        )
        assertEquals(
            WidgetCollectionMode.LEGACY_REMOTE_SERVICE,
            WidgetCollectionMode.forSdk(30),
        )
    }

    @Test
    fun `API 31 and newer use inline remote collection items`() {
        assertEquals(
            WidgetCollectionMode.INLINE_REMOTE_COLLECTION,
            WidgetCollectionMode.forSdk(31),
        )
        assertEquals(
            WidgetCollectionMode.INLINE_REMOTE_COLLECTION,
            WidgetCollectionMode.forSdk(36),
        )
    }
}
