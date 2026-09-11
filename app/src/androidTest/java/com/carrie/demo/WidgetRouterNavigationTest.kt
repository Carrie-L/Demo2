package com.carrie.demo

import android.app.Activity
import android.app.ActivityOptions
import android.app.Instrumentation
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.callback.InterceptorCallback
import com.alibaba.android.arouter.facade.service.InterceptorService
import com.carrie.demo.searchmock.ui.MainActivity
import com.carrie.demo.searchmock.ui.SearchActivationActivity
import com.carrie.demo.searchmock.ui.SearchResultActivity
import com.carrie.demo.searchmock.ui.shortcut.FavoritesActivity
import com.carrie.demo.searchmock.ui.shortcut.WeatherActivity
import com.carrie.demo.searchtoolswidget.router.RoutePath
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetClickContract
import com.carrie.demo.searchtoolswidget.router.WidgetNavigationContract
import com.carrie.demo.searchtoolswidget.router.WidgetPendingIntents
import com.carrie.demo.searchtoolswidget.router.WidgetRouterActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 用真实 PendingIntent → 中转 Activity → ARouter → 目标 Activity 验证连续点击。
 *
 * 这里只测试页面跳转，不需要往用户桌面添加测试组件。虚拟组件 id 不参与推进范围，
 * 当前设备若没有组件，advanceAll 自然为空操作。
 */
@RunWith(AndroidJUnit4::class)
class WidgetRouterNavigationTest {
    /** 仪器线程负责等待，Activity 和生命周期断言交回主线程。 */
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var main: ActivityScenario<MainActivity>

