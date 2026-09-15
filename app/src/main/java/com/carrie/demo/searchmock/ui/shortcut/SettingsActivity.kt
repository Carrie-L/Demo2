package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.searchmock.navigation.RoutePath

@Route(path = RoutePath.SETTINGS)
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("设置", "这是设置页面的 Mock 实现。")
    }
}
