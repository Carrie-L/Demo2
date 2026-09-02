package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("历史", "这是搜索历史页面的 Mock 实现。")
    }
}
