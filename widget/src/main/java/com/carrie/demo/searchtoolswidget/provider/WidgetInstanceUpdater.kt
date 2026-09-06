package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore

/**
 * 处理“哪些组件实例需要更新”。
 *
 * 两种更新的范围不同：
 * 1. 暗词池成功替换：所有实例共享新池，因此全部回到新池第一条；
 * 2. 用户点击某个实例：只给该 [appWidgetId] 发送 showNext 命令，其他实例不动。
 */
object WidgetInstanceUpdater {
    /** 查询桌面与负一屏当前安装的所有本类型小组件实例 id。 */
    fun allWidgetIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context.packageName, WidgetComponentNames.PROVIDER_CLASS),
        )
    }

    /**
     * 新池写入成功后刷新全部实例。
     *
     * API 29～30 的集合数据由 RemoteViewsService 提供，需要显式通知宿主重新取数；
     * API 31+ 的数据直接随 RemoteViews.RemoteCollectionItems 下发，重新 render 即可。
     */
    fun refreshAll(context: Context, displayedChild: Int?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = allWidgetIds(context)
        if (ids.isEmpty()) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            notifyLegacyCollectionChanged(manager, ids)
        }
        WidgetRemoteViewsRenderer.render(context, manager, ids, displayedChild)
        ids.forEach(WidgetRenderSessionRegistry.current::markForInitialRender)
    }

    /**
     * 让用户刚刚点击的那个实例立即显示下一条暗词。
     *
     * [RemoteViews.showNext] 最终在桌面宿主进程中的 AdapterViewFlipper 上执行，所以
     * 即使应用 Activity 已退出后台，宿主仍能完成这次切换；它不会刷新静态工具按钮。
     */
    @Suppress("DEPRECATION") // 产品明确选择 showNext；API 33 起虽标废弃，在 minSdk 29 仍需兼容。
    fun advanceOne(context: Context, appWidgetId: Int) {
        val targetIds = WidgetUpdateScope.forClick(appWidgetId)
        if (targetIds.isEmpty()) return

        val store = MmkvWidgetStateStore.get()
        if (!store.isPrivacyAllowed() || store.readPool().isEmpty()) return

        val manager = AppWidgetManager.getInstance(context)
        val command = RemoteViews(context.packageName, R.layout.widget_search_tools).apply {
            // 这里只下发一个“下一条”命令，不重建 RemoteViews，也不计算或保存宿主的当前位置。
            showNext(R.id.hint_flipper)
        }
        manager.partiallyUpdateAppWidget(targetIds, command)
        WidgetRenderSessionRegistry.current.markForInitialRender(appWidgetId)
    }

    /** API 29～30 专用：通知 RemoteViewsFactory 重新执行 onDataSetChanged。 */
    @Suppress("DEPRECATION") // API 35 才废弃；这里只在 API 29～30 分支调用。
    private fun notifyLegacyCollectionChanged(
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.hint_flipper)
    }
}
