package com.carrie.demo.searchtoolswidget.storage

import android.content.Context
import com.tencent.mmkv.MMKV

object WidgetStorageInitializer {
    @Volatile
    private var initialized = false

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

