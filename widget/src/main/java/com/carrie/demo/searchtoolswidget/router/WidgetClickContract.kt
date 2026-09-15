package com.carrie.demo.searchtoolswidget.router

/** 跨模块只约定 URI 和可选 keyword，不共享业务枚举或 Activity 类型。 */
object WidgetClickContract {
    /** 与主 App 通用 deeplink 入口约定的搜索词键；工具按钮不传。 */
    const val EXTRA_KEYWORD = "keyword"
}
