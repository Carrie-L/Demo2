package com.carrie.demo.searchmock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.callback.NavCallback
import com.alibaba.android.arouter.launcher.ARouter
import com.carrie.demo.R
import com.carrie.demo.searchmock.data.MockCloudConfigStore
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.PrivacyAgreementStore
import com.carrie.demo.searchmock.data.SearchHintDao
import com.carrie.demo.searchmock.data.SearchHintRecord
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.sync.WidgetScheduleActions
import com.carrie.demo.searchmock.navigation.RoutePath
import com.carrie.demo.searchmock.navigation.SearchNavigationContract

/**
 * Demo 控制台：模拟隐私、云配和搜索模块落库。
 * 这些按钮是验收工具，不属于正式小组件 UI。
 * 同时 Mock 主 App 已有的通用 deeplink 入口：只把 URI/keyword 交给 ARouter。
 * 不判断组件按钮、不推进组件、不维护点击队列，正式项目沿用自己的 LauncherActivity。
 */
@Route(path = RoutePath.MAIN)
class LauncherActivity : Activity() {
    /** 显式记录当前 Intent 是否已消费，不把“有恢复状态”直接当成“这次按钮已处理”。 */
    private var deepLinkConsumed = false

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
        logEntry("onCreate", intent)
        super.onCreate(savedInstanceState)
        deepLinkConsumed = savedInstanceState?.getBoolean(STATE_DEEP_LINK_CONSUMED) ?: false
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
        if (deepLinkConsumed) clearDeepLinkParameters() else consumeDeepLink()
    }

    /** 通用热启动入口必须保存新 Intent，不能读取上一请求的 URI/keyword。 */
    override fun onNewIntent(intent: Intent) {
        logEntry("onNewIntent", intent)
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkConsumed = false
        consumeDeepLink()
    }

    /** 配置恢复不是新的 deeplink 请求；只防止重复导航，与组件轮播无关。 */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DEEP_LINK_CONSUMED, deepLinkConsumed)
        super.onSaveInstanceState(outState)
    }

    /** 普通图标启动没有 URI，不路由；只有通用协议检查，没有按钮到页面的 resolve。 */
    private fun consumeDeepLink() {
        if (intent.action != Intent.ACTION_VIEW) return
        try {
            val uri = intent.data ?: return
            val keyword = intent.getStringExtra(SearchNavigationContract.EXTRA_KEYWORD)
            deepLinkConsumed = true
            clearDeepLinkParameters()
            if (uri.scheme != "demo2" || uri.host != "app" || uri.path.isNullOrBlank()) {
                Log.w(LOG_TAG, "忽略非法 deeplink")
                return
            }
            // 原样交给主 App 路由，不识别 widget、不推导目标、不处理搜索冻结语义。
            // 不跳过宿主拦截器；搜索模块只会收到可选 keyword，不收到按钮类型。
            ARouter.getInstance().build(uri)
                .apply {
                    if (keyword != null) withString(SearchNavigationContract.EXTRA_KEYWORD, keyword)
                }
                .navigation(this, object : NavCallback() {
                    override fun onArrival(postcard: Postcard) {
                        Log.d(LOG_TAG, "路由已交付 path=${postcard.path}")
                    }
                    override fun onLost(postcard: Postcard) {
                        Log.w(LOG_TAG, "未找到路由")
                    }
                    override fun onInterrupt(postcard: Postcard) {
                        Log.w(LOG_TAG, "路由被宿主拦截")
                    }
                })
        } catch (error: Exception) {
            // 捕获 URI/extra 读取与同步路由异常；异步失败由上方回调处理。
            Log.e(LOG_TAG, "处理 deeplink 失败: ${error.javaClass.simpleName}")
        }
    }

    /** 消费后清除 URI/keyword，普通返回或配置重建不再重放旧页面。 */
    private fun clearDeepLinkParameters() {
        try {
            if (intent.action != Intent.ACTION_VIEW) return
            setIntent(Intent(intent).apply {
                removeExtra(SearchNavigationContract.EXTRA_KEYWORD)
                data = null
            })
        } catch (error: Exception) {
            Log.w(LOG_TAG, "清理 deeplink 失败: ${error.javaClass.simpleName}")
        }
    }

    /** 冷热入口均有日志，不输出搜索词或 URI 参数。 */
    private fun logEntry(event: String, incoming: Intent) {
        Log.d(LOG_TAG, "$event pid=${Process.myPid()} task=$taskId " +
            "instance=${System.identityHashCode(this)} flags=0x${incoming.flags.toString(16)}")
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

    private companion object {
        /** 当前 Activity 保存状态的键，不是跨进程持久化的点击队列。 */
        const val STATE_DEEP_LINK_CONSUMED = "launcher.deep_link_consumed"
        /** 正常入口也有日志，不再依靠只有失败时才出现的路由日志判断点击是否收到。 */
        const val LOG_TAG = "AppDeepLink"
    }
}
