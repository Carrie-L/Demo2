package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.searchtoolswidget.router.RoutePath

@Route(path = RoutePath.HISTORY)
class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("历史", "这是搜索历史页面的 Mock 实现。")
    }
}
