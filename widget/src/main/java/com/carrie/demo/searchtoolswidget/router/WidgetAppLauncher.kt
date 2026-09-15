package com.carrie.demo.searchtoolswidget.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Provider 推进后打开宿主 App。仅使用 Android Intent，不依赖 app 模块或 ARouter。 */
object WidgetAppLauncher {
    /**
     * 只取本包启动入口的 component，不沿用查询结果中的 MAIN、Launcher 类别及旧 flags。
     * 无 URI 的 HINT_WORD_NEXT 是纯推进。多个 Launcher/alias 的正式宿主需确认入口配置。
     * 仅供用户点击调用，不能从 Worker/定时广播主动拉起 App。
     */
    fun open(context: Context, uri: Uri?, keyword: String?) {
        if (uri == null) return
        try {
            // 只放行组件配置的页面，不能把可变 Fill-in 变成任意页面启动器。
            val destination = WidgetAction.entries.firstOrNull { it.deepLink == uri.toString() }
            if (destination == null) {
                Log.w(LOG_TAG, "忽略不属于组件协议的 URI")
                return
            }
            val entry = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
            if (entry == null || entry.packageName != context.packageName) {
                Log.w(LOG_TAG, "未找到本 App 的启动入口")
                return
            }
            val launchIntent = Intent(Intent.ACTION_VIEW, uri).setComponent(entry).apply {
                if (destination.acceptsKeyword && keyword != null) {
                    putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
                }
                // 进入 App 任务；已有入口清除其上方旧页并接收 onNewIntent，不清空整个任务。
                // CLEAR_TOP 会影响返回栈；入口不在栈内时正常新建，不要求入口是任务根。
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            Log.d(LOG_TAG, "打开宿主入口 path=${uri.path}")
            context.startActivity(launchIntent)
        } catch (error: Exception) {
            // 后台启动被拒绝可能仅记系统日志；没有抛异常不等于页面已经落地。
            Log.e(LOG_TAG, "打开 App 失败: ${error.javaClass.simpleName}")
        }
    }

    /** 仅记固定路径、异常类型，不打印 keyword。 */
    private const val LOG_TAG = "WidgetClick"
}
