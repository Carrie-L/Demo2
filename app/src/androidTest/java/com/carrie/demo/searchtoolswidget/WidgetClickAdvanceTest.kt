package com.carrie.demo.searchtoolswidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import android.view.View
import android.widget.AdapterViewFlipper
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.carrie.demo.searchtoolswidget.provider.SearchToolsWidgetProvider
import com.carrie.demo.searchtoolswidget.provider.WidgetInstanceUpdater
import com.carrie.demo.searchtoolswidget.provider.WidgetRemoteViewsRenderer
import com.carrie.demo.searchtoolswidget.storage.MmkvWidgetStateStore
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
            instrumentation.runOnMainSync { WidgetInstanceUpdater.advanceAll(context) }
            awaitIndices((click + 1) % 4, (click + 3) % 4)
            instrumentation.runOnMainSync {
                views.forEachIndexed { index, view ->
                    assertSame("完整更新应复用静态工具 View，不重建底部按钮",
                        originalTools[index], view.findViewById(R.id.tool_favorites))
                }
            }
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

    /** 合并后的正式清单中，组件、服务、中转页均不得落入独立进程。 */
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
            context, "com.carrie.demo.searchtoolswidget.router.WidgetRouterActivity",
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
