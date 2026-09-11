package com.carrie.demo.searchtoolswidget.router

/**
 * 中转页的极小路由队列，只允许一个 ARouter 跳转正在执行。
 *
 * ARouter 的拦截器可以异步执行。用户在等待期间又点击其他工具时，若同时发起两个跳转，
 * 旧请求可能最后完成，再把旧页面盖到新页面上。这里等当前请求结束后再处理最新点击。
 * 所有调用均在 Activity 主线程；不需要线程锁、定时器或跨进程状态。
 */
internal class WidgetNavigationQueue {
    /** 是否已经提交了一次尚未收到结果回调的跳转。 */
    var isNavigating = false
        private set

    /** 等待期间只保留最后一次用户选择，避免依次打开已经不想去的中间页面。 */
    private var latestPendingRoute: WidgetRoute? = null

    /** 返回可立即执行的路由；返回 null 表示已记住新选择，等待当前跳转结束。 */
    fun submit(route: WidgetRoute): WidgetRoute? {
        if (isNavigating) {
            latestPendingRoute = route
            return null
        }
        isNavigating = true
        return route
    }

    /** 当前跳转成功、失败或被拦截后调用；返回下一跳，没有下一跳时允许结束中转页。 */
    fun complete(): WidgetRoute? {
        val next = latestPendingRoute
        latestPendingRoute = null
        isNavigating = next != null
        return next
    }
}
