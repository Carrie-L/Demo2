package com.carrie.demo.searchtoolswidget.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.carrie.demo.searchmock.data.MockCloudConfigStore
import com.carrie.demo.searchmock.data.PrivacyAgreementStore
import com.carrie.demo.searchtoolswidget.provider.WidgetBroadcasts
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
import java.util.concurrent.TimeUnit

/**
 * 小组件暗词任务的 WorkManager 调度中心。
 *
 * WorkManager 不是用来做 8 秒轮播的：8 秒由桌面宿主中的 AdapterViewFlipper 负责。
 * 它只负责可靠地安排“默认每 60 分钟从主搜索数据库同步一次暗词池”。即使应用进程
 * 曾被杀，系统之后仍可按 WorkManager 的约束重新启动进程执行 Worker。
 *
 * 本类维护两个“唯一任务名”，但两者执行的是同一个 [WidgetHintSyncWorker]：
 * - 周期任务：长期每 frequency 分钟执行；
 * - 立即任务：首次添加组件或重新同意隐私后执行一次，让组件不必先空等一个周期。
 * 分开命名是因为 WorkManager 的一次性任务与周期任务是两种不同类型，不能共用同一
 * 唯一任务槽；它们不是两个 Worker 类，也不会产生两套数据处理逻辑。
 */
class WidgetHintSyncScheduler(context: Context) {
    /** 始终持有 Application Context，避免调度器错误持有 Activity。 */
    private val appContext = context.applicationContext

    /** WorkManager 是任务持久化、约束与恢复执行的系统入口。 */
    private val workManager = WorkManager.getInstance(appContext)

    /** 保存暗词池、上次成功时间、隐私门禁快照和当前 frequency。 */
    private val stateStore = MmkvWidgetStateStore.get()

    /**
     * 根据当前真实状态协调周期任务。
     *
     * 调用场景：App 冷启动、首个/最后一个组件变化、隐私状态变化、frequency 变化。
     * [enqueueImmediate] 只决定本次协调后是否额外立刻同步一次，不影响周期任务本身。
     */
    fun reconcile(enqueueImmediate: Boolean) {
        val privacyAccepted = PrivacyAgreementStore(appContext).isAccepted()
        val widgetCount = WidgetInstanceCounter.count(appContext)

        // Provider 渲染时只依赖 widget 模块自己的 Store；这里把主 App 的 SP 状态同步进去。
        stateStore.setPrivacyAllowed(privacyAccepted)

        if (!WidgetEligibility.canSync(privacyAccepted, widgetCount)) {
            // 没同意协议或一个组件都没有时，不允许后台读取搜索数据库。
            cancelAllWidgetWork()
            if (!privacyAccepted) {
                // 撤回隐私同意后清空已缓存暗词，并让仍存在的实例立即显示兜底文案。
                stateStore.clearPool()
                if (widgetCount > 0) {
                    WidgetBroadcasts.sendPoolChanged(appContext)
                }
            }
            return
        }

        // 云配置字段单位为 min。异常值和小于 15 分钟的值由 FrequencyPolicy 统一修正。
        val frequency = FrequencyPolicy.sanitizeMinutes(
            MockCloudConfigStore(appContext).frequencyMinutes(),
        )

        // 如果配置没变化，保留既有任务的计时；变化时原位更新同名周期任务。
        val periodicWorkPolicy = PeriodicWorkPolicySelector.select(
            storedMinutes = stateStore.frequencyMinutes(),
            requestedMinutes = frequency,
        )

        // PeriodicWorkRequest 只保证“不早于周期约束的合适时间执行”，不承诺整点精确触发。
        // 首次同步由下面的一次性任务负责，所以周期任务首轮延迟一个完整 frequency。
        val periodicRequest = PeriodicWorkRequestBuilder<WidgetHintSyncWorker>(
            frequency,
            TimeUnit.MINUTES,
        ).setInitialDelay(frequency, TimeUnit.MINUTES).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            periodicWorkPolicy,
            periodicRequest,
        )

        // 保存本次已应用的值，下一次 reconcile 才能判断是否真的发生配置变化。
        stateStore.setFrequencyMinutes(frequency)
        if (enqueueImmediate) {
            enqueueImmediateSync()
        }
    }

    /**
     * 创建一次立即同步任务。
     *
     * 再次检查资格是为了防止广播排队期间用户删除组件或撤回隐私同意。
     */
    private fun enqueueImmediateSync() {
        val eligible = WidgetEligibility.canSync(
            privacyAccepted = PrivacyAgreementStore(appContext).isAccepted(),
            widgetCount = WidgetInstanceCounter.count(appContext),
        )
        if (!eligible) return

        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            // 短时间重复触发时只保留最新的一次请求，防止并发查询和旧结果覆盖新结果。
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetHintSyncWorker>().build(),
        )
    }

    /** 同时取消本功能名下的周期任务和尚未执行的一次性任务。 */
    private fun cancelAllWidgetWork() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    companion object {
        /** 周期同步任务在 WorkManager 数据库中的唯一名字。 */
        const val PERIODIC_WORK_NAME = "search_tools_widget_hint_sync"

        /** 立即同步任务的唯一名字；与周期任务分开，但仍执行同一个 Worker 类。 */
        const val IMMEDIATE_WORK_NAME = "search_tools_widget_hint_sync_now"
    }
}
