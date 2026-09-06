package com.carrie.demo.searchtoolswidget.sync

import androidx.work.ExistingPeriodicWorkPolicy

/** 根据 frequency 是否变化，选择唯一周期任务的更新策略。 */
object PeriodicWorkPolicySelector {
    /**
     * 频率相同时 KEEP 原任务，避免每次冷启动都重置其下一次执行时间；
     * 频率变化时 UPDATE 同名任务，让新的周期配置生效且不制造第二条周期任务。
     */
    fun select(storedMinutes: Long, requestedMinutes: Long): ExistingPeriodicWorkPolicy =
        if (storedMinutes == requestedMinutes) {
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            ExistingPeriodicWorkPolicy.UPDATE
        }
}
