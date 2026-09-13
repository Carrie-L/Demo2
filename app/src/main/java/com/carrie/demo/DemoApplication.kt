package com.carrie.demo

import android.app.Application
import android.content.pm.ApplicationInfo
import com.alibaba.android.arouter.launcher.ARouter
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.sync.WidgetHintSyncScheduler
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer
import com.carrie.demo.searchtoolswidget.router.WidgetLaunchTracker

/** Demo 主进程的全局初始化入口。 */
class DemoApplication : Application() {
    /**
     * 初始化 ARouter、MMKV 和 Mock 数据库，并核对已有 WorkManager 周期任务。
     * 冷启动传 false，不额外发起立即同步，避免每次打开 App 都读取一次数据库。
     */
    override fun onCreate() {
        super.onCreate()
        // 观察所有入口的页面生命周期；不要在 Application 初始化结束就直接标成“热启动”。
        WidgetLaunchTracker.install(this)
        initializeRouter()
        WidgetStorageInitializer.initialize(this)
        val dao = SearchMockGraph.hintDao(this)
        MockSearchData.ensureSeeded(this, dao)
        WidgetHintSyncScheduler(this).reconcile(enqueueImmediate = false)
    }

    /** Debug 包打开 ARouter 日志/调试开关，之后初始化全局路由表。 */
    private fun initializeRouter() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            ARouter.openLog()
            ARouter.openDebug()
        }
        ARouter.init(this)
    }
}
