package com.carrie.demo.searchmock.data

import android.content.Context

/** 用 SP 模拟云配置中心下发的 `frequency` 分钟字段。 */
class MockCloudConfigStore(context: Context) {
    /** Mock 云配持久文件。 */
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    /** 读取 frequency；未配置时返回产品默认 60 分钟。 */
    fun frequencyMinutes(): Int = preferences.getInt(KEY_FREQUENCY, DEFAULT_FREQUENCY)

    /** Demo 控制页修改云配值，随后需发 RECONCILE 才会更新周期任务。 */
    fun setFrequencyMinutes(minutes: Int) {
        preferences.edit().putInt(KEY_FREQUENCY, minutes).apply()
    }

    private companion object {
        /** SP 文件名。 */
        const val PREFERENCES = "mock_cloud_config"
        /** 对应正式云配字段 frequency。 */
        const val KEY_FREQUENCY = "frequency"
        /** 云端无值时使用的默认分钟数。 */
        const val DEFAULT_FREQUENCY = 60
    }
}
