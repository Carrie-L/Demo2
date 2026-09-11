package com.carrie.demo.searchtoolswidget.router

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.callback.NavigationCallback
import com.alibaba.android.arouter.launcher.ARouter
import com.carrie.demo.searchtoolswidget.provider.WidgetInstanceUpdater
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer
import java.lang.ref.WeakReference

/**
 * 小组件点击的轻量中转页。
 *
 * RemoteViews 无法直接执行项目内的 ARouter 调用，因此所有按钮先进入本 Activity：
 * 先让所有组件的暗词立即前进，再解析固定 RoutePath 跳进主 App 页面。
 * 本页在主进程运行，不加载布局或 Logo；路由完成后立即关闭。
 */
class WidgetRouterActivity : Activity() {
    /** 第一次创建入口时读取本次点击；不能在 navigation() 返回后马上 finish。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetStorageInitializer.initialize(this)
        currentEntry = WeakReference(this)
        // 配置重建是在恢复同一次点击，不是用户再次点击；队列仍在主进程中等待原请求。
        // 进程死亡后队列自然为空，也不重放旧点击。onPostResume 会关闭这种空的中转页。
        if (savedInstanceState == null) handleClick(intent)
    }

    /**
     * 入口尚未结束时，singleTop + CLEAR_TOP 会把再次点击送到这里，而不是重新 onCreate。
     * 必须先 setIntent，否则后续通过 Activity.intent 读到的仍然是上一按钮/上一暗词。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentEntry = WeakReference(this)
        handleClick(intent)
    }

    /** 允许系统先交付可能存在的新 Intent，再收掉“仅恢复旧状态、没有待完成路由”的入口。 */
    override fun onPostResume() {
        super.onPostResume()
        if (!navigationQueue.isNavigating) finish()
    }

    /** 配置变化时释放旧窗口引用，不取消已经收到的点击，也不把新窗口引用一起清掉。 */
    override fun onDestroy() {
        if (currentEntry?.get() === this) currentEntry = null
        super.onDestroy()
    }

    /** onCreate/onNewIntent 共用同一套参数解析和推进逻辑，每次有效点击只推进一次。 */
    private fun handleClick(clickIntent: Intent) {
        // action 可能来自系统恢复的旧 PendingIntent，解析失败时必须安全结束。
        val action = clickIntent.getStringExtra(WidgetClickContract.EXTRA_ACTION)
            ?.let { value -> runCatching { WidgetAction.valueOf(value) }.getOrNull() }
        if (action == null) {
            finish()
            return
        }

        // 不再按 appWidgetId 隔离：点击任意按钮，都向所有已添加的实例发送一次 showNext。
        // 搜索入参取点击 item 自带的旧词，因此不会误把推进后的下一个词带入搜索页。
        WidgetInstanceUpdater.advanceAll(this)
        val route = WidgetRoute.resolve(
            action = action,
            keyword = clickIntent.getStringExtra(WidgetClickContract.EXTRA_KEYWORD),
        )
        navigationQueue.submit(route)?.let { navigate(applicationContext, it) }
    }

    /** 入口没有可见内容，也不应再播放一次退出动画，避免 Logo/底层窗口闪一下。 */
    @Suppress("DEPRECATION") // API 29 起统一关闭这次过渡；本页不引入额外兼容依赖。
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private companion object {
        /** 只记录路由路径，不在日志输出用户搜索词。 */
        const val LOG_TAG = "WidgetRouter"

        /** 一条执行中 + 一条最新等待。进程内共享，配置重建不能新建第二条并发导航链。 */
        val navigationQueue = WidgetNavigationQueue()

        /** 仅弱引用当前入口，旧 Activity 销毁后不会被路由回调长期持有。 */
        var currentEntry: WeakReference<WidgetRouterActivity>? = null

        /** 拦截器可能从后台线程回调；队列和窗口操作始终在主线程执行。 */
        val mainHandler = Handler(Looper.getMainLooper())

        /** 提交固定内部路径，保留正式项目的 ARouter 拦截器（例如登录校验）。 */
        fun navigate(appContext: Context, route: WidgetRoute) {
            // RoutePath 是主 App 与 widget 模块之间的正式路由协议；迁移时替换为宿主项目常量。
            ARouter.getInstance()
                .build(route.targetRoutePath)
                .withString(WidgetNavigationContract.EXTRA_KEYWORD, route.keyword)
                .withBoolean(
                    WidgetNavigationContract.EXTRA_FREEZE_HINT_ROTATION,
                    route.freezeHintRotation,
                )
                // 使用 Application Context，避免异步拦截器持有重建前的 Activity 再去启动页面。
                // NEW_TASK + 相同 App affinity 复用主 App 任务，不使用旧的空 affinity 独立任务。
                // 不加 SINGLE_TOP：Demo 目标是 standard，CLEAR_TOP 会重建目标并读取本次参数。
                // 正式目标若声明 singleTop/singleTask，目标自身仍须在 onNewIntent 中刷新入参。
                // withFlags 会覆盖 ARouter 默认 flags，必须显式包含 NEW_TASK。
                // 这里仍受系统后台启动规则约束，不是从后台绕过系统限制主动拉起。
                .withFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                .withTransition(0, 0)
                .navigation(appContext, object : NavigationCallback {
                    /** 找到路由不等于已启动页面；此时拦截器可能还没有执行，不能结束入口。 */
                    override fun onFound(postcard: Postcard) = Unit

                    /** ARouter 已调用 startActivity，才可以结束入口或处理等待中的最新点击。 */
                    override fun onArrival(postcard: Postcard) = onNavigationFinished(appContext)

                    /** 正式项目未注册 RoutePath 时及时结束，不留下透明窗口挡住桌面。 */
                    override fun onLost(postcard: Postcard) {
                        Log.w(LOG_TAG, "未注册组件目标路由：${postcard.path}")
                        onNavigationFinished(appContext)
                    }

                    /** 拦截器拒绝跳转也算一次结束，不绕过拦截器强行进入目标。 */
                    override fun onInterrupt(postcard: Postcard) {
                        Log.i(LOG_TAG, "组件路由被拦截：${postcard.path}")
                        onNavigationFinished(appContext)
                    }
                })
        }

        /** 拦截回调可能来自后台线程，所有 Activity/队列操作统一回主线程。 */
        fun onNavigationFinished(appContext: Context) {
            // 即使 ARouter 同步回调，也推迟到下一个主线程消息，避免在 onCreate 中递归导航。
            mainHandler.post {
                // 必须先取队列再判断 Activity 生命周期。CLEAR_TOP 可以销毁入口，但不能因此
                // 丢掉入口已经接收到的下一次点击，否则快速返回桌面点击仍可能停在旧页。
                val nextRoute = navigationQueue.complete()
                if (nextRoute != null) {
                    navigate(appContext, nextRoute)
                } else {
                    currentEntry?.get()?.let { entry ->
                        if (!entry.isFinishing && !entry.isDestroyed) entry.finish()
                    }
                }
            }
        }
    }
}
