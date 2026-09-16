package com.carrie.demo.searchtoolswidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.app.ActivityOptions
import android.os.Build
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.AdapterViewFlipper
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.provider.SearchToolsWidgetProvider
import com.carrie.demo.searchtoolswidget.provider.WidgetBroadcasts
import com.carrie.demo.searchtoolswidget.provider.WidgetInstanceUpdater
import com.carrie.demo.searchtoolswidget.provider.WidgetRemoteViewsRenderer
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
import com.carrie.demo.searchtoolswidget.router.WidgetAction
import com.carrie.demo.searchtoolswidget.router.WidgetPendingIntents
import com.carrie.demo.searchtoolswidget.router.WidgetDirectEntryIntents
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实系统 AppWidgetService + AppWidgetHost 回归，不 mock RemoteViews 的合并规则。
 * 在专用模拟器运行前授权：adb shell appwidget grantbind --package com.carrie.demo --user 0。
 * 测试宿主不附着窗口，因此没有 8 秒自动轮播干扰，可以断言每一次点击的准确增量。
 */
@RunWith(AndroidJUnit4::class)
class WidgetClickAdvanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val store get() = MmkvWidgetStateStore.get()
    private lateinit var host: AppWidgetHost
    private val ids = mutableListOf<Int>()
    private val views = mutableListOf<AppWidgetHostView>()
    private var previousPool = emptyList<WidgetHintRecord>()
    private var previousPrivacy = false

    /** 两个真实绑定实例模拟桌面与负一屏；只删除测试自己分配的 id。 */
    @Before
    fun setUp() {
        previousPool = store.readPool()
        previousPrivacy = store.isPrivacyAllowed()
        store.setPrivacyAllowed(false)
        instrumentation.runOnMainSync {
            host = AppWidgetHost(context, TEST_HOST_ID)
            host.startListening()
            repeat(2) {
                val id = host.allocateAppWidgetId()
                ids += id
                assertTrue("请先用 grantbind 授权专用测试模拟器", manager.bindAppWidgetIdIfAllowed(
                    id, ComponentName(context, SearchToolsWidgetProvider::class.java),
                ))
                views += host.createView(context, id, manager.getAppWidgetInfo(id))
            }
        }
        instrumentation.waitForIdleSync()
        store.replacePool((0..3).map { WidgetHintRecord(it.toLong(), "测试暗词$it", it) })
        store.setPrivacyAllowed(true)
        instrumentation.runOnMainSync {
            WidgetRemoteViewsRenderer.render(context, manager, ids.toIntArray(), 0)
        }
        awaitIndices(0, 0)
    }

    /** 恢复测试前快照，避免影响模拟器上已有的手动测试组件。 */
    @After
    fun tearDown() {
        if (::host.isInitialized) {
            instrumentation.runOnMainSync {
                ids.forEach(host::deleteAppWidgetId)
                host.stopListening()
            }
        }
        store.replacePool(previousPool)
        store.setPrivacyAllowed(previousPrivacy)
        WidgetInstanceUpdater.refreshAll(context, 0)
    }

    /** 记录旧实现失败的系统原因：partial merge 丢弃 showNext，一次都不会推进。 */
    @Suppress("DEPRECATION")
    @Test
    fun partialShowNextIsIgnoredBySystemMerge() {
        val command = RemoteViews(context.packageName, R.layout.widget_search_tools).apply {
            showNext(R.id.hint_flipper)
        }
        manager.partiallyUpdateAppWidget(ids.toIntArray(), command)
        SystemClock.sleep(300)
        instrumentation.waitForIdleSync()
        assertArrayEquals(intArrayOf(0, 0), indices())
    }

    /** 不仅检查第一下：不同起点的两个实例都推进，连续点击且跨列表尾部也不能归零卡住。 */
    @Test
    fun eachClickAdvancesAllInstancesFromTheirOwnPosition() {
        var originalTools = emptyList<View>()
        instrumentation.runOnMainSync {
            flipper(1).displayedChild = 2
            originalTools = views.map { it.findViewById(R.id.tool_favorites) }
        }
        repeat(6) { click ->
            instrumentation.runOnMainSync {
                SearchToolsWidgetProvider().onReceive(context, Intent(WidgetBroadcasts.HINT_WORD_NEXT))
            }
            awaitIndices((click + 1) % 4, (click + 3) % 4)
            instrumentation.runOnMainSync {
                views.forEachIndexed { index, view ->
                    assertSame("完整更新应复用静态工具 View，不重建底部按钮",
                        originalTools[index], view.findViewById(R.id.tool_favorites))
                }
            }
        }
    }

    /** 覆盖 Provider 广播入口到实际宿主翻页，不仅直接调用 advanceAll 测试 helper。 */
    @Suppress("DEPRECATION")
    @Test
    fun broadcastEntryAdvancesAllWidgetsExactlyOncePerDeliveredClick() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.START_ACTIVITIES_FROM_BACKGROUND")
        try {
            val cases = listOf(
                WidgetAction.FAVORITES to "com.carrie.demo.searchmock.ui.shortcut.FavoritesActivity",
                WidgetAction.WEATHER to "com.carrie.demo.searchmock.ui.shortcut.WeatherActivity",
            )
            cases.forEachIndexed { index, (action, pageClass) ->
                val monitor = instrumentation.addMonitor(pageClass, null, false)
                try {
                    val options = ActivityOptions.makeBasic()
                    if (Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    WidgetPendingIntents.action(context, action)
                        ?.send(context, 0, null, null, null, null, options.toBundle())
                    assertNotNull(monitor.waitForActivityWithTimeout(5_000))
                    awaitIndices(index + 1, index + 1)
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                val registry = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                listOf(androidx.test.runner.lifecycle.Stage.RESUMED, androidx.test.runner.lifecycle.Stage.STARTED,
                    androidx.test.runner.lifecycle.Stage.PAUSED, androidx.test.runner.lifecycle.Stage.STOPPED)
                    .flatMap { registry.getActivitiesInStage(it).toList() }.distinct()
                    .forEach { it.finish() }
            }
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    /** B 经过 Launcher 的来源判断和真实 next 广播，两实例都只推进一次；重建不重发。 */
    @Suppress("DEPRECATION")
    @Test
    fun directEntryAdvancesExactlyOnceAndRecreationDoesNotRepeatIt() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.START_ACTIVITIES_FROM_BACKGROUND")
        val monitor = instrumentation.addMonitor(
            "com.carrie.demo.searchmock.ui.shortcut.FavoritesActivity", null, false,
        )
        try {
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= 34) setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            WidgetDirectEntryIntents.action(context, WidgetAction.FAVORITES)
                ?.send(context, 0, null, null, null, null, options.toBundle())
            assertNotNull(monitor.waitForActivityWithTimeout(5_000))
            awaitIndices(1, 1)
            // 重新创建已消费点击的 Launcher，确保来源标记/保存状态不再次触发广播。
            instrumentation.runOnMainSync {
                val registry = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                listOf(androidx.test.runner.lifecycle.Stage.STOPPED, androidx.test.runner.lifecycle.Stage.PAUSED)
                    .flatMap { registry.getActivitiesInStage(it).toList() }
                    .filterIsInstance<com.carrie.demo.searchmock.ui.LauncherActivity>()
                    .forEach { it.recreate() }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            assertArrayEquals(intArrayOf(1, 1), indices())
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync {
                val registry = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                listOf(androidx.test.runner.lifecycle.Stage.RESUMED, androidx.test.runner.lifecycle.Stage.STARTED,
                    androidx.test.runner.lifecycle.Stage.PAUSED, androidx.test.runner.lifecycle.Stage.STOPPED)
                    .flatMap { registry.getActivitiesInStage(it).toList() }.distinct()
                    .forEach { it.finish() }
            }
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    /** 无隐私许可或空池时，点击仅负责路由，不给隐藏 Flipper 下发前进动作。 */
    @Test
    fun privacyDeniedOrEmptyPoolDoesNotAdvance() {
        store.setPrivacyAllowed(false)
        instrumentation.runOnMainSync { WidgetInstanceUpdater.advanceAll(context) }
        instrumentation.waitForIdleSync()
        assertArrayEquals(intArrayOf(0, 0), indices())
        store.setPrivacyAllowed(true)
        store.clearPool()
        instrumentation.runOnMainSync { WidgetInstanceUpdater.advanceAll(context) }
        instrumentation.waitForIdleSync()
        assertArrayEquals(intArrayOf(0, 0), indices())
    }

    /** 系统 onUpdate 在当前进程已渲染后不能让轮播重头开始。 */
    @Test
    fun systemUpdateAfterClickDoesNotResetProgress() {
        instrumentation.runOnMainSync { WidgetInstanceUpdater.advanceAll(context) }
        awaitIndices(1, 1)
        instrumentation.runOnMainSync {
            SearchToolsWidgetProvider().onUpdate(context, manager, ids.toIntArray())
        }
        instrumentation.waitForIdleSync()
        assertArrayEquals(intArrayOf(1, 1), indices())
    }

    /** 升级后即使系统 UI 已初始化，也必须重绑旧界面及点击；重绑不得推进或归零。 */
    @Test
    fun packageReplacementRebindsWidgetsWithoutChangingProgress() {
        manager.partiallyUpdateAppWidget(ids.toIntArray(), RemoteViews(context.packageName, R.layout.widget_search_tools).apply {
            setOnClickPendingIntent(R.id.tool_favorites, null)
        })
        val deadline = SystemClock.uptimeMillis() + 4_000
        var changed = false
        while (!changed && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                changed = views.all { !it.findViewById<View>(R.id.tool_favorites).hasOnClickListeners() }
            }
            if (!changed) SystemClock.sleep(25)
        }
        assertTrue("先确认宿主显示了旧版快照，避免测试未经过重绑就通过", changed)
        // 在 partial 准备动作结束后建立进度基线，否则它会重放 setUp 快照中的索引 0。
        // 模拟桌面自身已经轮播到第 1 项，不在测试准备阶段引入任何 showNext 缓存。
        instrumentation.runOnMainSync { views.indices.forEach { flipper(it).displayedChild = 1 } }
        awaitIndices(1, 1)
        instrumentation.runOnMainSync {
            SearchToolsWidgetProvider().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        }
        val end = SystemClock.uptimeMillis() + 4_000
        var rebound = false
        while (!rebound && SystemClock.uptimeMillis() < end) {
            instrumentation.runOnMainSync {
                rebound = views.all { it.findViewById<View>(R.id.tool_favorites).hasOnClickListeners() }
            }
            if (!rebound) SystemClock.sleep(25)
        }
        assertTrue("应用升级必须重绑所有现存组件", rebound)
        assertArrayEquals(intArrayOf(1, 1), indices())
    }

    /** 合并后的正式清单中，组件、服务、主 App 入口均不得落入独立进程。 */
    @Test
    fun widgetComponentsUseMainProcess() {
        val pm = context.packageManager
        assertEquals(context.packageName, pm.getReceiverInfo(
            ComponentName(context, SearchToolsWidgetProvider::class.java), 0,
        ).processName)
        assertEquals(context.packageName, pm.getServiceInfo(ComponentName(
            context, "com.carrie.demo.searchtoolswidget.remote.HintRemoteViewsService",
        ), 0).processName)
        assertEquals(context.packageName, pm.getActivityInfo(ComponentName(
            context, "com.carrie.demo.searchmock.ui.LauncherActivity",
        ), 0).processName)
    }

    /** 在主线程读取宿主视图，避免测试跨线程访问 View。 */
    private fun flipper(index: Int): AdapterViewFlipper = views[index].findViewById(R.id.hint_flipper)

    private fun indices(): IntArray {
        var result = intArrayOf()
        instrumentation.runOnMainSync {
            result = views.map {
                it.findViewById<AdapterViewFlipper>(R.id.hint_flipper)?.displayedChild ?: -1
            }.toIntArray()
        }
        return result
    }

    /** 等待 Binder 回调，不用 8 秒轮播碰巧切换来冒充点击成功。 */
    private fun awaitIndices(vararg expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 4_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (indices().contentEquals(expected)) return
            SystemClock.sleep(25)
        }
        assertArrayEquals(expected, indices())
    }

    companion object {
        /** 与 Launcher 的 hostId 隔离，只代表测试宿主身份，不是生产组件隔离策略。 */
        private const val TEST_HOST_ID = 91126
    }
}
