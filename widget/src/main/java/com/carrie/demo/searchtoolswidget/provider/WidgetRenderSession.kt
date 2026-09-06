package com.carrie.demo.searchtoolswidget.provider

/**
 * 记录“当前 App 进程已经完整初始化过哪些小组件实例”。
 *
 * 这个状态故意只保存在内存：同一进程内重复收到系统 onUpdate 时跳过完整重建，
 * 避免打断桌面宿主当前的轮播位置；进程被杀后状态自然清空，重新从第一条初始化符合产品约定。
 */
class WidgetRenderSession {
    /** 当前进程已经完成首次完整渲染的 appWidgetId 集合。 */
    private val initializedWidgetIds = mutableSetOf<Int>()

    /**
     * 尝试登记一个准备首次渲染的组件实例。
     *
     * @return `true` 表示本进程尚未渲染过该实例，调用方应执行完整初始化；
     * `false` 表示它已初始化，调用方应保留宿主当前画面。
     */
    @Synchronized
    fun markForInitialRender(appWidgetId: Int): Boolean {
        return initializedWidgetIds.add(appWidgetId)
    }

    /** 在某个组件实例被删除时移除其进程内登记。 */
    @Synchronized
    fun remove(appWidgetId: Int) {
        initializedWidgetIds.remove(appWidgetId)
    }

    /** 在最后一个组件被删除时清空本进程的全部登记。 */
    @Synchronized
    fun clear() {
        initializedWidgetIds.clear()
    }
}

/**
 * 当前主进程共用的渲染会话。
 *
 * Provider、数据刷新器和点击更新器必须共用同一份登记，否则其中一处刚完成完整渲染，
 * 另一处随后收到系统 onUpdate 时仍会误判为“尚未初始化”，再次重建并打断轮播。
 */
internal object WidgetRenderSessionRegistry {
    /** 主进程中 Provider、刷新器和点击器共享的唯一会话实例。 */
    val current = WidgetRenderSession()
}
