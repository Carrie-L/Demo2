package com.carrie.demo.searchmock.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.carrie.demo.R
import com.carrie.demo.searchtoolswidget.router.WidgetNavigationContract

class SearchResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_result)
        val keyword = intent.getStringExtra(WidgetNavigationContract.EXTRA_KEYWORD).orEmpty()
        findViewById<TextView>(R.id.result_keyword).text = if (keyword.isBlank()) {
            "没有传入关键词"
        } else {
            "正在展示「$keyword」的 Mock 搜索结果"
        }
    }
}

