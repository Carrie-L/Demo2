package com.carrie.demo.searchtoolswidget.router

/** 小组件中可发生的六种用户点击。 */
enum class WidgetAction {
    /** 点击暗词搜索框，进入搜索激活页。 */
    SEARCH_ACTIVATE,

    /** 点击搜索按钮，直接进入搜索结果页。 */
    SEARCH_SUBMIT,

    /** 点击收藏工具。 */
    FAVORITES,

    /** 点击历史工具。 */
    HISTORY,

    /** 点击天气工具。 */
    WEATHER,

    /** 点击设置工具。 */
    SETTINGS,
}

/** 把一次点击解析成主 App 能理解的 ARouter 路径和参数。 */
data class WidgetRoute(
    /** 固定 RoutePath，不把任意外部 URI 直接交给路由器。 */
    val targetRoutePath: String,

    /** 仅搜索相关入口保留暗词；工具页固定为空。 */
    val keyword: String,

    /** 仅带有效暗词进入搜索激活页时为 true。 */
    val freezeHintRotation: Boolean,
) {
    companion object {
        /** 根据动作生成安全、确定的内部路由。 */
        fun resolve(action: WidgetAction, keyword: String?): WidgetRoute {
            // 空白暗词按“没有暗词”处理，避免 App 搜索框显示纯空格。
            val safeKeyword = keyword?.takeIf { it.isNotBlank() }.orEmpty()
            return when (action) {
                WidgetAction.SEARCH_ACTIVATE -> WidgetRoute(
                    targetRoutePath = RoutePath.SEARCH_ACTIVATION,
                    keyword = safeKeyword,
                    freezeHintRotation = safeKeyword.isNotEmpty(),
                )

                WidgetAction.SEARCH_SUBMIT -> WidgetRoute(
                    targetRoutePath = RoutePath.SEARCH_RESULT,
                    keyword = safeKeyword,
                    freezeHintRotation = false,
                )

                WidgetAction.FAVORITES -> toolRoute(RoutePath.FAVORITES)
                WidgetAction.HISTORY -> toolRoute(RoutePath.HISTORY)
                WidgetAction.WEATHER -> toolRoute(RoutePath.WEATHER)
                WidgetAction.SETTINGS -> toolRoute(RoutePath.SETTINGS)
            }
        }

        /** 工具按钮只跳固定页面，不携带 keyword，也不控制 App 搜索轮播。 */
        private fun toolRoute(targetRoutePath: String) = WidgetRoute(
            targetRoutePath = targetRoutePath,
            keyword = "",
            freezeHintRotation = false,
        )
    }
}
