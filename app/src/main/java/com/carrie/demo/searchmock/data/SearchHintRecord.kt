package com.carrie.demo.searchmock.data

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord

/** Mock 主搜索模块自己的数据库记录模型。 */
data class SearchHintRecord(
    /** 数据库主键。 */
    val id: Long,
    /** 搜索暗词文案。 */
    val keyword: String,
    /** 搜索模块定义的顺序。 */
    val sortOrder: Int,
) {
    /** 在 Worker 边界映射为 widget 模块模型，不改变字段或顺序。 */
    fun toWidgetRecord(): WidgetHintRecord = WidgetHintRecord(
        id = id,
        keyword = keyword,
        sortOrder = sortOrder,
    )
}
