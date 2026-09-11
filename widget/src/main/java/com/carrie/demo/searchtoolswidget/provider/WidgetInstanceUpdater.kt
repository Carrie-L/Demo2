package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore

/**
 * 处理“哪些组件实例需要更新”。
 *
 * 两种更新都面向桌面和负一屏的全部已安装实例，但推进方式不同：
 * 1. 暗词池成功替换：所有实例共享新池，因此全部回到新池第一条；
 * 2. 用户点击任意实例：给所有实例发送 showNext，各自从当前位置前进一条，不强行对齐。
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
     * 点击任意按钮后，让全部已安装实例各自立即前进一条。
     *
     * showNext 最终在宿主的 AdapterViewFlipper 上执行，不需要 App 自己计时或记录索引。
     * 注意不能用 partiallyUpdateAppWidget：系统合并局部 RemoteViews 时，会忽略
     * showNext 的一次性导航 Action（MERGE_IGNORE），导致点击后完全没有前进。
     * 因此发送完整布局和数据，再附加一个 showNext；不带 setDisplayedChild(0)，
     * 宿主复用相同布局时从原位置前进，且没有淡入淡出动画。不是每 8 秒重发完整布局。
     */
    fun advanceAll(context: Context) {
        val targetIds = allWidgetIds(context)
        if (targetIds.isEmpty()) return

        val store = MmkvWidgetStateStore.get()
        if (!store.isPrivacyAllowed() || store.readPool().isEmpty()) return

        val manager = AppWidgetManager.getInstance(context)
        WidgetRemoteViewsRenderer.render(
            context, manager, targetIds, displayedChild = null, advance = true,
        )
        targetIds.forEach(WidgetRenderSessionRegistry.current::markForInitialRender)
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
