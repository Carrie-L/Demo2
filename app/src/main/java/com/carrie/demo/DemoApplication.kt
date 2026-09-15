package com.carrie.demo

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.alibaba.android.arouter.launcher.ARouter
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.sync.WidgetHintSyncScheduler
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

/** Demo 主进程的全局初始化入口。 */
class DemoApplication : Application() {
    /**
     * 初始化 ARouter、MMKV 和 Mock 数据库，并核对已有 WorkManager 周期任务。
     * 冷启动传 false，不额外发起立即同步，避免每次打开 App 都读取一次数据库。
     */
    override fun onCreate() {
        super.onCreate()
        initializeRouter()
        try {
            WidgetStorageInitializer.initialize(this)
            val dao = SearchMockGraph.hintDao(this)
            MockSearchData.ensureSeeded(this, dao)
            WidgetHintSyncScheduler(this).reconcile(enqueueImmediate = false)
        } catch (error: Exception) {
            // Mock 启动接入的失败不能让进程在 Provider 收到点击之前就崩溃。
            // MMKV/数据库失败仍记录为失败，不假报同步成功；下一次入口会重试初始化。
            Log.e("DemoApplication", "初始化数据/组件调度失败: ${error.javaClass.simpleName}")
        }
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
