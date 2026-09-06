package com.carrie.demo.searchtoolswidget.provider

import android.content.Context
import android.content.Intent

/** widget 模块内部用于通知“MMKV 暗词池已真正变化”的广播协议。 */
object WidgetBroadcasts {
    /** Provider 收到后会刷新所有实例并让它们从新池第 0 条开始。 */
    const val ACTION_HINT_POOL_CHANGED = "com.carrie.demo.action.HINT_POOL_CHANGED"

    /**
     * 给本应用指定 Provider 发送显式广播。
     * 使用明确 className，不让这条内部状态通知扩散给其他应用的广播接收器。
     */
    fun sendPoolChanged(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_HINT_POOL_CHANGED).setClassName(
                context.packageName,
                WidgetComponentNames.PROVIDER_CLASS,
            ),
        )
    }
}
