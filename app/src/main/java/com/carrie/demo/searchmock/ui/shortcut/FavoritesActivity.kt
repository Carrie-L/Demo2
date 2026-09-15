package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.searchmock.navigation.RoutePath

@Route(path = RoutePath.FAVORITES)
class FavoritesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("收藏", "这是收藏页面的 Mock 实现。")
    }
}
