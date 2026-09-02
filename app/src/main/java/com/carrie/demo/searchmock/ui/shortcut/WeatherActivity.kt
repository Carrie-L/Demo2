package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle

class WeatherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("天气", "这是天气工具页面的 Mock 实现。")
    }
}
