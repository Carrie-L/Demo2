package com.carrie.demo.searchtoolswidget.router

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.alibaba.android.arouter.launcher.ARouter
import com.carrie.demo.searchtoolswidget.provider.WidgetInstanceUpdater
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

/**
 * 小组件点击的轻量中转页。
 *
 * RemoteViews 无法直接执行项目内的 ARouter 调用，因此所有按钮先进入本 Activity：
 * 先让被点击实例的暗词立即前进，再解析固定 RoutePath 跳进主 App 页面。
 */
class WidgetRouterActivity : Activity() {
    /** 处理一次点击后立即结束，不进入最近任务列表，也不保留页面。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetStorageInitializer.initialize(this)

        // action 可能来自系统恢复的旧 PendingIntent，解析失败时必须安全结束。
        val action = intent.getStringExtra(WidgetClickContract.EXTRA_ACTION)
            ?.let { value -> runCatching { WidgetAction.valueOf(value) }.getOrNull() }
        if (action == null) {
            finish()
            return
        }

        // appWidgetId 只控制“推进哪个实例”，不参与页面路由。工具按钮之间在推进语义上
        // 没有任何区别，区别只在下面解析出的 RoutePath。
        WidgetInstanceUpdater.advanceOne(
            context = this,
            appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
        val route = WidgetRoute.resolve(
            action = action,
            keyword = intent.getStringExtra(WidgetClickContract.EXTRA_KEYWORD),
        )

        // RoutePath 是主 App 与 widget 模块之间的正式路由协议；迁移时替换为宿主项目常量。
        ARouter.getInstance()
            .build(route.targetRoutePath)
            .withString(WidgetNavigationContract.EXTRA_KEYWORD, route.keyword)
            .withBoolean(
                WidgetNavigationContract.EXTRA_FREEZE_HINT_ROTATION,
                route.freezeHintRotation,
            )
            .withFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .navigation(this)
        finish()
    }
}
