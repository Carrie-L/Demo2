package com.carrie.demo.searchmock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.carrie.demo.R
import com.carrie.demo.searchmock.data.MockCloudConfigStore
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.PrivacyAgreementStore
import com.carrie.demo.searchmock.data.SearchHintDao
import com.carrie.demo.searchmock.data.SearchHintRecord
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.sync.WidgetScheduleActions

class MainActivity : Activity() {
    private lateinit var hintDao: SearchHintDao
    private lateinit var privacyStore: PrivacyAgreementStore
    private lateinit var cloudConfigStore: MockCloudConfigStore
    private lateinit var privacyStatus: TextView
    private lateinit var frequencyStatus: TextView
    private lateinit var dataPreview: TextView

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
            sendWidgetCommand(
                WidgetScheduleActions.ACTION_RECONCILE,
                enqueueImmediate = true,
            )
            renderState()
        }
        findViewById<Button>(R.id.frequency_button).setOnClickListener {
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
        findViewById<Button>(R.id.sync_now_button).setOnClickListener {
            sendWidgetCommand(WidgetScheduleActions.ACTION_SYNC_NOW)
        }
        findViewById<Button>(R.id.open_search_button).setOnClickListener {
            startActivity(Intent(this, SearchActivationActivity::class.java))
        }
        findViewById<Button>(R.id.open_result_button).setOnClickListener {
            startActivity(Intent(this, SearchResultActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun replaceData(records: List<SearchHintRecord>) {
        hintDao.replaceAll(records)
        renderState()
    }

    private fun sendWidgetCommand(action: String, enqueueImmediate: Boolean = false) {
        sendBroadcast(
            Intent(action)
                .setClassName(packageName, WidgetScheduleActions.RECEIVER_CLASS)
                .putExtra(WidgetScheduleActions.EXTRA_ENQUEUE_IMMEDIATE, enqueueImmediate),
        )
    }

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
