package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord

/**
 * widget 持久状态接口。
 * 业务同步只依赖本接口，单元测试可用内存实现，正式运行使用 [MmkvWidgetStateStore]。
 */
interface WidgetStateStore {
    /** 读取完整暗词池，顺序即轮播顺序。 */
    fun readPool(): List<WidgetHintRecord>

    /** 将暗词池作为一个整体覆盖，不做先删再插和增量合并。 */
    fun replacePool(pool: List<WidgetHintRecord>)

    /** 用空列表覆盖当前池。 */
    fun clearPool()

    /** 记录最近一次成功读取数据库的时间；内容相同也属于读取成功。 */
    fun updateLastSuccessfulRefreshAt(timestampMillis: Long)

    /** 返回最近一次成功读取数据库的毫秒时间戳，0 表示尚未成功。 */
    fun lastSuccessfulRefreshAtMillis(): Long

    /** 同步主 App 的隐私协议状态，供 Provider 独立判断是否允许展示池。 */
    fun setPrivacyAllowed(allowed: Boolean)

    /** 是否允许读取和展示搜索暗词。 */
    fun isPrivacyAllowed(): Boolean

    /** 保存当前已经应用到周期任务的分钟数。 */
    fun setFrequencyMinutes(minutes: Long)

    /** 读取当前已应用的周期分钟数。 */
    fun frequencyMinutes(): Long
}
