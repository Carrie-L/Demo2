package com.carrie.demo.searchtoolswidget.remote

import android.content.Intent
import android.widget.RemoteViewsService

class HintRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return HintRemoteViewsFactory(applicationContext)
    }
}

