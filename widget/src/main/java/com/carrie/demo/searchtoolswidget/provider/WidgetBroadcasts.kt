package com.carrie.demo.searchtoolswidget.provider

import android.content.Context
import android.content.Intent

object WidgetBroadcasts {
    const val ACTION_HINT_POOL_CHANGED = "com.carrie.demo.action.HINT_POOL_CHANGED"

    fun sendPoolChanged(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_HINT_POOL_CHANGED).setClassName(
                context.packageName,
                WidgetComponentNames.PROVIDER_CLASS,
            ),
        )
    }
}

