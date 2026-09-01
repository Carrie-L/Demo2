package com.carrie.demo.searchtoolswidget.sync

object WidgetEligibility {
    fun canSync(privacyAccepted: Boolean, widgetCount: Int): Boolean {
        return privacyAccepted && widgetCount > 0
    }
}

