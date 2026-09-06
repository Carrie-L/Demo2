package com.carrie.demo.searchtoolswidget.model

/**
 * 从主搜索模块 DAO 查询结果映射出的完整暗词记录。
 *
 * 比较新旧池时三个字段和列表顺序都参与判断；[keyword] 是组件实际展示和搜索跳转参数。
 */
data class WidgetHintRecord(
    /** 数据库记录稳定 id，也作为 API 31+ RemoteCollectionItems 的 itemId。 */
    val id: Long,

    /** 显示在搜索框中的暗词。 */
    val keyword: String,

    /** 主搜索模块返回的排序字段；widget 不再自行排序。 */
    val sortOrder: Int,
)
