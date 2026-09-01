package com.carrie.demo.searchtoolswidget.sync

object WidgetScheduleActions {
    const val RECEIVER_CLASS =
        "com.carrie.demo.searchtoolswidget.sync.WidgetScheduleReceiver"
    const val ACTION_RECONCILE = "com.carrie.demo.action.WIDGET_RECONCILE"
    const val ACTION_SYNC_NOW = "com.carrie.demo.action.WIDGET_SYNC_NOW"
    const val EXTRA_ENQUEUE_IMMEDIATE = "enqueue_immediate_widget_sync"
}
