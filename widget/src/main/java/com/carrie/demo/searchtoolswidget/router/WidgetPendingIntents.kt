package com.carrie.demo.searchtoolswidget.router

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object WidgetPendingIntents {
    fun collectionTemplate(context: Context, appWidgetId: Int): PendingIntent {
        val intent = baseIntent(context, appWidgetId).apply {
            data = Uri.parse("demo2://widget/collection/$appWidgetId")
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun action(
        context: Context,
        appWidgetId: Int,
        action: WidgetAction,
        keyword: String? = null,
    ): PendingIntent {
        val intent = baseIntent(context, appWidgetId).apply {
            putExtra(WidgetClickContract.EXTRA_ACTION, action.name)
            putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword.orEmpty())
            data = Uri.parse("demo2://widget/action/$appWidgetId/${action.name}")
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId * 10 + action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun baseIntent(context: Context, appWidgetId: Int): Intent {
        return Intent(context, WidgetRouterActivity::class.java).putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            appWidgetId,
        )
    }
}

