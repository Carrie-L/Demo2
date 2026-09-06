package com.carrie.demo.searchmock.data

import android.content.Context

/** Demo 控制页可写入搜索数据库的几组固定样本。 */
object MockSearchData {
    /** 首次安装时写入的默认暗词池。 */
    val initial = listOf(
        SearchHintRecord(1L, "露营帐篷怎么选", 10),
        SearchHintRecord(2L, "周末北京天气", 20),
        SearchHintRecord(3L, "Android 小组件", 30),
        SearchHintRecord(4L, "草莓蛋糕做法", 40),
    )

    /** 用于验证“完整列表变化后换池并从头轮播”的新数据。 */
    val changed = listOf(
        SearchHintRecord(11L, "MagicOS 负一屏", 10),
        SearchHintRecord(12L, "露营地推荐", 20),
        SearchHintRecord(13L, "端侧大模型", 30),
        SearchHintRecord(14L, "今日咖啡", 40),
        SearchHintRecord(15L, "Kotlin 协程", 50),
    )

    /** 用于证明 widget 不会擅自按文案去重的样本。 */
    val withDuplicateKeyword = listOf(
        SearchHintRecord(21L, "重复暗词保留", 10),
        SearchHintRecord(22L, "重复暗词保留", 20),
        SearchHintRecord(23L, "数据库原始顺序", 30),
    )

    /** 只在首次启动时灌入初始数据，之后保留用户在 Demo 控制页写入的数据库状态。 */
    fun ensureSeeded(context: Context, dao: SearchHintDao) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_SEEDED, false)) {
            dao.replaceAll(initial)
            preferences.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    /** Mock 首次灌库状态的 SP 文件名。 */
    private const val PREFERENCES = "search_mock_state"

    /** 是否已经完成过首次灌库。 */
    private const val KEY_SEEDED = "database_seeded"
}
