package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore

@Suppress("DEPRECATION")
object WidgetInstanceUpdater {
    fun allWidgetIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context.packageName, WidgetComponentNames.PROVIDER_CLASS),
        )
    }

    fun refreshAll(context: Context, displayedChild: Int?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = allWidgetIds(context)
        if (ids.isEmpty()) return
        manager.notifyAppWidgetViewDataChanged(ids, R.id.hint_flipper)
        WidgetRemoteViewsRenderer.render(context, manager, ids, displayedChild)
    }

    fun advanceAll(context: Context, clickedPosition: Int) {
        val ids = allWidgetIds(context)
        if (ids.isEmpty()) return
        val poolSize = MmkvWidgetStateStore.get().readPool().size
        val nextPosition = HintPositionPolicy.next(clickedPosition, poolSize) ?: return
        val manager = AppWidgetManager.getInstance(context)
        WidgetRemoteViewsRenderer.render(context, manager, ids, nextPosition)
    }
}
