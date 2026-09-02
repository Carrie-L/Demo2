package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer
import com.carrie.demo.searchtoolswidget.sync.WidgetScheduleActions

class SearchToolsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetStorageInitializer.initialize(context)
        WidgetRemoteViewsRenderer.render(
            context = context,
            manager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            displayedChild = null,
        )
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        notifyMainProcess(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        notifyMainProcess(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetBroadcasts.ACTION_HINT_POOL_CHANGED) {
            WidgetStorageInitializer.initialize(context)
            WidgetInstanceUpdater.refreshAll(context, displayedChild = 0)
            return
        }
        super.onReceive(context, intent)
    }

    private fun notifyMainProcess(context: Context) {
        context.sendBroadcast(
            Intent(WidgetScheduleActions.ACTION_RECONCILE).setClassName(
                context.packageName,
                WidgetScheduleActions.RECEIVER_CLASS,
            ).putExtra(WidgetScheduleActions.EXTRA_ENQUEUE_IMMEDIATE, true),
        )
    }
}
