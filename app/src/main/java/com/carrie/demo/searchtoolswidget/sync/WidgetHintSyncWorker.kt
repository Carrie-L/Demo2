package com.carrie.demo.searchtoolswidget.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.carrie.demo.searchmock.data.PrivacyAgreementStore
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.provider.WidgetBroadcasts
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

/**
 * WorkManager 真正执行的一次暗词同步单元。
 *
 * 周期任务与立即任务都复用本类，所以“从 DAO 读取、比较、覆盖 MMKV、通知组件”的
 * 业务流程只有一份。这里使用同步 [Worker] 是因为 Demo DAO 查询很短；正式项目若 DAO
 * 是 suspend API，可迁移为 CoroutineWorker，但业务边界保持不变。
 */
class WidgetHintSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {

    /**
     * WorkManager 后台线程调用的方法。
     * 返回 success 表示本轮结束；retry 表示按 WorkManager 退避策略稍后重试。
     */
    override fun doWork(): Result {
        WidgetStorageInitializer.initialize(applicationContext)

        // 任务可能已在队列中等待一段时间，执行前必须再次检查隐私和组件数量。
        val eligible = WidgetEligibility.canSync(
            privacyAccepted = PrivacyAgreementStore(applicationContext).isAccepted(),
            widgetCount = WidgetInstanceCounter.count(applicationContext),
        )
        if (!eligible) return Result.success()

        // 正式迁移时只替换 source 内的 DAO 获取方式：它必须与搜索模块使用同一查询语句，
        // widget 侧不二次排序、不按文案去重，数据库返回顺序就是最终轮播顺序。
        val engine = HintSyncEngine(
            source = {
                SearchMockGraph.hintDao(applicationContext)
                    .queryHints()
                    .map { it.toWidgetRecord() }
            },
            store = MmkvWidgetStateStore.get(),
        )
        return when (engine.sync()) {
            HintSyncOutcome.Changed -> {
                // 只有完整记录列表真的变化时才通知 Provider：全部实例换新池并从第 0 条开始。
                WidgetBroadcasts.sendPoolChanged(applicationContext)
                Result.success()
            }

            // 内容相同只更新时间，不刷新 RemoteViews，因此不会把轮播进度归零。
            HintSyncOutcome.Unchanged -> Result.success()
            HintSyncOutcome.Failed -> {
                // 第 0 次是首次执行，之后最多额外 retry 两次；最终仍保留旧池。
                if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.success()
            }
        }
    }

    private companion object {
        /** 查询/写入异常时允许的额外重试次数，总尝试次数最多为 3。 */
        const val MAX_RETRY_COUNT = 2
    }
}
