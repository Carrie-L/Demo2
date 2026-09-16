package com.carrie.demo.searchtoolswidget.router

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * 方案 B：桌面直接发送 Activity PendingIntent，入口收到后发一条纯 HINT_WORD_NEXT 广播。
 *
 * 切换方法：把 WidgetPendingIntents.USE_DIRECT_ENTRY 改为 true，然后重新下发组件布局。
 * 不查入口、不指定 component，真实 URI 仍由主 App 的 Manifest/路由处理。
 * 若设备要求非空 action，按正式入口协议同时修改本类两个 Intent 的构造，不要只改静态按钮。
 * 这是供对照的直接启动写法，不等同于方案 A，也不宣称规避了所有 NEW_TASK 复用问题。
 */
object WidgetDirectEntryIntents {
    /** 只标记点击来源，不编码按钮、组件实例或路由；主 App 只据此发送一次 next 广播。 */
    const val EXTRA_FROM_WIDGET = "from_search_tools_widget"

    /**
     * 集合模板必须 data=null，点击时现有 fillIn() 才能填入当前 item 的 URI/keyword。
     * setPackage 固定本 App 范围；无需 FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT，也不填伪 URI。
     */
    fun collectionTemplate(context: Context): PendingIntent? = try {
        val intent = Intent().apply {
            setPackage(context.packageName)
            putExtra(EXTRA_FROM_WIDGET, true)
            // NEW_TASK 找任务，REORDER_TO_FRONT 把已有入口移到前台并交付新 Intent。
            // 只用 NEW_TASK 时，重复首个 URI 可能仅恢复旧任务而不通知入口。
            // 不清除业务页面；入口必须在 onNewIntent 中再次消费 URI。
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    } catch (error: Exception) {
        Log.e(LOG_TAG, "创建直接启动模板失败: ${error.javaClass.simpleName}")
        null
    }

    /** 静态按钮的真实 URI 与 keyword 已经确定，使用 IMMUTABLE，工具按钮不带 keyword。 */
    fun action(context: Context, action: WidgetAction, keyword: String? = null): PendingIntent? = try {
        val intent = Intent().apply {
            data = Uri.parse(action.deepLink)
            setPackage(context.packageName)
            putExtra(EXTRA_FROM_WIDGET, true)
            if (action.acceptsKeyword && keyword != null) {
                putExtra(WidgetClickContract.EXTRA_KEYWORD, keyword)
            }
            // 保持 uri-only 协议。正式入口若要求 action/category，模板也要同步填写。
            // 和集合模板保持一致，避免重复首个 URI 时只恢复旧页面。
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        PendingIntent.getActivity(
            context, action.ordinal + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    } catch (error: Exception) {
        Log.e(LOG_TAG, "创建直接启动按钮失败: ${error.javaClass.simpleName}")
        null
    }

    /** 日志不输出搜索词。 */
    private const val LOG_TAG = "WidgetDirectEntry"
}
