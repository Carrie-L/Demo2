package com.carrie.demo.searchmock.data

import android.content.Context

class PrivacyAgreementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun isAccepted(): Boolean = preferences.getBoolean(KEY_ACCEPTED, false)

    fun setAccepted(accepted: Boolean) {
        preferences.edit().putBoolean(KEY_ACCEPTED, accepted).apply()
    }

    private companion object {
        const val PREFERENCES = "privacy_agreement"
        const val KEY_ACCEPTED = "accepted"
    }
}

