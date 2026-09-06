package com.carrie.demo.searchtoolswidget.sync

import com.carrie.demo.searchtoolswidget.domain.HintPoolDecider
import com.carrie.demo.searchtoolswidget.domain.HintPoolDecision
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.storage.WidgetStateStore

/** 一次数据库读取与 MMKV 同步的业务结果。 */
enum class HintSyncOutcome {
    /** 数据库完整记录列表与旧池不同，MMKV 已被整体替换。 */
    Changed,

    /** 新旧完整记录列表相等，池未重写、轮播也不归零。 */
    Unchanged,

    /** 查询或写入抛出异常，旧池完整保留。 */
    Failed,
}

/**
 * 暗词来源抽象。
 * 正式迁移时传入主搜索模块与其自身完全相同的 DAO 查询，不在 widget 侧另做排序或去重。
 */
fun interface WidgetHintSource {
    /** 返回数据库查询结果，列表顺序即轮播顺序。 */
    fun query(): List<WidgetHintRecord>
}

/**
 * 暗词同步的纯业务核心，不依赖 WorkManager，因此可以直接做单元测试。
 *
 * 注意：这里只比较数据库完整记录列表是否相等，不是只按展示文案精确比较。
 * 数据不同才整体覆盖 MMKV；查询失败绝不清空旧池；查询成功但结果为空则会用空池替换。
 */
class HintSyncEngine(
    private val source: WidgetHintSource,
    private val store: WidgetStateStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** 执行一次“查询数据库 -> 比较完整列表 -> 必要时替换 -> 记录成功时间”。 */
    fun sync(): HintSyncOutcome = try {
        val newPool = source.query()
        when (val decision = HintPoolDecider.decide(store.readPool(), newPool)) {
            HintPoolDecision.Unchanged -> {
                // 即使内容没变化，本次数据库读取仍然成功，因此更新时间仍应记录。
                runCatching { store.updateLastSuccessfulRefreshAt(nowMillis()) }
                HintSyncOutcome.Unchanged
            }

            is HintPoolDecision.Replace -> {
                // 整个列表序列化后一次覆盖同一个 MMKV key，不做先删再插，避免中间空窗。
                store.replacePool(decision.newPool)
                runCatching { store.updateLastSuccessfulRefreshAt(nowMillis()) }
                HintSyncOutcome.Changed
            }
        }
    } catch (_: Exception) {
        // 保留旧池，让下一次 WorkManager 周期或 retry 再尝试。
        HintSyncOutcome.Failed
    }
}
