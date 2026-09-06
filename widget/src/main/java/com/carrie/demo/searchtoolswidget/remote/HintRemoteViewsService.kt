package com.carrie.demo.searchtoolswidget.remote

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * API 29～30 提供集合数据的系统绑定服务。
 *
 * 它不是常驻 Service，也不负责 8 秒计时；桌面宿主需要列表 item 时才绑定并调用
 * [onGetViewFactory]。API 31+ 使用内联集合后，本服务仍保留以兼容旧系统。
 */
class HintRemoteViewsService : RemoteViewsService() {
    /** 为本次 Adapter Intent 创建一份读取 MMKV 的 Factory。 */
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return HintRemoteViewsFactory(applicationContext)
    }
}
