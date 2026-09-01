package com.carrie.demo.searchtoolswidget.sync

object FrequencyPolicy {
    const val DEFAULT_MINUTES = 60L
    const val MINIMUM_MINUTES = 15L

    fun sanitizeMinutes(configuredMinutes: Int?): Long = when {
        configuredMinutes == null || configuredMinutes <= 0 -> DEFAULT_MINUTES
        configuredMinutes < MINIMUM_MINUTES -> MINIMUM_MINUTES
        else -> configuredMinutes.toLong()
    }
}

