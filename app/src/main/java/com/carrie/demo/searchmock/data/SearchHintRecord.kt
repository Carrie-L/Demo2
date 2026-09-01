package com.carrie.demo.searchmock.data

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord

data class SearchHintRecord(
    val id: Long,
    val keyword: String,
    val sortOrder: Int,
) {
    fun toWidgetRecord(): WidgetHintRecord = WidgetHintRecord(
        id = id,
        keyword = keyword,
        sortOrder = sortOrder,
    )
}

