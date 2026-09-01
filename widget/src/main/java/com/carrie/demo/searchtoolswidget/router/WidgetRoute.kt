package com.carrie.demo.searchtoolswidget.router

enum class WidgetAction {
    SEARCH_ACTIVATE,
    SEARCH_SUBMIT,
    FAVORITES,
    HISTORY,
    WEATHER,
    SETTINGS,
}

data class WidgetRoute(
    val targetClassName: String,
    val keyword: String,
    val freezeHintRotation: Boolean,
) {
    companion object {
        fun resolve(action: WidgetAction, keyword: String?): WidgetRoute {
            val safeKeyword = keyword?.takeIf { it.isNotBlank() }.orEmpty()
            return when (action) {
                WidgetAction.SEARCH_ACTIVATE -> WidgetRoute(
                    targetClassName = WidgetNavigationContract.SEARCH_ACTIVATION_CLASS,
                    keyword = safeKeyword,
                    freezeHintRotation = safeKeyword.isNotEmpty(),
                )

                WidgetAction.SEARCH_SUBMIT -> WidgetRoute(
                    targetClassName = WidgetNavigationContract.SEARCH_RESULT_CLASS,
                    keyword = safeKeyword,
                    freezeHintRotation = false,
                )

                WidgetAction.FAVORITES -> toolRoute(WidgetNavigationContract.FAVORITES_CLASS)
                WidgetAction.HISTORY -> toolRoute(WidgetNavigationContract.HISTORY_CLASS)
                WidgetAction.WEATHER -> toolRoute(WidgetNavigationContract.WEATHER_CLASS)
                WidgetAction.SETTINGS -> toolRoute(WidgetNavigationContract.SETTINGS_CLASS)
            }
        }

        private fun toolRoute(target: String) = WidgetRoute(
            targetClassName = target,
            keyword = "",
            freezeHintRotation = false,
        )
    }
}

