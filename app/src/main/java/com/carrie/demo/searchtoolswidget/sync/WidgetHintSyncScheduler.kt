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

class WidgetHintSyncScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val stateStore = MmkvWidgetStateStore.get()

    fun reconcile(enqueueImmediate: Boolean) {
        val privacyAccepted = PrivacyAgreementStore(appContext).isAccepted()
        val widgetCount = WidgetInstanceCounter.count(appContext)
        stateStore.setPrivacyAllowed(privacyAccepted)

        if (!WidgetEligibility.canSync(privacyAccepted, widgetCount)) {
            cancelAllWidgetWork()
            if (!privacyAccepted) {
                stateStore.clearPool()
                if (widgetCount > 0) {
                    WidgetBroadcasts.sendPoolChanged(appContext)
                }
            }
            return
        }

        val frequency = FrequencyPolicy.sanitizeMinutes(
            MockCloudConfigStore(appContext).frequencyMinutes(),
        )
        val periodicWorkPolicy = PeriodicWorkPolicySelector.select(
            storedMinutes = stateStore.frequencyMinutes(),
            requestedMinutes = frequency,
        )
        val periodicRequest = PeriodicWorkRequestBuilder<WidgetHintSyncWorker>(
            frequency,
            TimeUnit.MINUTES,
        ).setInitialDelay(frequency, TimeUnit.MINUTES).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            periodicWorkPolicy,
            periodicRequest,
        )
        stateStore.setFrequencyMinutes(frequency)
        if (enqueueImmediate) {
            enqueueImmediateSync()
        }
    }

    fun enqueueImmediateSync() {
        val eligible = WidgetEligibility.canSync(
            privacyAccepted = PrivacyAgreementStore(appContext).isAccepted(),
            widgetCount = WidgetInstanceCounter.count(appContext),
        )
        if (!eligible) return

        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetHintSyncWorker>().build(),
        )
    }

    private fun cancelAllWidgetWork() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    companion object {
        const val PERIODIC_WORK_NAME = "search_tools_widget_hint_sync"
        const val IMMEDIATE_WORK_NAME = "search_tools_widget_hint_sync_now"
    }
}
