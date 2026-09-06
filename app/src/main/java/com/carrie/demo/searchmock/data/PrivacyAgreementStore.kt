package com.carrie.demo.searchmock.data

import android.content.Context

/** 用主 App 的 SP 模拟正式项目隐私协议同意状态。 */
class PrivacyAgreementStore(context: Context) {
    /** 隐私状态持久文件。 */
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    /** 用户是否已同意隐私协议，默认 false。 */
    fun isAccepted(): Boolean = preferences.getBoolean(KEY_ACCEPTED, false)

    /** 修改同意状态；调用方随后通过 RECONCILE 让任务和组件响应。 */
    fun setAccepted(accepted: Boolean) {
        preferences.edit().putBoolean(KEY_ACCEPTED, accepted).apply()
    }

    private companion object {
        /** SP 文件名。 */
        const val PREFERENCES = "privacy_agreement"
        /** 隐私同意布尔 key。 */
        const val KEY_ACCEPTED = "accepted"
    }
}
