package com.carrie.demo.searchtoolswidget.storage

import android.content.Context
import com.tencent.mmkv.MMKV

/** 保证任意 Provider、Worker、Service 或 Activity 入口都只初始化一次 MMKV。 */
object WidgetStorageInitializer {
    /** volatile 配合 synchronized，保证多线程可见性。 */
    @Volatile
    private var initialized = false

    /**
     * 幂等初始化。始终使用 Application Context，避免持有短生命周期组件。
     * Provider 与 WorkManager 都可能在 Application.onCreate 之外被系统直接拉起，所以入口各自调用。
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                MMKV.initialize(context.applicationContext)
                initialized = true
            }
        }
    }
}
