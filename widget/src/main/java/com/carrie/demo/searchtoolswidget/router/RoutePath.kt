package com.carrie.demo.searchtoolswidget.router

/** ARouter 固定路径；正式迁移时直接替换为宿主项目已有的 RoutePath 常量。 */
object RoutePath {
    /** Demo 首页。 */
    const val MAIN = "/main/home"

    /** 搜索激活页：展示组件带入的暗词，并冻结 App 原有暗词轮播。 */
    const val SEARCH_ACTIVATION = "/search/activation"

    /** 搜索结果页：使用组件当前暗词直接执行搜索。 */
    const val SEARCH_RESULT = "/search/result"

    /** 收藏页。 */
    const val FAVORITES = "/tools/favorites"

    /** 历史页。 */
    const val HISTORY = "/tools/history"

    /** 天气页。 */
    const val WEATHER = "/tools/weather"

    /** 设置页。 */
    const val SETTINGS = "/tools/settings"
}
