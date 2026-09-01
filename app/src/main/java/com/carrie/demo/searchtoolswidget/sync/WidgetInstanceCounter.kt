package com.carrie.demo.searchtoolswidget.sync

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.carrie.demo.searchtoolswidget.provider.WidgetComponentNames

object WidgetInstanceCounter {
    fun count(context: Context): Int {
        val component = ComponentName(context.packageName, WidgetComponentNames.PROVIDER_CLASS)
        return AppWidgetManager.getInstance(context).getAppWidgetIds(component).size
    }
}

