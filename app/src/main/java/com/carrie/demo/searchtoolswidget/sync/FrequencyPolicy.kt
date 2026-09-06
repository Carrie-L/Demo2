package com.carrie.demo.searchtoolswidget.sync

/** 把云配置 frequency 转换成 WorkManager 能接受的周期分钟数。 */
object FrequencyPolicy {
    /** 云端未下发、下发 0 或负数时采用的产品默认值：60 分钟。 */
    const val DEFAULT_MINUTES = 60L

    /**
     * WorkManager 对 PeriodicWorkRequest 的平台级最小周期限制：15 分钟。
     * 这不是本 Demo 自己拍定的阈值；低于它会导致周期任务构建失败。
     */
    const val MINIMUM_MINUTES = 15L

    /** 对缺失值、非法值以及小于 WorkManager 下限的值进行兜底。 */
    fun sanitizeMinutes(configuredMinutes: Int?): Long = when {
        configuredMinutes == null || configuredMinutes <= 0 -> DEFAULT_MINUTES
        configuredMinutes < MINIMUM_MINUTES -> MINIMUM_MINUTES
        else -> configuredMinutes.toLong()
    }
}
