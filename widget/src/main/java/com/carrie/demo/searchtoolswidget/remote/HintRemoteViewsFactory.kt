package com.carrie.demo.searchtoolswidget.remote

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetClickContract
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

class HintRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var records: List<WidgetHintRecord> = emptyList()

    override fun onCreate() {
        WidgetStorageInitializer.initialize(context)
        reload()
    }

    override fun onDataSetChanged() {
        reload()
    }

    override fun onDestroy() {
        records = emptyList()
    }

    override fun getCount(): Int = records.size

    override fun getViewAt(position: Int): RemoteViews {
        val record = records.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_hint_item)
        return RemoteViews(context.packageName, R.layout.widget_hint_item).apply {
            setTextViewText(R.id.widget_hint_text, record.keyword)
            setOnClickFillInIntent(
                R.id.widget_hint_text,
                fillInIntent(WidgetAction.SEARCH_ACTIVATE, record.keyword, position),
            )
            setOnClickFillInIntent(
                R.id.widget_search_button,
                fillInIntent(WidgetAction.SEARCH_SUBMIT, record.keyword, position),
            )
            bindTool(R.id.tool_favorites, WidgetAction.FAVORITES, record.keyword, position)
            bindTool(R.id.tool_history, WidgetAction.HISTORY, record.keyword, position)
            bindTool(R.id.tool_weather, WidgetAction.WEATHER, record.keyword, position)
            bindTool(R.id.tool_settings, WidgetAction.SETTINGS, record.keyword, position)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = records.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun reload() {
        val store = MmkvWidgetStateStore.get()
        records = if (store.isPrivacyAllowed()) store.readPool() else emptyList()
    }

    private fun RemoteViews.bindTool(
        viewId: Int,
        action: WidgetAction,
        keyword: String,
        position: Int,
    ) {
        setOnClickFillInIntent(viewId, fillInIntent(action, keyword, position))
    }

    private fun fillInIntent(action: WidgetAction, keyword: String, position: Int): Intent {
        return Intent().apply {
            putExtra(WidgetClickContract.EXTRA_ACTION, action.name)
            putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
            putExtra(WidgetClickContract.EXTRA_HINT_POSITION, position)
        }
    }
}
