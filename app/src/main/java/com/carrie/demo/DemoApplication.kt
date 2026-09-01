package com.carrie.demo

import android.app.Application
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.SearchMockGraph
import com.carrie.demo.searchtoolswidget.sync.WidgetHintSyncScheduler
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetStorageInitializer.initialize(this)
        if (getProcessName() == packageName) {
            val dao = SearchMockGraph.hintDao(this)
            MockSearchData.ensureSeeded(this, dao)
            WidgetHintSyncScheduler(this).reconcile(enqueueImmediate = false)
        }
    }
}
