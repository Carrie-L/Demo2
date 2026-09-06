package com.carrie.demo.searchmock.domain

/** 搜索激活页根据 ARouter 入参解析出的初始状态。 */
data class SearchLaunchState(
    /** App 搜索框首次显示的文字。 */
    val initialKeyword: String,
    /** 是否继续 App 原有暗词轮播。组件带入有效暗词时为 false。 */
    val shouldRotateHints: Boolean,
) {
    companion object {
        /** 只有“要求冻结且暗词非空”两个条件同时满足，才停止 App 原有暗词轮播。 */
        fun resolve(keyword: String?, freezeHintRotation: Boolean): SearchLaunchState {
            val normalizedKeyword = keyword?.takeIf { it.isNotBlank() }.orEmpty()
            val hasFrozenWidgetKeyword = freezeHintRotation && normalizedKeyword.isNotEmpty()
            return SearchLaunchState(
                initialKeyword = normalizedKeyword,
                shouldRotateHints = !hasFrozenWidgetKeyword,
            )
        }
    }
}
