package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.remote.HintRemoteViewsService
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetPendingIntents

@Suppress("DEPRECATION")
object WidgetRemoteViewsRenderer {
    fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        resetToFirst: Boolean,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(
                appWidgetId,
                buildViews(context, appWidgetId, resetToFirst),
            )
        }
    }

    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        resetToFirst: Boolean,
    ): RemoteViews {
        val adapterIntent = Intent(context, HintRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("demo2://widget/adapter/$appWidgetId")
        }
        return RemoteViews(context.packageName, R.layout.widget_search_tools).apply {
            setRemoteAdapter(R.id.hint_flipper, adapterIntent)
            setEmptyView(R.id.hint_flipper, R.id.empty_hint_container)
            setPendingIntentTemplate(
                R.id.hint_flipper,
                WidgetPendingIntents.collectionTemplate(context, appWidgetId),
            )
            if (resetToFirst) {
                setDisplayedChild(R.id.hint_flipper, 0)
            }

            setOnClickPendingIntent(
                R.id.empty_hint_text,
                WidgetPendingIntents.action(
                    context,
                    appWidgetId,
                    WidgetAction.SEARCH_ACTIVATE,
                ),
            )
            setOnClickPendingIntent(
                R.id.empty_search_button,
                WidgetPendingIntents.action(
                    context,
                    appWidgetId,
                    WidgetAction.SEARCH_ACTIVATE,
                ),
            )
            bindTool(context, appWidgetId, R.id.tool_favorites, WidgetAction.FAVORITES)
            bindTool(context, appWidgetId, R.id.tool_history, WidgetAction.HISTORY)
            bindTool(context, appWidgetId, R.id.tool_weather, WidgetAction.WEATHER)
            bindTool(context, appWidgetId, R.id.tool_settings, WidgetAction.SETTINGS)
        }
    }

    private fun RemoteViews.bindTool(
        context: Context,
        appWidgetId: Int,
        viewId: Int,
        action: WidgetAction,
    ) {
        setOnClickPendingIntent(
            viewId,
            WidgetPendingIntents.action(context, appWidgetId, action),
        )
    }
}
