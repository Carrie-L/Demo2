package com.carrie.demo.searchtoolswidget.router

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * 方案 A：点击先到 Provider，推进暗词后在已有 AppTask 中启动 deeplink。
 *
 * 所有逻辑集中在本类：不查 getLaunchIntentForPackage、不指定 component、不依赖主模块。
 * Intent 保持用户真实项目已验证的 uri-only 协议，由 Manifest 找到 deeplink 接收页。
 * 若正式入口要求特定 action/category，在下面构造 Intent 的地方按该协议填写。
 * 新系统若启用了严格的非空 action 匹配，也应填写入口声明的 action，不能靠指定 component 绕过。
 *
 * 适用前提：入口是 standard/singleTop；singleTask/singleInstance 不能这样插入非空任务。
 * 本例用 application 默认 taskAffinity 识别主任务，不选择文档或隐藏任务。
 * 正式项目若有多个普通主任务，应在下面选择任务的循环中明确产品的选择规则。
 * 只处理 widget -> App 的启动；不会修改主 App 分发后另行构造的 Intent。
 */
object WidgetTaskLauncher {
    /** 仅在用户点击时调用；uri=null 是 Launcher 发来的纯推进广播，不能再次打开 App。 */
    fun open(context: Context, uri: Uri?, keyword: String?) {
        if (uri == null) return
        try {
            val destination = WidgetAction.entries.firstOrNull { it.deepLink == uri.toString() }
                ?: return // 沿用组件自己的固定路由表，不向 App 传 WidgetAction。
            val launchIntent = Intent().apply {
                data = uri
                setPackage(context.packageName)
                // 不添加 MAIN/LAUNCHER，也不手动指定一个可能只负责显示首页的 component。
                if (destination.acceptsKeyword && keyword != null) {
                    putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
                }
            }

            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (manager == null) {
                Log.w(LOG_TAG, "无法读取 AppTask，本次不盲目恢复旧任务")
                return
            }
            var targetTask: ActivityManager.AppTask? = null
            for (task in manager.appTasks) {
                try {
                    val info = task.taskInfo ?: continue
                    val base = info.baseActivity ?: continue
                    if (base.packageName != context.packageName) continue
                    val flags = info.baseIntent.flags
                    if (flags and (Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0) continue
                    // 这里只读取已有任务根的 affinity，不寻找或指定 deeplink 的目标 Activity。
                    val affinity = context.packageManager.getActivityInfo(base, 0).taskAffinity
                    if (affinity != context.applicationInfo.taskAffinity) continue
                    targetTask = task
                    break
                } catch (error: Exception) {
                    // 读列表后任务可能已被移除，或旧版本 Activity 已不存在，继续检查其他任务。
                    Log.w(LOG_TAG, "跳过不可用任务: ${error.javaClass.simpleName}")
                }
            }

            val task = targetTask
            if (task != null) {
                try {
                    // 核心：由 AppTask 指定入栈位置，不经过普通 Context + NEW_TASK 的任务搜索。
                    // 不加 NEW_TASK / CLEAR_TOP / SINGLE_TOP / NEW_DOCUMENT。
                    // Manifest 自带的 singleTop 仍有效：入口恰在栈顶时由 onNewIntent 接收。
                    Log.d(LOG_TAG, "AppTask 内启动 path=${uri.path}")
                    task.startActivity(context, launchIntent, null)
                    return
                } catch (error: IllegalArgumentException) {
                    val taskStillExists = try { task.taskInfo != null } catch (_: Exception) { false }
                    if (taskStillExists) {
                        // 例如入口是 singleTask：记录不兼容，不退回已知可能恢复旧页的启动方式。
                        Log.e(LOG_TAG, "指定任务启动被拒绝，请核对入口 launchMode")
                        return
                    }
                    // 仅任务在本次调用前已消失时，允许走下面的无任务启动；不无限重试。
                    Log.w(LOG_TAG, "目标任务已消失，按无任务入口启动")
                }
            }

            // 按任务有无分支，而不是按进程冷热分支。冷进程也可能仍有系统保留的任务。
            // 没有选到主任务时，普通 Context 启动才需要 NEW_TASK。
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d(LOG_TAG, "无可用主任务，启动 deeplink path=${uri.path}")
            context.startActivity(launchIntent)
        } catch (error: Exception) {
            // 启动/系统查询失败不崩溃；BAL 拒绝也可能只有系统日志，未抛异常不代表已落地。
            Log.e(LOG_TAG, "AppTask 方案启动失败: ${error.javaClass.simpleName}")
        }
    }

    /** 不打印 keyword 或完整 URI 参数。 */
    private const val LOG_TAG = "WidgetTaskLauncher"
}