    @Before
    fun openAppTask() {
        // 测试发送方模拟 Launcher 的授权启动能力；生产小组件由可见 Launcher 发送点击。
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            "android.permission.START_ACTIVITIES_FROM_BACKGROUND",
        )
        main = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeTestPages() {
        // 清理本测试打开的页面，不留下前一个测试的 Activity 干扰下一个路由断言。
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED, Stage.STOPPED)
                .flatMap { registry.getActivitiesInStage(it).toList() }
                .distinct()
                .filter { it !is MainActivity }
                .forEach(Activity::finish)
        }
        if (::main.isInitialized) main.close()
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun nextToolClickAfterHomeOpensItsOwnPage() {
        awaitPage(FavoritesActivity::class.java) { send(WidgetAction.FAVORITES) }
        pressHome()
        val weather = awaitPage(WeatherActivity::class.java) { send(WidgetAction.WEATHER) }
        assertResumed(weather)
    }

    @Test
    fun updatedWidgetDoesNotReuseLegacyTokenWithOldLaunchFlags() {
        // 模拟升级前系统仍缓存着旧入口（action=null、没有 Activity 启动 flags）。
        val action = WidgetAction.FAVORITES
        val legacyIntent = Intent(context, WidgetRouterActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID)
            putExtra(WidgetClickContract.EXTRA_ACTION, action.name)
            data = Uri.parse("demo2://widget/action/$TEST_WIDGET_ID/${action.name}")
        }
        val legacy = PendingIntent.getActivity(
            context,
            TEST_WIDGET_ID * 10 + action.ordinal,
            legacyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            val updated = WidgetPendingIntents.action(context, TEST_WIDGET_ID, action)
            assertNotEquals("必须换新身份，UPDATE_CURRENT 不能刷新旧 token 的 flags", legacy, updated)
            val router = awaitPage(WidgetRouterActivity::class.java) { send(action) }
            assertTrue(router.intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
            assertTrue(router.intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
        } finally {
            legacy.cancel()
        }
    }

    @Test
    fun repeatedSearchClickUsesTheNewKeyword() {
        awaitPage(SearchResultActivity::class.java) { send(WidgetAction.SEARCH_SUBMIT, "旧暗词") }
        pressHome()
        val result = awaitPage(SearchResultActivity::class.java) {
            send(WidgetAction.SEARCH_SUBMIT, "本次新暗词")
        }
        instrumentation.runOnMainSync {
            assertEquals(
                "本次新暗词",
                result.intent.getStringExtra(WidgetNavigationContract.EXTRA_KEYWORD),
            )
            assertTrue(result.findViewById<TextView>(R.id.result_keyword).text.contains("本次新暗词"))
        }
        assertResumed(result)
    }

    /** 生产搜索入口使用可变集合模板；同一个 token 每次必须取得 item 自带的新 action/词。 */
    @Test
    fun collectionFillInIntentUsesCurrentActionAndKeyword() {
        val template = WidgetPendingIntents.collectionTemplate(context, TEST_WIDGET_ID)
        fun click(action: WidgetAction, keyword: String) {
            sendPending(template, Intent().apply {
                putExtra(WidgetClickContract.EXTRA_ACTION, action.name)
                putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
            })
        }
        val activation = awaitPage(SearchActivationActivity::class.java) {
            click(WidgetAction.SEARCH_ACTIVATE, "集合当前暗词")
        }
        instrumentation.runOnMainSync {
            assertEquals("集合当前暗词", activation.findViewById<TextView>(R.id.search_input).text.toString())
            assertTrue(activation.intent.getBooleanExtra(
                WidgetNavigationContract.EXTRA_FREEZE_HINT_ROTATION, false,
            ))
        }
        pressHome()
        val result = awaitPage(SearchResultActivity::class.java) {
            click(WidgetAction.SEARCH_SUBMIT, "集合下一个暗词")
        }
        instrumentation.runOnMainSync {
            assertEquals("集合下一个暗词", result.intent.getStringExtra(WidgetNavigationContract.EXTRA_KEYWORD))
        }
        assertResumed(result)
    }

    /** 异步等待中旋转/配置重建不是新点击，不得重复推进或再提交同一条 ARouter 请求。 */
    @Test
    fun recreatingRouterDuringInterceptionDoesNotReplayClick() {
        val field = Class.forName("com.alibaba.android.arouter.launcher._ARouter")
            .getDeclaredField("interceptorService").apply { isAccessible = true }
        val previous = field.get(null)
        val delayed = DelayedFirstRoute()
        field.set(null, delayed)
        try {
            val original = awaitPage(WidgetRouterActivity::class.java) { send(WidgetAction.FAVORITES) }
            assertTrue(delayed.entered.await(5, TimeUnit.SECONDS))
            val recreated = awaitPage(WidgetRouterActivity::class.java) {
                instrumentation.runOnMainSync { original.recreate() }
            }
            assertNotSame(original, recreated)
            instrumentation.runOnMainSync { assertFalse(recreated.isFinishing) }
            assertEquals("配置恢复不得把同一次点击再发一遍", 1, delayed.favoritesCalls.get())
            val favorites = awaitPage(FavoritesActivity::class.java) { delayed.release() }
            assertResumed(favorites)
            assertEquals(1, delayed.favoritesCalls.get())
        } finally {
            delayed.release()
            instrumentation.waitForIdleSync()
            field.set(null, previous)
        }
    }

    @Test
    fun newIntentDuringDelayedRouteKeepsLatestDestination() {
        // 测试专用替身仅替换 ARouter 1.5.2 的内存拦截服务，finally 恢复。
        // 不增加正式拦截器，不改发布包配置，也不让登录/隐私校验在生产中被绕过。
        val field = Class.forName("com.alibaba.android.arouter.launcher._ARouter")
            .getDeclaredField("interceptorService").apply { isAccessible = true }
        val previous = field.get(null)
        val delayed = DelayedFirstRoute()
        field.set(null, delayed)
        try {
            val router = awaitPage(WidgetRouterActivity::class.java) { send(WidgetAction.FAVORITES) }
            assertTrue("收藏路由应已进入可控制的异步拦截器", delayed.entered.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertFalse("目标尚未启动时不能提前 finish 中转页", router.isFinishing)
            }

            pressHome()
            send(WidgetAction.WEATHER)
            awaitNewAction(router, WidgetAction.WEATHER)

            val weather = awaitPage(WeatherActivity::class.java) { delayed.release() }
            assertResumed(weather)
        } finally {
            delayed.release()
            instrumentation.waitForIdleSync()
            field.set(null, previous)
        }
    }

    /** 等待中转页被复用并保存了第二次点击；证明测试实际经过 onNewIntent 分支。 */
    private fun awaitNewAction(router: WidgetRouterActivity, action: WidgetAction) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        var latestAction: String? = null
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                latestAction = router.intent.getStringExtra(WidgetClickContract.EXTRA_ACTION)
            }
            if (latestAction == action.name) return
            android.os.SystemClock.sleep(20)
        }
        assertEquals("复用入口必须保存本次 Intent，不能继续使用上一个按钮", action.name, latestAction)
    }

    /** 发送项目真正生成的 PendingIntent，覆盖系统 PendingIntent 身份/extra 合并逻辑。 */
    private fun send(action: WidgetAction, keyword: String? = null) {
        val pendingIntent = WidgetPendingIntents.action(context, TEST_WIDGET_ID, action, keyword)
        sendPending(pendingIntent)
    }

    /** 模拟可见 Launcher 发送静态 PendingIntent 或模板 + 本次 Fill-in Intent。 */
    @Suppress("DEPRECATION") // API 34/35 的测试设备仍使用此后台启动授权常量。
    private fun sendPending(pendingIntent: PendingIntent, fillIn: Intent? = null) {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }
        pendingIntent.send(context, 0, fillIn, null, null, null, options.toBundle())
    }

    /** 模拟用户快速按 Home 后点下一按钮；不使用 Back，避免把上一个页面从任务中删掉。 */
    private fun pressHome() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        instrumentation.waitForIdleSync()
    }

    /** ActivityMonitor 在创建阶段就会返回，随后等待主线程空闲再断言可见页面。 */
    private fun <T : Activity> awaitPage(type: Class<T>, launch: () -> Unit): T {
        val monitor: Instrumentation.ActivityMonitor = instrumentation.addMonitor(type.name, null, false)
        try {
            launch()
            val page = monitor.waitForActivityWithTimeout(5_000)
            assertNotNull("应启动 ${type.simpleName}", page)
            instrumentation.waitForIdleSync()
            return type.cast(page)!!
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** 最后停在本次选择的目标，不能被上一次异步路由覆盖。 */
    private fun assertResumed(expected: Activity) {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            assertSame(expected, resumed.single())
        }
    }

    /** 收藏路由等待测试手动放行，其余路由立即通过，以稳定复现异步路由等待窗口。 */
    private class DelayedFirstRoute : InterceptorService {
        val entered = CountDownLatch(1)
        /** 主线程/测试线程共同观察提交次数，证明恢复期间没有重复发起请求。 */
        val favoritesCalls = AtomicInteger()
        private var pending: Pair<Postcard, InterceptorCallback>? = null

        override fun init(context: Context) = Unit

        @Synchronized
        override fun doInterceptions(postcard: Postcard, callback: InterceptorCallback) {
            if (postcard.path == RoutePath.FAVORITES) favoritesCalls.incrementAndGet()
            if (postcard.path == RoutePath.FAVORITES && entered.count > 0) {
                pending = postcard to callback
                entered.countDown()
            } else {
                callback.onContinue(postcard)
            }
        }

        /** 即使 finally 再次调用也只放行一次。 */
        @Synchronized
        fun release() {
            val request = pending ?: return
            pending = null
            request.second.onContinue(request.first)
        }
    }

    private companion object {
        /** 用于区分测试 PendingIntent；不是实际系统绑定的组件 id。 */
        const val TEST_WIDGET_ID = 9_101
    }
}
