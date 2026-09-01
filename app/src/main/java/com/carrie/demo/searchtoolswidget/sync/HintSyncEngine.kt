package com.carrie.demo.searchtoolswidget.sync

import com.carrie.demo.searchtoolswidget.domain.HintPoolDecider
import com.carrie.demo.searchtoolswidget.domain.HintPoolDecision
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.storage.WidgetStateStore

enum class HintSyncOutcome {
    Changed,
    Unchanged,
    Failed,
}

fun interface WidgetHintSource {
    fun query(): List<WidgetHintRecord>
}

class HintSyncEngine(
    private val source: WidgetHintSource,
    private val store: WidgetStateStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun sync(): HintSyncOutcome = try {
        val newPool = source.query()
        when (val decision = HintPoolDecider.decide(store.readPool(), newPool)) {
            HintPoolDecision.Unchanged -> {
                store.updateLastSuccessfulRefreshAt(nowMillis())
                HintSyncOutcome.Unchanged
            }

            is HintPoolDecision.Replace -> {
                store.replacePool(decision.newPool)
                store.updateLastSuccessfulRefreshAt(nowMillis())
                HintSyncOutcome.Changed
            }
        }
    } catch (_: Exception) {
        HintSyncOutcome.Failed
    }
}

