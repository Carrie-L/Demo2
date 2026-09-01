package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.carrie.demo.searchtoolswidget.R

@Suppress("DEPRECATION")
object WidgetInstanceUpdater {
    fun allWidgetIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context.packageName, WidgetComponentNames.PROVIDER_CLASS),
        )
    }

    fun refreshAll(context: Context, resetToFirst: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = allWidgetIds(context)
        if (ids.isEmpty()) return
        manager.notifyAppWidgetViewDataChanged(ids, R.id.hint_flipper)
        WidgetRemoteViewsRenderer.render(context, manager, ids, resetToFirst)
    }

    fun advanceAll(context: Context) {
        val ids = allWidgetIds(context)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_search_tools).apply {
            showNext(R.id.hint_flipper)
        }
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids, views)
    }
}
