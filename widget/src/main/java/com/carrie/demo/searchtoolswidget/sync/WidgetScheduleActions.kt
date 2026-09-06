package com.carrie.demo.searchtoolswidget.sync

/** widget 模块与 app 模块调度接收器之间的显式广播协议。 */
object WidgetScheduleActions {
    /** app 模块内真正持有 WorkManager 依赖的 Receiver 完整类名。 */
    const val RECEIVER_CLASS =
        "com.carrie.demo.searchtoolswidget.sync.WidgetScheduleReceiver"

    /** 重新核对隐私、实例数量和 frequency，并创建/更新/取消周期任务。 */
    const val ACTION_RECONCILE = "com.carrie.demo.action.WIDGET_RECONCILE"

    /** reconcile 完成后是否还要额外排入一次立即同步。 */
    const val EXTRA_ENQUEUE_IMMEDIATE = "enqueue_immediate_widget_sync"
}
