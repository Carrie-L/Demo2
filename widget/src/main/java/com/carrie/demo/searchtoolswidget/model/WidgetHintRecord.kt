package com.carrie.demo.searchtoolswidget.model

/** Complete record mirrored from the search module query. Field and list order both matter. */
data class WidgetHintRecord(
    val id: Long,
    val keyword: String,
    val sortOrder: Int,
)

