package com.carrie.demo.searchtoolswidget.provider

/**
 * 计算一次用户点击应该更新哪些小组件实例。
 *
 * 用户点击桌面上的 A 实例时，只应让 A 的暗词前进一条；B 实例和负一屏实例都保持
 * 各自当前的轮播进度。因此这里绝不能返回“全部已安装实例”。
 */
object WidgetUpdateScope {
    /**
     * 返回本次点击唯一允许更新的 [appWidgetId]。
     *
     * [android.appwidget.AppWidgetManager] 分配的有效 id 为正数。若入口参数丢失或非法，
     * 返回空数组，让调用方安全地放弃组件更新，但仍可继续完成页面跳转。
     */
    fun forClick(appWidgetId: Int): IntArray {
        return if (appWidgetId > 0) intArrayOf(appWidgetId) else intArrayOf()
    }
}
