package com.carrie.demo.searchtoolswidget.domain

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord

sealed interface HintPoolDecision {
    data object Unchanged : HintPoolDecision

    data class Replace(val newPool: List<WidgetHintRecord>) : HintPoolDecision
}

object HintPoolDecider {
    fun decide(
        oldPool: List<WidgetHintRecord>,
        newPool: List<WidgetHintRecord>,
    ): HintPoolDecision = if (newPool == oldPool) {
        HintPoolDecision.Unchanged
    } else {
        HintPoolDecision.Replace(newPool)
    }
}

