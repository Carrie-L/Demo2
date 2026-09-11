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
     * 否则 Android 12+ 无法写入 fill-in 参数；组件 id 仅用来区分 PendingIntent 身份。
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
     * 普通工具按钮不需要 keyword；appWidgetId 只用于身份区分，不参与路由或推进范围。
     * 每次有效点击统一推进所有实例。
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

    /**
     * 所有点击先进入无 Logo 的路由 Activity，再通过 ARouter 跳到主 App 页面。
     *
     * NEW_TASK 从桌面进入主 App 的任务；CLEAR_TOP 把未完成的路由入口带回顶部，
     * 配合入口 singleTop 触发 onNewIntent，确保快速再次点击传来的新按钮/新词得到处理。
     * NO_ANIMATION 只禁用这次中转过渡，不影响应用内其他页面的正常动画。
     */
    private fun baseIntent(context: Context, appWidgetId: Int): Intent {
        return Intent(context, WidgetRouterActivity::class.java).apply {
            // UPDATE_CURRENT 只替换 extras，不能靠它更新旧 token 里的 Activity flags。
            // 用明确的入口 action 与旧版 action=null 区分；无需每次取消仍在桌面使用的 token。
            action = WidgetClickContract.ACTION_OPEN_WIDGET_ROUTE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        }
    }
}
