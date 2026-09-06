package com.carrie.demo.searchtoolswidget.router

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 为集合点击和静态工具按钮创建互不串参的 PendingIntent。 */
object WidgetPendingIntents {
    /**
     * 暗词集合共用的 PendingIntent Template。
     *
     * 每个 item 的 Fill-in Intent 会把 action/keyword 合并进来。模板必须使用 MUTABLE，
     * 否则 Android 12+ 无法写入 fill-in 参数；组件 id 固定在模板里，用来只推进当前实例。
     */
    fun collectionTemplate(context: Context, appWidgetId: Int): PendingIntent {
        val intent = baseIntent(context, appWidgetId).apply {
            // data 只用于区分不同组件实例的 PendingIntent 身份，不参与 ARouter 页面跳转。
            data = Uri.parse("demo2://widget/collection/$appWidgetId")
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * 为兜底搜索入口或底部静态工具按钮创建不可变 PendingIntent。
     *
     * 普通工具按钮不需要 keyword；appWidgetId 仍然需要，因为所有有效点击都要让
     * 被点击的那个实例 showNext，而不是推进全部实例。
     */
    fun action(
        context: Context,
        appWidgetId: Int,
        action: WidgetAction,
        keyword: String? = null,
    ): PendingIntent {
        val intent = baseIntent(context, appWidgetId).apply {
            putExtra(WidgetClickContract.EXTRA_ACTION, action.name)
            if (keyword != null) {
                putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
            }
            // action 也进入 URI，避免同一实例的多个按钮被系统判定为同一个 PendingIntent。
            data = Uri.parse("demo2://widget/action/$appWidgetId/${action.name}")
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId * 10 + action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 所有点击最终先进入透明路由 Activity，再通过 ARouter 跳到主 App 页面。 */
    private fun baseIntent(context: Context, appWidgetId: Int): Intent {
        return Intent(context, WidgetRouterActivity::class.java).putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            appWidgetId,
        )
    }
}
