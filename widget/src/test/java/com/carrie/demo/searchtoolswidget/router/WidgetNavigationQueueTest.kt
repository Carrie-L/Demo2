package com.carrie.demo.searchtoolswidget.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 验证快速重复点击时，新页面不会被旧异步跳转反盖。 */
class WidgetNavigationQueueTest {
    @Test
    fun `first click runs immediately and completion releases the queue`() {
        val queue = WidgetNavigationQueue()
        val favorites = WidgetRoute.resolve(WidgetAction.FAVORITES, null)
        assertEquals(favorites, queue.submit(favorites))
        assertNull(queue.complete())
        assertEquals(favorites, queue.submit(favorites))
    }

    @Test
    fun `rapid different clicks wait and only the latest destination follows`() {
        val queue = WidgetNavigationQueue()
        val favorites = WidgetRoute.resolve(WidgetAction.FAVORITES, null)
        val history = WidgetRoute.resolve(WidgetAction.HISTORY, null)
        val weather = WidgetRoute.resolve(WidgetAction.WEATHER, null)
        assertEquals(favorites, queue.submit(favorites))
        assertNull(queue.submit(history))
        assertNull(queue.submit(weather))
        assertEquals(weather, queue.complete())
        assertNull(queue.complete())
    }

    @Test
    fun `repeated search clicks use the new keyword rather than previous extras`() {
        val queue = WidgetNavigationQueue()
        val first = WidgetRoute.resolve(WidgetAction.SEARCH_SUBMIT, "第一条")
        val latest = WidgetRoute.resolve(WidgetAction.SEARCH_SUBMIT, "最新暗词")
        assertEquals(first, queue.submit(first))
        assertNull(queue.submit(latest))
        assertEquals("最新暗词", queue.complete()?.keyword)
        assertNull(queue.complete())
    }
}
