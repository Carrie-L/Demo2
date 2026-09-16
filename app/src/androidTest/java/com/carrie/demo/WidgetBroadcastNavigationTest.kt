package com.carrie.demo

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.carrie.demo.searchmock.ui.SearchActivationActivity
import com.carrie.demo.searchmock.ui.SearchResultActivity
import com.carrie.demo.searchmock.ui.shortcut.FavoritesActivity
import com.carrie.demo.searchmock.ui.shortcut.HistoryActivity
import com.carrie.demo.searchmock.ui.shortcut.WeatherActivity
import com.carrie.demo.searchmock.ui.shortcut.SettingsActivity
import com.carrie.demo.searchtoolswidget.provider.SearchToolsWidgetProvider
import com.carrie.demo.searchtoolswidget.provider.WidgetBroadcasts
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetTaskLauncher
import com.carrie.demo.searchtoolswidget.router.WidgetDirectEntryIntents
import com.carrie.demo.searchtoolswidget.router.WidgetPendingIntents
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 覆盖生产代码的广播/URI/keyword 契约，而不是继续验证已删除的 action/队列方案。
 * 直接发送 PendingIntent 只验证已交付的点击；不能替代荣耀 Launcher 触摸和冷进程验收。
 */
class WidgetBroadcastNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** 仅给测试发送方启动能力；绝不能把此权限或测试结论作为真实宿主启动保证。 */
    @Before
    fun allowTestSender() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            "android.permission.START_ACTIVITIES_FROM_BACKGROUND",
        )
    }

    @After
    fun cleanUp() {
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED, Stage.STOPPED)
                .flatMap { registry.getActivitiesInStage(it).toList() }.distinct()
                .forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    /** 默认 A 是广播；B 的静态凭据另行检查，避免两个方案接反。 */
    @Test
    fun buttonsSendBroadcastInsteadOfOpeningActivity() {
        assumeTrue(Build.VERSION.SDK_INT >= 31) // PendingIntent.isBroadcast 在 API 31 才公开。
        WidgetAction.entries.forEach {
            val click = WidgetPendingIntents.action(context, it)
            assertNotNull(click)
            assertTrue("点击必须发送 widget 广播", click?.isBroadcast == true)
        }
    }

    /** 接收器真实执行，只替代系统 startActivity 边界来检查实际发出去的协议。 */
    @Test
    fun providerForwardsRealUriAndOriginalKeywordWithNoWidgetExtras() {
        val capture = LaunchCapture(context)
        SearchToolsWidgetProvider().onReceive(capture, Intent("HINT_WORD_NEXT").apply {
            data = Uri.parse("demo2://app/search/activation")
            putExtra("keyword", "点击时的词")
        })
        val launch = capture.launches.single()
        assertEquals("demo2://app/search/activation", launch.dataString)
        assertEquals("点击时的词", launch.getStringExtra("keyword"))
        assertEquals(setOf("keyword"), launch.extras?.keySet())
        assertNull(launch.action)
        assertNull("只能按真实 dp 匹配，不能强行指定 MAIN 入口", launch.component)
        assertEquals(context.packageName, launch.`package`)
        assertTrue(launch.categories.isNullOrEmpty())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, launch.flags) // 本用例无活动任务，拦截冷入口。
    }

    @Test
    fun toolRoutesDiscardKeywordAndEachRequestKeepsItsOwnUri() {
        val capture = LaunchCapture(context)
        listOf("favorites", "weather", "history", "settings").forEach { page ->
            WidgetTaskLauncher.open(capture, Uri.parse("demo2://app/tools/$page"), "不应传给工具")
        }
        assertEquals(listOf(
            "demo2://app/tools/favorites", "demo2://app/tools/weather",
            "demo2://app/tools/history", "demo2://app/tools/settings",
        ), capture.launches.map { it.dataString })
        capture.launches.forEach { assertFalse(it.hasExtra("keyword")) }
    }

    @Test
    fun advanceOnlyBroadcastDoesNotOpenAnActivity() {
        val capture = LaunchCapture(context)
        SearchToolsWidgetProvider().onReceive(capture, Intent(WidgetBroadcasts.HINT_WORD_NEXT))
        assertTrue(capture.launches.isEmpty())
    }

    @Test
    fun unknownUriDoesNotTurnProviderIntoAnArbitraryLauncher() {
        val capture = LaunchCapture(context)
        WidgetTaskLauncher.open(capture, Uri.parse("https://example.com/"), null)
        WidgetTaskLauncher.open(capture, Uri.parse("demo2://app/unknown/page"), null)
        assertTrue(capture.launches.isEmpty())
    }

    @Test
    fun taskQueryExceptionIsContained() {
        val rejected = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? {
                throw SecurityException("test rejection")
            }
        }
        WidgetTaskLauncher.open(rejected, Uri.parse("demo2://app/tools/favorites"), null)
    }

    /** A -> B -> A，直接验证重复首个入口仍创建落地页，且没有每次新建 App 任务。 */
    @Test
    fun appTaskRepeatedClicksStayInTheExistingTask() {
        var firstTaskId: Int? = null
        listOf(
            WidgetAction.FAVORITES to FavoritesActivity::class.java,
            WidgetAction.WEATHER to WeatherActivity::class.java,
            WidgetAction.FAVORITES to FavoritesActivity::class.java,
            WidgetAction.HISTORY to HistoryActivity::class.java,
            WidgetAction.FAVORITES to FavoritesActivity::class.java,
        ).forEach { (action, destination) ->
            val page = awaitPage(destination) {
                WidgetPendingIntents.action(context, action)
                    ?.send(context, 0, null, null, null, null, senderOptions())
            }
            if (firstTaskId == null) firstTaskId = page.taskId
            assertEquals("AppTask 方案不能每次新建任务", firstTaskId, page.taskId)
        }
    }

    /** 不切换生产常量，直接调用方案 B 的真实凭据工厂，覆盖所有静态按钮。 */
    @Test
    fun directActivityButtonsNavigateToTheirOwnPages() {
        val cases = listOf(
            WidgetAction.FAVORITES to FavoritesActivity::class.java,
            WidgetAction.HISTORY to HistoryActivity::class.java,
            WidgetAction.WEATHER to WeatherActivity::class.java,
            WidgetAction.SETTINGS to SettingsActivity::class.java,
            WidgetAction.SEARCH_ACTIVATE to SearchActivationActivity::class.java,
            WidgetAction.SEARCH_SUBMIT to SearchResultActivity::class.java,
        )
        cases.forEach { (action, destination) ->
            val page = awaitPage(destination) {
                val click = WidgetDirectEntryIntents.action(context, action, "直接入口暗词")
                assertNotNull(click)
                assertTrue(click?.isActivity == true)
                click?.send(context, 0, null, null, null, null, senderOptions())
            }
            if (action.acceptsKeyword) assertEquals("直接入口暗词", page.intent.getStringExtra("keyword"))
            else assertNull(page.intent.getStringExtra("keyword"))
            assertFalse(page.intent.hasExtra(WidgetDirectEntryIntents.EXTRA_FROM_WIDGET))
        }
    }

    /** 方案 B 的模板也是 data=null，连续 fill-in 必须分别到激活页和结果页。 */
    @Test
    fun directActivityTemplateUsesEachItemsUriAndKeyword() {
        val template = WidgetDirectEntryIntents.collectionTemplate(context)
        assertNotNull(template)
        listOf(
            Triple(WidgetAction.SEARCH_ACTIVATE, SearchActivationActivity::class.java, "直接第一词"),
            Triple(WidgetAction.SEARCH_SUBMIT, SearchResultActivity::class.java, "直接第二词"),
            Triple(WidgetAction.SEARCH_ACTIVATE, SearchActivationActivity::class.java, "直接第三词"),
        ).forEach { (action, destination, word) ->
            val page = awaitPage(destination) {
                template?.send(context, 0, WidgetPendingIntents.fillIn(action, word),
                    null, null, null, senderOptions())
            }
            assertEquals(word, page.intent.getStringExtra("keyword"))
        }
    }

    /** 六个入口经过真实 PendingIntent、Provider、Launcher 和 ARouter，而非直接调用目标页。 */
    @Test
    fun allDeliveredButtonsNavigateToTheirOwnPages() {
        val cases = listOf(
            WidgetAction.SEARCH_ACTIVATE to SearchActivationActivity::class.java,
            WidgetAction.SEARCH_SUBMIT to SearchResultActivity::class.java,
            WidgetAction.FAVORITES to FavoritesActivity::class.java,
            WidgetAction.HISTORY to HistoryActivity::class.java,
            WidgetAction.WEATHER to WeatherActivity::class.java,
            WidgetAction.SETTINGS to SettingsActivity::class.java,
        )
        cases.forEach { (action, destination) ->
            val page = awaitPage(destination) {
                val pending = WidgetPendingIntents.action(context, action, "当前暗词")
                assertNotNull(pending)
                pending?.send(context, 0, null, null, null, null, senderOptions())
            }
            if (action.acceptsKeyword) assertEquals("当前暗词", page.intent.getStringExtra("keyword"))
            else assertNull(page.intent.getStringExtra("keyword"))
        }
    }

    /** 模板不能占用 data，也不能把上次 Fill-in 的 keyword 带到下一次点击。 */
    @Test
    fun collectionTemplateDeliversFreshUriAndKeywordEveryTime() {
        val template = WidgetPendingIntents.collectionTemplate(context)
        assertNotNull(template)
        val cases = listOf(
            Triple(WidgetAction.SEARCH_ACTIVATE, SearchActivationActivity::class.java, "第一条"),
            Triple(WidgetAction.SEARCH_SUBMIT, SearchResultActivity::class.java, "第二条"),
            Triple(WidgetAction.SEARCH_ACTIVATE, SearchActivationActivity::class.java, "第三条"),
        )
        cases.forEach { (action, destination, word) ->
            val page = awaitPage(destination) {
                template?.send(context, 0, WidgetPendingIntents.fillIn(action, word),
                    null, null, null, senderOptions())
            }
            assertEquals(word, page.intent.getStringExtra("keyword"))
            if (page is SearchActivationActivity) {
                instrumentation.runOnMainSync {
                    assertEquals(word, page.findViewById<EditText>(R.id.search_input).text.toString())
                    val status = page.findViewById<android.widget.TextView>(R.id.rotation_status)
                    assertTrue(status.text.contains("冻结"))
                }
            }
        }
    }

    /** 启动权限只属于这个测试 sender，不修改生产 PendingIntent。 */
    @Suppress("DEPRECATION")
    private fun senderOptions() = ActivityOptions.makeBasic().apply {
        if (Build.VERSION.SDK_INT >= 34) {
            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
    }.toBundle()

    private fun awaitPage(type: Class<out Activity>, click: () -> Unit): Activity {
        val monitor = instrumentation.addMonitor(type.name, null, false)
        try {
            click()
            val page = monitor.waitForActivityWithTimeout(5_000)
            assertNotNull("应打开 ${type.simpleName}", page)
            instrumentation.waitForIdleSync()
            return page ?: throw AssertionError("没有收到目标页面")
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** 测试 double 仅拦截系统启动边界；URI/flags 构建和 Provider 分支均用真实实现。 */
    private class LaunchCapture(base: Context) : ContextWrapper(base) {
        val launches = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) { launches += Intent(intent) }
    }
}
