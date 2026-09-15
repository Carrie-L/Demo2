package com.carrie.demo.searchmock.navigation

/** 主 App 通用入口/搜索模块约定。独立于 widget，只通过文档约定键名相同。 */
object SearchNavigationContract {
    /** 外部指定搜索词；是否冻结搜索暗词由搜索页自行处理。 */
    const val EXTRA_KEYWORD = "keyword"
}
