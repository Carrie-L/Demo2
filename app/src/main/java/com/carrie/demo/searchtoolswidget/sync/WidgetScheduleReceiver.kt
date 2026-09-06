package com.carrie.demo.searchtoolswidget.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

/**
 * 把 Provider/UI 发来的显式“重新协调”广播转给 [WidgetHintSyncScheduler]。
 *
 * Receiver 本身不查询数据库、不更新 RemoteViews，只做很薄的命令分发，避免生命周期
 * 短暂的 BroadcastReceiver 承担耗时工作；真正任务交给 WorkManager 持久化执行。
 */
class WidgetScheduleReceiver : BroadcastReceiver() {
    /** 收到 RECONCILE 后重新核对隐私、组件数量和云配频率。 */
    override fun onReceive(context: Context, intent: Intent) {
        WidgetStorageInitializer.initialize(context)
        if (intent.action == WidgetScheduleActions.ACTION_RECONCILE) {
            WidgetHintSyncScheduler(context).reconcile(
                enqueueImmediate = intent.getBooleanExtra(
                    WidgetScheduleActions.EXTRA_ENQUEUE_IMMEDIATE,
                    false,
                ),
            )
        }
    }
}
