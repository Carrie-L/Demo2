package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle

class FavoritesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("收藏", "这是收藏页面的 Mock 实现。")
    }
}
