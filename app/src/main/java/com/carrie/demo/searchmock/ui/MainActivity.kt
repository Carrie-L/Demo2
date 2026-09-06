package com.carrie.demo.searchmock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.alibaba.android.arouter.facade.annotation.Route
import com.carrie.demo.R
import com.carrie.demo.searchmock.data.MockCloudConfigStore
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.PrivacyAgreementStore
import com.carrie.demo.searchmock.data.SearchHintDao
import com.carrie.demo.searchmock.data.SearchHintRecord
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.sync.WidgetScheduleActions
import com.carrie.demo.searchtoolswidget.router.RoutePath

/**
 * Demo 控制台：模拟隐私、云配和搜索模块落库。
 * 这些按钮是验收工具，不属于正式小组件 UI。
 */
@Route(path = RoutePath.MAIN)
class MainActivity : Activity() {
    /** 主搜索模块 DAO；页面预览和 Worker 使用同一 queryHints。 */
    private lateinit var hintDao: SearchHintDao

    /** 主 App 隐私协议 SP。 */
    private lateinit var privacyStore: PrivacyAgreementStore

    /** 模拟云端 frequency 配置。 */
    private lateinit var cloudConfigStore: MockCloudConfigStore

    /** 以下三个 TextView 只展示当前测试状态。 */
    private lateinit var privacyStatus: TextView
    private lateinit var frequencyStatus: TextView
    private lateinit var dataPreview: TextView

    /** 初始化控制页并绑定所有测试入口。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hintDao = SearchMockGraph.hintDao(this)
        privacyStore = PrivacyAgreementStore(this)
        cloudConfigStore = MockCloudConfigStore(this)

        privacyStatus = findViewById(R.id.privacy_status)
        frequencyStatus = findViewById(R.id.frequency_status)
        dataPreview = findViewById(R.id.data_preview)

        findViewById<Button>(R.id.privacy_button).setOnClickListener {
            privacyStore.setAccepted(!privacyStore.isAccepted())
            // 隐私变化需要重新协调周期任务，并在允许同步时立即取一次数据。
            sendWidgetCommand(
                WidgetScheduleActions.ACTION_RECONCILE,
                enqueueImmediate = true,
            )
            renderState()
        }
        findViewById<Button>(R.id.frequency_button).setOnClickListener {
            // 在三组合法频率间循环，便于观察 KEEP 与 UPDATE 策略。
            // UPDATE 保留旧任务已经过去的计时时间，不额外创建一次性立即任务。
            val next = when (cloudConfigStore.frequencyMinutes()) {
                60 -> 15
                15 -> 120
                else -> 60
            }
            cloudConfigStore.setFrequencyMinutes(next)
            sendWidgetCommand(WidgetScheduleActions.ACTION_RECONCILE)
            renderState()
        }
        findViewById<Button>(R.id.data_initial_button).setOnClickListener {
            replaceData(MockSearchData.initial)
        }
        findViewById<Button>(R.id.data_changed_button).setOnClickListener {
            replaceData(MockSearchData.changed)
        }
        findViewById<Button>(R.id.data_duplicate_button).setOnClickListener {
            replaceData(MockSearchData.withDuplicateKeyword)
        }
        findViewById<Button>(R.id.data_empty_button).setOnClickListener {
            replaceData(emptyList())
        }
        findViewById<Button>(R.id.open_search_button).setOnClickListener {
            startActivity(Intent(this, SearchActivationActivity::class.java))
        }
        findViewById<Button>(R.id.open_result_button).setOnClickListener {
            startActivity(Intent(this, SearchResultActivity::class.java))
        }
    }

    /** 从其他页面返回时重新读取真实持久状态。 */
    override fun onResume() {
        super.onResume()
        renderState()
    }

    /** 模拟主搜索网络模块把一组新结果整体写入自己的数据库。 */
    private fun replaceData(records: List<SearchHintRecord>) {
        hintDao.replaceAll(records)
        renderState()
    }

    /** 给未导出的调度 Receiver 发送显式应用内命令。 */
    private fun sendWidgetCommand(action: String, enqueueImmediate: Boolean = false) {
        sendBroadcast(
            Intent(action)
                .setClassName(packageName, WidgetScheduleActions.RECEIVER_CLASS)
                .putExtra(WidgetScheduleActions.EXTRA_ENQUEUE_IMMEDIATE, enqueueImmediate),
        )
    }

    /** 展示隐私、频率以及主搜索 DAO 当前查询结果。 */
    private fun renderState() {
        val accepted = privacyStore.isAccepted()
        privacyStatus.text = if (accepted) {
            "隐私协议：已同意，可以在存在组件时同步"
        } else {
            "隐私协议：未同意，组件只显示兜底文案"
        }
        frequencyStatus.text =
            "Mock 云配 frequency：${cloudConfigStore.frequencyMinutes()} min"

        val records = hintDao.queryHints()
        dataPreview.text = if (records.isEmpty()) {
            "数据库当前为空"
        } else {
            records.joinToString(separator = "\n") { record ->
                "${record.sortOrder}. [${record.id}] ${record.keyword}"
            }
        }
    }
}
