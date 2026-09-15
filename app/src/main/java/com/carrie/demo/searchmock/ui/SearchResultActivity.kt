package com.carrie.demo.searchmock.ui

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.R
import com.carrie.demo.searchmock.navigation.SearchNavigationContract
import com.carrie.demo.searchmock.navigation.RoutePath

@Route(path = RoutePath.SEARCH_RESULT)
class SearchResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_result)
        val keyword = intent.getStringExtra(SearchNavigationContract.EXTRA_KEYWORD).orEmpty()
        findViewById<TextView>(R.id.result_keyword).text = if (keyword.isBlank()) {
            "没有传入关键词"
        } else {
            "正在展示「$keyword」的 Mock 搜索结果"
        }
    }
}
