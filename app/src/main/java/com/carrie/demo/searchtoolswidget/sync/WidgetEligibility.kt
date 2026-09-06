package com.carrie.demo.searchtoolswidget.sync

/** 集中定义小组件是否允许读取搜索暗词的生命周期门槛。 */
object WidgetEligibility {
    /** 必须同时满足“用户已同意隐私协议”和“至少存在一个组件实例”。 */
    fun canSync(privacyAccepted: Boolean, widgetCount: Int): Boolean {
        return privacyAccepted && widgetCount > 0
    }
}
