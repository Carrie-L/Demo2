package com.carrie.demo.searchmock.data

/** Mock 搜索模块的数据访问接口；widget 只允许复用其查询结果。 */
interface SearchHintDao {
    /** 搜索页和 widget Worker 共用的唯一查询，返回顺序就是暗词轮播顺序。 */
    fun queryHints(): List<SearchHintRecord>

    /** Demo 控制页整体替换数据库内容，用于模拟搜索模块下次网络落库。 */
    fun replaceAll(records: List<SearchHintRecord>)
}
