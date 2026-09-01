package com.carrie.demo.searchmock.domain

data class SearchLaunchState(
    val initialKeyword: String,
    val shouldRotateHints: Boolean,
) {
    companion object {
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

