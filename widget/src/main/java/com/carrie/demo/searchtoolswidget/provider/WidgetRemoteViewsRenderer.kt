package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.carrie.demo.searchtoolswidget.R
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.remote.WidgetHintItemRemoteViews
import com.carrie.demo.searchtoolswidget.remote.HintRemoteViewsService
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetPendingIntents
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore

/**
 * 构建并下发小组件的完整 RemoteViews。
 *
 * 首次显示、暗词池替换和点击时下发完整 RemoteViews。日常 8 秒轮播由桌面宿主中的
 * AdapterViewFlipper 自己完成。点击时不设置索引，只在完整更新末尾附加 showNext。
 * 不能把 showNext 单独放入局部更新：系统合并时会忽略它，详见 WidgetInstanceUpdater。
 */
object WidgetRemoteViewsRenderer {
    /** 为系统指定的实例下发池数据；点击广播不按实例隔离。 */
    fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        displayedChild: Int?,
        advance: Boolean = false,
    ) {
        val store = MmkvWidgetStateStore.get()
        val pool = store.readPool()
        val hasRenderablePool = store.isPrivacyAllowed() && pool.isNotEmpty()
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(
                appWidgetId,
                buildViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    displayedChild = displayedChild,
                    hasRenderablePool = hasRenderablePool,
                    pool = pool,
                    advance = advance && hasRenderablePool,
                ),
            )
        }
    }

    /** 构建某一个 appWidgetId 的根布局、集合数据和全部点击入口。 */
    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        displayedChild: Int?,
        hasRenderablePool: Boolean,
        pool: List<WidgetHintRecord>,
        advance: Boolean,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_search_tools).apply {
            // Flipper 里的每个 item 只含“暗词 + 搜索按钮”；底部工具区属于根布局，
            // 因此 8 秒轮播不会再让收藏/历史/天气/设置一起闪动。
            bindHintCollection(context, appWidgetId, pool)
            setEmptyView(R.id.hint_flipper, R.id.empty_hint_container)
            setViewVisibility(
                R.id.hint_flipper,
                if (hasRenderablePool) View.VISIBLE else View.GONE,
            )
            setViewVisibility(
                R.id.empty_hint_container,
                if (hasRenderablePool) View.GONE else View.VISIBLE,
            )
            if (displayedChild != null) {
                setDisplayedChild(R.id.hint_flipper, displayedChild)
            }

            setOnClickPendingIntent(
                R.id.empty_hint_text,
                WidgetPendingIntents.action(
                    context,
                    WidgetAction.SEARCH_ACTIVATE,
                ),
            )
            setOnClickPendingIntent(
                R.id.empty_search_button,
                WidgetPendingIntents.action(
                    context,
                    WidgetAction.SEARCH_SUBMIT,
                ),
            )
            bindTool(context, R.id.tool_favorites, WidgetAction.FAVORITES)
            bindTool(context, R.id.tool_history, WidgetAction.HISTORY)
            bindTool(context, R.id.tool_weather, WidgetAction.WEATHER)
            bindTool(context, R.id.tool_settings, WidgetAction.SETTINGS)
            if (advance) {
                // 每次从全新 RemoteViews 构建，恰好追加一次，不在上次命令上不断累加。
                // 同布局 reapply 时不重新 inflate 静态按钮，也不把当前位置归零。
                @Suppress("DEPRECATION") // 产品选择宿主相对推进；不引入应用侧轮播计时器。
                showNext(R.id.hint_flipper)
            }
        }
    }

    /** 根据系统版本选择集合数据通道，不引入 androidx.core:core-remoteviews。 */
    private fun RemoteViews.bindHintCollection(
        context: Context,
        appWidgetId: Int,
        pool: List<WidgetHintRecord>,
    ) {
        when (WidgetCollectionMode.forSdk(Build.VERSION.SDK_INT)) {
            WidgetCollectionMode.INLINE_REMOTE_COLLECTION -> {
                bindInlineCollection(context, pool)
            }

            WidgetCollectionMode.LEGACY_REMOTE_SERVICE -> {
                bindLegacyCollection(context, appWidgetId, pool.hashCode())
            }
        }

        // 集合 item 不能直接持有普通 PendingIntent；它们通过 fill-in Intent 补充
        // 真实 URI/keyword，再与这个可变模板合并，最终交给 widget 自己的 Provider。
        setPendingIntentTemplate(
            R.id.hint_flipper,
            WidgetPendingIntents.collectionTemplate(context),
        )
    }

    /** API 31+：集合数据直接放入 RemoteViews，不再启动 RemoteViewsService 取数。 */
    @TargetApi(Build.VERSION_CODES.S) // 仅由上方 SDK >= 31 的分支调用，不额外依赖注解库。
    private fun RemoteViews.bindInlineCollection(
        context: Context,
        pool: List<WidgetHintRecord>,
    ) {
        val itemsBuilder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        pool.forEach { record ->
            itemsBuilder.addItem(record.id, WidgetHintItemRemoteViews.build(context, record))
        }
        setRemoteAdapter(R.id.hint_flipper, itemsBuilder.build())
    }

    /**
     * API 29～30：由系统绑定 RemoteViewsService，再向 RemoteViewsFactory 逐项取数。
     *
     * data URI 不是页面 Deep Link，也不会触发路由；它只是让系统把不同组件实例、
     * 不同池内容的 Adapter Intent 视为不同身份。poolToken 只服务旧版适配器缓存失效。
     */
    @Suppress("DEPRECATION") // Intent 形式在 API 35 才废弃；本方法只服务 API 29～30。
    private fun RemoteViews.bindLegacyCollection(
        context: Context,
        appWidgetId: Int,
        poolToken: Int,
    ) {
        val adapterIntent = Intent(context, HintRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("demo2://widget/adapter/$appWidgetId/$poolToken")
        }
        setRemoteAdapter(R.id.hint_flipper, adapterIntent)
    }

    /** 为根布局中始终静止的单个工具按钮绑定点击入口。 */
    private fun RemoteViews.bindTool(
        context: Context,
        viewId: Int,
        action: WidgetAction,
    ) {
        setOnClickPendingIntent(
            viewId,
            WidgetPendingIntents.action(context, action),
        )
    }
}
