package com.carrie.demo.searchtoolswidget.domain

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord

/** 比较数据库新结果与 MMKV 旧池后得到的更新决策。 */
sealed interface HintPoolDecision {
    /** 完整记录列表相等，不覆盖池，也不重置组件轮播。 */
    data object Unchanged : HintPoolDecision

    /** 完整记录列表不同，用 [newPool] 整体覆盖旧池。空列表同样是有效的新池。 */
    data class Replace(val newPool: List<WidgetHintRecord>) : HintPoolDecision
}

/** 暗词池比较规则的唯一入口。 */
object HintPoolDecider {
    /**
     * 使用 Kotlin List 的结构相等比较：元素数量、顺序和每条记录所有字段都要相等。
     * 这与“同一 DAO 查询结果直接比较”的正式语义一致，不单独按 keyword 比较或去重。
     */
    fun decide(
        oldPool: List<WidgetHintRecord>,
        newPool: List<WidgetHintRecord>,
    ): HintPoolDecision = if (newPool == oldPool) {
        HintPoolDecision.Unchanged
    } else {
        HintPoolDecision.Replace(newPool)
    }
}
