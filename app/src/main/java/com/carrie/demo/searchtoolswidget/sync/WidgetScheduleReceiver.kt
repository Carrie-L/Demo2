package com.carrie.demo.searchtoolswidget.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

class WidgetScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetStorageInitializer.initialize(context)
        val scheduler = WidgetHintSyncScheduler(context)
        when (intent.action) {
            WidgetScheduleActions.ACTION_SYNC_NOW -> scheduler.enqueueImmediateSync()
            WidgetScheduleActions.ACTION_RECONCILE -> scheduler.reconcile(
                enqueueImmediate = intent.getBooleanExtra(
                    WidgetScheduleActions.EXTRA_ENQUEUE_IMMEDIATE,
                    false,
                ),
            )
        }
    }
}
