package com.carrie.demo.searchtoolswidget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.carrie.demo.searchtoolswidget.router.WidgetTaskLauncher
import com.carrie.demo.searchtoolswidget.router.WidgetClickContract
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer
import com.carrie.demo.searchtoolswidget.sync.WidgetScheduleActions

/**
 * 桌面与负一屏共用的小组件入口。
 *
 * 系统会把 APPWIDGET_UPDATE 广播转换成 [onUpdate] 回调。这里的核心约束是：
 * 系统重复发送更新广播时，只补齐本进程尚未初始化的实例，不能重建已经在轮播的实例，
 * 否则桌面宿主会把该实例重新放回第一条暗词。
 */
class SearchToolsWidgetProvider : AppWidgetProvider() {
    /** 系统要求创建或更新指定小组件实例时触发。 */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetStorageInitializer.initialize(context)

        // 仅完整渲染当前进程尚未初始化过的实例。已经显示中的实例由桌面宿主继续
        // 执行 AdapterViewFlipper 的 8 秒轮播，不因重复 onUpdate 被归零。
        val idsNeedingInitialRender = appWidgetIds.filter {
            WidgetRenderSessionRegistry.current.markForInitialRender(it)
        }.toIntArray()
        if (idsNeedingInitialRender.isEmpty()) return

        WidgetRemoteViewsRenderer.render(
            context = context,
            manager = appWidgetManager,
            appWidgetIds = idsNeedingInitialRender,
            displayedChild = null,
        )
    }

    /** 第一个同类组件被添加后，让主进程核对 WorkManager 调度状态。 */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        requestScheduleReconcile(context)
    }

    /** 最后一个同类组件被删除后，取消不再需要的定时同步任务。 */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRenderSessionRegistry.current.clear()
        requestScheduleReconcile(context)
    }

    /** 删除单个实例时移除其进程内登记，避免以后复用 id 时被错误跳过。 */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach(WidgetRenderSessionRegistry.current::remove)
        super.onDeleted(context, appWidgetIds)
    }

    /**
     * 接收本应用内部的“暗词池已替换”通知。
     *
     * 该广播由同步 Worker 在 MMKV 成功写入新池之后发送。此时产品语义要求所有实例
     * 一起换到新池并从第 0 条开始，因此这里允许完整刷新全部实例。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetBroadcasts.HINT_WORD_NEXT) {
            handleHintWordNext(context, intent)
            return // 不再发送同名广播，不进入系统 onUpdate，不重复推进。
        }
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 删除中转 Activity 后，旧宿主快照可能仍绑定旧 PendingIntent，必须主动换绑。
            // 只使用 MMKV 快照，不读取数据库，不 showNext，也不 setDisplayedChild(0)。
            // 该受保护广播只能由系统发送；重复收到只做幂等重绑。
            WidgetStorageInitializer.initialize(context)
            WidgetInstanceUpdater.refreshAll(context, displayedChild = null)
            return
        }
        if (intent.action == WidgetBroadcasts.ACTION_HINT_POOL_CHANGED) {
            WidgetStorageInitializer.initialize(context)
            WidgetInstanceUpdater.refreshAll(context, displayedChild = 0)
            return
        }
        super.onReceive(context, intent)
    }

    /**
     * 一次点击只在这里推进一次。先保存 item 入参，刷新后仍传点击时的原词。
     * 不查数据库、不等 Worker、不按 appWidgetId 隔离。方案 B 的主入口只发通知，推进仍在这里。
     */
    private fun handleHintWordNext(context: Context, intent: Intent) {
        try {
            val uri = intent.data
            val keyword = intent.getStringExtra(WidgetClickContract.EXTRA_KEYWORD)
            Log.d("WidgetClick", "收到 HINT_WORD_NEXT path=${uri?.path}")
            try {
                WidgetStorageInitializer.initialize(context)
                WidgetInstanceUpdater.advanceAll(context)
            } catch (error: Exception) {
                // 推进失败不吞掉打开 App 的请求；不输出暗词或系统异常中的参数。
                Log.e("WidgetClick", "推进组件失败: ${error.javaClass.simpleName}")
            }
            // 方案 A 有 URI，指定 AppTask 启动；方案 B 的 next 无 URI，到这里直接返回不再导航。
            WidgetTaskLauncher.open(context, uri, keyword)
        } catch (error: Exception) {
            // Intent 解包也可能失败；畸形输入安全结束，不用 requireNotNull/!!。
            Log.e("WidgetClick", "读取点击入参失败: ${error.javaClass.simpleName}")
        }
    }

    /** 请求调度接收器依据“隐私协议 + 是否存在实例”重新决定创建或取消任务。 */
    private fun requestScheduleReconcile(context: Context) {
        context.sendBroadcast(
            Intent(WidgetScheduleActions.ACTION_RECONCILE).setClassName(
                context.packageName,
                WidgetScheduleActions.RECEIVER_CLASS,
            ).putExtra(WidgetScheduleActions.EXTRA_ENQUEUE_IMMEDIATE, true),
        )
    }
}
