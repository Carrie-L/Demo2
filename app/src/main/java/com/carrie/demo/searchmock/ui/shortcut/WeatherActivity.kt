package com.carrie.demo.searchmock.ui.shortcut

import android.app.Activity
import android.os.Bundle
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.searchtoolswidget.router.RoutePath

@Route(path = RoutePath.WEATHER)
class WeatherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderShortcutPage("天气", "这是天气工具页面的 Mock 实现。")
    }
}
