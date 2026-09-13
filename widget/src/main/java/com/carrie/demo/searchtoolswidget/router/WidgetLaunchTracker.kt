package com.carrie.demo.searchtoolswidget.router

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 判断“当前 App 进程是否已经创建过页面”，只用于中转窗口外观，不改变任何业务门槛。
 *
 * 不能只统计 Router：用户先开主页面再点组件，也应是透明中转。标记不持久化，进程重建
 * 自然回到首次 UI；Worker/Provider 启动过进程但没有页面时，首次进 UI 仍采用 Logo 外观。
 * 这不是系统 cold/warm/hot 的完整分类，尤其不能用它控制 onCreate 之前的系统启动预览。
 */
object WidgetLaunchTracker : Application.ActivityLifecycleCallbacks {
    /** 主线程内使用，不持有 Activity，不需要 MMKV、锁或轮询。 */
    var hasCreatedActivity: Boolean = false
        private set

    /** 防止宿主 Application 重复注册同一个观察者。 */
    private var installed = false

    /** 在 Application.onCreate 的初始化前安装；注册本身不执行 I/O。 */
    fun install(application: Application) {
        if (installed) return
        application.registerActivityLifecycleCallbacks(this)
        installed = true
    }

    /** 框架在 Activity.super.onCreate 内分发此回调；Router 必须在 super 之前读取标记。 */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        hasCreatedActivity = true
    }

    /** 以下事件不复位标记：所有页面都关闭、回桌面或锁屏，不等于进程已经死亡。 */
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
