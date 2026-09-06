package com.carrie.demo.searchmock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.R
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchmock.domain.SearchLaunchState
import com.carrie.demo.searchtoolswidget.router.WidgetNavigationContract
import com.carrie.demo.searchtoolswidget.router.RoutePath

@Route(path = RoutePath.SEARCH_ACTIVATION)
class SearchActivationActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var searchInput: EditText
    private var hints: List<String> = emptyList()
    private var nextIndex = 0

    private val rotateRunnable = object : Runnable {
        override fun run() {
            if (hints.isNotEmpty()) {
                searchInput.hint = hints[nextIndex % hints.size]
                nextIndex += 1
                handler.postDelayed(this, ROTATION_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_activation)

        searchInput = findViewById(R.id.search_input)
        val state = SearchLaunchState.resolve(
            keyword = intent.getStringExtra(WidgetNavigationContract.EXTRA_KEYWORD),
            freezeHintRotation = intent.getBooleanExtra(
                WidgetNavigationContract.EXTRA_FREEZE_HINT_ROTATION,
                false,
            ),
        )
        searchInput.setText(state.initialKeyword)
        findViewById<TextView>(R.id.rotation_status).text = if (state.shouldRotateHints) {
            "App 原有暗词轮播：运行中（8 秒）"
        } else {
            "App 原有暗词轮播：已由组件入参冻结"
        }

        findViewById<Button>(R.id.search_submit_button).setOnClickListener {
            startActivity(
                Intent(this, SearchResultActivity::class.java).putExtra(
                    WidgetNavigationContract.EXTRA_KEYWORD,
                    searchInput.text.toString(),
                ),
            )
        }

        if (state.shouldRotateHints) {
            hints = SearchMockGraph.hintDao(this).queryHints().map { it.keyword }
            if (hints.isNotEmpty()) {
                rotateRunnable.run()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(rotateRunnable)
        super.onDestroy()
    }

    private companion object {
        const val ROTATION_INTERVAL_MS = 8_000L
    }
}
