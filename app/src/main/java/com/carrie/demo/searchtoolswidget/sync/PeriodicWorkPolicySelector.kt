package com.carrie.demo.searchtoolswidget.sync

import androidx.work.ExistingPeriodicWorkPolicy

object PeriodicWorkPolicySelector {
    fun select(storedMinutes: Long, requestedMinutes: Long): ExistingPeriodicWorkPolicy =
        if (storedMinutes == requestedMinutes) {
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            ExistingPeriodicWorkPolicy.UPDATE
        }
}
