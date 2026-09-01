package com.carrie.demo

import android.app.Application
import com.carrie.demo.searchmock.data.MockSearchData
import com.carrie.demo.searchmock.data.SearchMockGraph

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) {
            val dao = SearchMockGraph.hintDao(this)
            MockSearchData.ensureSeeded(this, dao)
        }
    }
}

