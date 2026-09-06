package com.carrie.demo.searchtoolswidget.remote

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

/**
 * API 29～30 的暗词集合数据源。
 *
 * 桌面宿主通过 [HintRemoteViewsService] 创建它，然后按 position 请求每个暗词 item。
 * API 31+ 已改用 RemoteViews.RemoteCollectionItems，不会走到本类。
 */
class HintRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    /** 当前一次数据快照；顺序就是轮播顺序。 */
    private var records: List<WidgetHintRecord> = emptyList()

    /** Factory 首次创建时初始化跨进程 MMKV，并读取一份池快照。 */
    override fun onCreate() {
        WidgetStorageInitializer.initialize(context)
        reload()
    }

    /** AppWidgetManager 通知数据变化后，整体替换当前快照。 */
    override fun onDataSetChanged() {
        reload()
    }

    /** 释放宿主持有的列表引用。 */
    override fun onDestroy() {
        records = emptyList()
    }

    /** 告诉宿主当前共有多少条暗词。 */
    override fun getCount(): Int = records.size

    /** 只构建“暗词 + 搜索按钮”，底部工具按钮不属于轮播 item。 */
    override fun getViewAt(position: Int): RemoteViews {
        val record = records.getOrNull(position)
            ?: return WidgetHintItemRemoteViews.empty(context)
        return WidgetHintItemRemoteViews.build(context, record)
    }

    /** 使用系统默认 loading view；池数据很小，无需额外占位布局。 */
    override fun getLoadingView(): RemoteViews? = null

    /** 所有暗词共用同一种 item 布局。 */
    override fun getViewTypeCount(): Int = 1

    /** 数据库记录 id 作为稳定 id，帮助宿主识别同一条数据。 */
    override fun getItemId(position: Int): Long = records.getOrNull(position)?.id ?: position.toLong()

    /** 与 [getItemId] 配套声明稳定 id。 */
    override fun hasStableIds(): Boolean = true

    /** 隐私未同意时返回空池，由根 RemoteViews 显示兜底文案并停止轮播。 */
    private fun reload() {
        val store = MmkvWidgetStateStore.get()
        records = if (store.isPrivacyAllowed()) store.readPool() else emptyList()
    }
}
