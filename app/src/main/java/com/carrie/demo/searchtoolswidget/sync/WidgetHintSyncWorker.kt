package com.carrie.demo.searchtoolswidget.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.carrie.demo.searchmock.data.PrivacyAgreementStore
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.provider.WidgetBroadcasts
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

class WidgetHintSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        WidgetStorageInitializer.initialize(applicationContext)
        val eligible = WidgetEligibility.canSync(
            privacyAccepted = PrivacyAgreementStore(applicationContext).isAccepted(),
            widgetCount = WidgetInstanceCounter.count(applicationContext),
        )
        if (!eligible) return Result.success()

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
                WidgetBroadcasts.sendPoolChanged(applicationContext)
                Result.success()
            }

            HintSyncOutcome.Unchanged -> Result.success()
            HintSyncOutcome.Failed -> {
                if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.success()
            }
        }
    }

    private companion object {
        const val MAX_RETRY_COUNT = 2
    }
}

