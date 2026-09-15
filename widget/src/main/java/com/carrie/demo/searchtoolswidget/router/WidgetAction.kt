package com.carrie.demo.searchtoolswidget.router

/**
 * widget 内部按钮定义，不把枚举作为 extra 传给主 App。
 * deepLink 是与宿主约定的真实 URI，迁移时替换地址，不引用宿主 Activity 或 RoutePath 类。
 * demo2://app/search/result 的 path 是 /search/result，可直接交给 ARouter。
 */
enum class WidgetAction(val deepLink: String, val acceptsKeyword: Boolean = false) {
    /** 搜索模块根据 keyword 入参自行冻结原有轮播。 */
    SEARCH_ACTIVATE("demo2://app/search/activation", true),
    /** 直接展示点击时 keyword 的结果。 */
    SEARCH_SUBMIT("demo2://app/search/result", true),
    /** 收藏固定入口，无搜索词。 */
    FAVORITES("demo2://app/tools/favorites"),
    /** 历史固定入口，无搜索词。 */
    HISTORY("demo2://app/tools/history"),
    /** 天气固定入口，无搜索词。 */
    WEATHER("demo2://app/tools/weather"),
    /** 设置固定入口，无搜索词。 */
    SETTINGS("demo2://app/tools/settings"),
}
