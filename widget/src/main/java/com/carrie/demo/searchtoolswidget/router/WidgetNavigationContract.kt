package com.carrie.demo.searchtoolswidget.router

/** widget 模块通过 ARouter 交给主 App 页面的参数协议。 */
object WidgetNavigationContract {
    /** 搜索入口携带的当前暗词。 */
    const val EXTRA_KEYWORD = "widget_keyword"

    /** 搜索激活页是否停止主 App 原有暗词轮播，并固定展示带入词。 */
    const val EXTRA_FREEZE_HINT_ROTATION = "freeze_hint_rotation"
}
