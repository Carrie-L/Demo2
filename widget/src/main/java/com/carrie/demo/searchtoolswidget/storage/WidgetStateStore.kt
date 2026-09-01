package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord

interface WidgetStateStore {
    fun readPool(): List<WidgetHintRecord>
    fun replacePool(pool: List<WidgetHintRecord>)
    fun clearPool()
    fun updateLastSuccessfulRefreshAt(timestampMillis: Long)
    fun lastSuccessfulRefreshAtMillis(): Long
    fun setPrivacyAllowed(allowed: Boolean)
    fun isPrivacyAllowed(): Boolean
    fun setFrequencyMinutes(minutes: Long)
    fun frequencyMinutes(): Long
}

