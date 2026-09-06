package com.carrie.demo.searchtoolswidget.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRenderSessionTest {

    @Test
    fun `the same widget is initialized only once in one process session`() {
        val session = WidgetRenderSession()

        assertTrue(session.markForInitialRender(10))
        assertFalse(session.markForInitialRender(10))
    }

    @Test
    fun `different widget instances are initialized independently`() {
        val session = WidgetRenderSession()

        assertTrue(session.markForInitialRender(10))
        assertTrue(session.markForInitialRender(11))
    }

    @Test
    fun `a deleted widget can be initialized if the id appears again`() {
        val session = WidgetRenderSession()
        session.markForInitialRender(10)

        session.remove(10)

        assertTrue(session.markForInitialRender(10))
    }

    @Test
    fun `clearing the session permits every widget to initialize again`() {
        val session = WidgetRenderSession()
        session.markForInitialRender(10)
        session.markForInitialRender(11)

        session.clear()

        assertTrue(session.markForInitialRender(10))
        assertTrue(session.markForInitialRender(11))
    }
}
