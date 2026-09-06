package com.carrie.demo.searchtoolswidget.sync

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.carrie.demo.searchtoolswidget.provider.WidgetComponentNames

/** 查询本应用当前实际存在的小组件实例数量。 */
object WidgetInstanceCounter {
    /** 桌面和负一屏只要使用同一个 Provider，都会出现在这个 id 数组中。 */
    fun count(context: Context): Int {
        val component = ComponentName(context.packageName, WidgetComponentNames.PROVIDER_CLASS)
        return AppWidgetManager.getInstance(context).getAppWidgetIds(component).size
    }
}
