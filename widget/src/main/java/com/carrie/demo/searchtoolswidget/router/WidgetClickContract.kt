package com.carrie.demo.searchtoolswidget.router

/** Widget 点击入口内部使用的 Intent extra 协议。 */
object WidgetClickContract {
    /**
     * 显式 Activity Intent 的入口标识，同时参与 PendingIntent 身份匹配。
     * 旧版没有该 action；增加它使更新后的组件重新创建带正确启动 flags 的 token。
     */
    const val ACTION_OPEN_WIDGET_ROUTE = "com.carrie.demo.searchtoolswidget.action.OPEN_WIDGET_ROUTE"

    /** 点击类型，对应 [WidgetAction.name]。 */
    const val EXTRA_ACTION = "widget_action"

    /** 仅搜索框/搜索按钮携带的当前暗词；普通工具按钮不传。 */
    const val EXTRA_KEYWORD = "widget_click_keyword"
}
