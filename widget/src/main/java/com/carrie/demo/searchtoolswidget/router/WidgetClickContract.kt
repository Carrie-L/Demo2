package com.carrie.demo.searchtoolswidget.router

/** Widget 点击入口内部使用的 Intent extra 协议。 */
object WidgetClickContract {
    /** 点击类型，对应 [WidgetAction.name]。 */
    const val EXTRA_ACTION = "widget_action"

    /** 仅搜索框/搜索按钮携带的当前暗词；普通工具按钮不传。 */
    const val EXTRA_KEYWORD = "widget_click_keyword"
}
