package com.carrie.demo.searchtoolswidget.remote

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetClickContract

/**
 * 暗词集合 item 的唯一构建入口。
 *
 * API 29～30 的 RemoteViewsFactory 与 API 31+ 的内联集合共用这里，避免两套版本
 * 出现文字、点击参数或布局行为不一致。
 */
object WidgetHintItemRemoteViews {
    /** 构建一条可点击的暗词 RemoteViews。 */
    fun build(context: Context, record: WidgetHintRecord): RemoteViews {
        return empty(context).apply {
            setTextViewText(R.id.widget_hint_text, record.keyword)
            setOnClickFillInIntent(
                R.id.widget_hint_text,
                fillInIntent(WidgetAction.SEARCH_ACTIVATE, record.keyword),
            )
            setOnClickFillInIntent(
                R.id.widget_search_button,
                fillInIntent(WidgetAction.SEARCH_SUBMIT, record.keyword),
            )
        }
    }

    /**
     * 创建空 item，供 Factory 遇到越界 position 时安全返回。
     * 正常路径一定会继续调用 [build] 写入暗词和点击参数。
     */
    fun empty(context: Context): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_hint_item)
    }

    /**
     * Fill-in Intent 只携带当前 item 独有的 action 与 keyword。
     * appWidgetId 来自外层 PendingIntent Template，用于只推进被点击的组件实例。
     */
    private fun fillInIntent(action: WidgetAction, keyword: String): Intent {
        return Intent().apply {
            putExtra(WidgetClickContract.EXTRA_ACTION, action.name)
            putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
        }
    }
}
