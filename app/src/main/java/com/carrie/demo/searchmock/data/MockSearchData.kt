package com.carrie.demo.searchmock.data

import android.content.Context

object MockSearchData {
    val initial = listOf(
        SearchHintRecord(1L, "露营帐篷怎么选", 10),
        SearchHintRecord(2L, "周末北京天气", 20),
        SearchHintRecord(3L, "Android 小组件", 30),
        SearchHintRecord(4L, "草莓蛋糕做法", 40),
    )

    val changed = listOf(
        SearchHintRecord(11L, "MagicOS 负一屏", 10),
        SearchHintRecord(12L, "露营地推荐", 20),
        SearchHintRecord(13L, "端侧大模型", 30),
        SearchHintRecord(14L, "今日咖啡", 40),
        SearchHintRecord(15L, "Kotlin 协程", 50),
    )

    val withDuplicateKeyword = listOf(
        SearchHintRecord(21L, "重复暗词保留", 10),
        SearchHintRecord(22L, "重复暗词保留", 20),
        SearchHintRecord(23L, "数据库原始顺序", 30),
    )

    fun ensureSeeded(context: Context, dao: SearchHintDao) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_SEEDED, false)) {
            dao.replaceAll(initial)
            preferences.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    private const val PREFERENCES = "search_mock_state"
    private const val KEY_SEEDED = "database_seeded"
}

