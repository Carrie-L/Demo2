package com.carrie.demo.searchmock.data

import android.content.Context

class MockCloudConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun frequencyMinutes(): Int = preferences.getInt(KEY_FREQUENCY, DEFAULT_FREQUENCY)

    fun setFrequencyMinutes(minutes: Int) {
        preferences.edit().putInt(KEY_FREQUENCY, minutes).apply()
    }

    private companion object {
        const val PREFERENCES = "mock_cloud_config"
        const val KEY_FREQUENCY = "frequency"
        const val DEFAULT_FREQUENCY = 60
    }
}

