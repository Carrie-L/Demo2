package com.carrie.demo.searchtoolswidget.router

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.carrie.demo.searchtoolswidget.provider.SearchToolsWidgetProvider
import com.carrie.demo.searchtoolswidget.provider.WidgetBroadcasts

/**
 * 默认方案 A：按钮向 Provider 发送 HINT_WORD_NEXT，由 WidgetTaskLauncher 启动 App。
 * 方案 B：USE_DIRECT_ENTRY=true 时直接打开入口，由入口发送纯推进广播。
 * 同一种按钮在全部实例间共享 PendingIntent；组件 ID 不属于点击协议。
 */
object WidgetPendingIntents {
    /**
     * false = Receiver + AppTask；true = Activity PendingIntent + Launcher 发 next。
     * 修改后要重新下发 RemoteViews：覆盖安装会由 MY_PACKAGE_REPLACED 重绑，也可重新添加组件。
     * 不要同时手动启用两条点击链，否则一次点击会推进两次。
     */
    const val USE_DIRECT_ENTRY = false

    /**
     * 模板必须留空 data，让当前 item 填入真实 URI/keyword，不能用伪 URI 占住 data。
     * 集合需要 MUTABLE；方案 A 固定 Provider/action，方案 B 固定包名并在新类中构建模板。
     */
    fun collectionTemplate(context: Context): PendingIntent? = if (USE_DIRECT_ENTRY) {
        WidgetDirectEntryIntents.collectionTemplate(context)
    } else createSafely {
        PendingIntent.getBroadcast(
            context, COLLECTION_REQUEST_CODE, broadcastIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /** 两种方案都只给搜索入口传 keyword，静态按钮都使用不可变 PendingIntent。 */
    fun action(context: Context, action: WidgetAction, keyword: String? = null): PendingIntent? =
        if (USE_DIRECT_ENTRY) {
            WidgetDirectEntryIntents.action(context, action, keyword)
        } else createSafely {
            val intent = broadcastIntent(context).apply {
                data = Uri.parse(action.deepLink)
                if (action.acceptsKeyword && keyword != null) {
                    putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
                }
            }
            PendingIntent.getBroadcast(
                context, action.ordinal + 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    /** 当前 item 携带点击时的词，不在点击后从池中猜当前位置。 */
    fun fillIn(action: WidgetAction, keyword: String): Intent = Intent().apply {
        data = Uri.parse(action.deepLink)
        if (action.acceptsKeyword) putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
    }

    /** 显式广播只交给本模块 Provider，与系统 onUpdate 分支分开。 */
    private fun broadcastIntent(context: Context): Intent =
        Intent(context, SearchToolsWidgetProvider::class.java).setAction(WidgetBroadcasts.HINT_WORD_NEXT)

    /** 系统拒绝创建凭据时不拖垮组件渲染；null 表示暂不绑定这个点击。 */
    private inline fun createSafely(create: () -> PendingIntent): PendingIntent? = try {
        create()
    } catch (error: Exception) {
        Log.e(LOG_TAG, "创建点击广播失败: ${error.javaClass.simpleName}")
        null
    }

    /** 集合和静态按钮使用不同 requestCode，不按实例分配。 */
    private const val COLLECTION_REQUEST_CODE = 0
    /** 不在日志中打印 keyword。 */
    private const val LOG_TAG = "WidgetClick"
}
