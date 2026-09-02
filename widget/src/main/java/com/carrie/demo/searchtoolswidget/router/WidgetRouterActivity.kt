package com.carrie.demo.searchtoolswidget.router

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.carrie.demo.searchtoolswidget.provider.WidgetInstanceUpdater
import com.carrie.demo.searchtoolswidget.storage.WidgetStorageInitializer

class WidgetRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetStorageInitializer.initialize(this)

        val action = intent.getStringExtra(WidgetClickContract.EXTRA_ACTION)
            ?.let { value -> runCatching { WidgetAction.valueOf(value) }.getOrNull() }
        if (action == null) {
            finish()
            return
        }

        WidgetInstanceUpdater.advanceAll(
            context = this,
            clickedPosition = intent.getIntExtra(
                WidgetClickContract.EXTRA_HINT_POSITION,
                WidgetClickContract.NO_HINT_POSITION,
            ),
        )
        val route = WidgetRoute.resolve(
            action = action,
            keyword = intent.getStringExtra(WidgetClickContract.EXTRA_KEYWORD),
        )
        startActivity(
            Intent().apply {
                component = ComponentName(packageName, route.targetClassName)
                putExtra(WidgetNavigationContract.EXTRA_KEYWORD, route.keyword)
                putExtra(
                    WidgetNavigationContract.EXTRA_FREEZE_HINT_ROTATION,
                    route.freezeHintRotation,
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        finish()
    }
}
