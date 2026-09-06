package com.carrie.demo.searchtoolswidget.provider

/**
 * 小组件暗词集合的数据交付方式。
 *
 * Android 12（API 31）开始可以把 [android.widget.RemoteViews.RemoteCollectionItems]
 * 直接交给桌面宿主；API 29～30 则继续通过 RemoteViewsService 提供集合 item。
 */
enum class WidgetCollectionMode {
    /** API 29～30 使用的 RemoteViewsService/RemoteViewsFactory 兼容路径。 */
    LEGACY_REMOTE_SERVICE,

    /** API 31 及以上使用的平台原生内联集合路径。 */
    INLINE_REMOTE_COLLECTION,
    ;

    companion object {
        /** Android 12 对应的 API Level，也是原生 RemoteCollectionItems 的起始版本。 */
        private const val INLINE_COLLECTION_MIN_SDK = 31

        /**
         * 根据运行设备 API Level 选择集合实现。
         *
         * 该方法接收普通整数而不是直接读取 Build.VERSION，便于单元测试覆盖版本边界。
         */
        fun forSdk(sdkInt: Int): WidgetCollectionMode {
            return if (sdkInt >= INLINE_COLLECTION_MIN_SDK) {
                INLINE_REMOTE_COLLECTION
            } else {
                LEGACY_REMOTE_SERVICE
            }
        }
    }
}
