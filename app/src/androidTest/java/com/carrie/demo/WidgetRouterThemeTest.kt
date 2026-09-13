package com.carrie.demo

import android.content.ComponentName
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.carrie.demo.searchtoolswidget.router.WidgetRouterActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 检查中转页两套 theme 的资源契约，避免慢冷启再次退化为“点击后桌面毫无反馈”。
 *
 * Manifest 中的默认主题由系统在应用代码运行前读取，所以必须允许绘制冷启预览。
 * 热启透明主题由 Activity 在创建窗口前选择，不能把 Manifest 默认值也设为透明。
 * 这里只验证主题配置，不证明系统在所有温启动场景都绝不展示 starting window；
 * 冷热选择逻辑、实际 Activity 路由与设备上的启动观感需要另行验证。
 */
@RunWith(AndroidJUnit4::class)
class WidgetRouterThemeTest {
    /** 使用被测 App 的合并资源，覆盖 widget 库资源和最终 Manifest 的实际配置。 */
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manifestAllowsAVisibleStartingWindowBeforeAppInitialization() {
        @Suppress("DEPRECATION") // 兼容 minSdk 29；这里只读取 Activity 的固定声明。
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, WidgetRouterActivity::class.java),
            0,
        )
        assertNotEquals("中转 Activity 必须声明启动主题", 0, info.themeResource)
        assertVisibleColdWindow(theme(info.themeResource))
        assertEquals(
            "Manifest 必须使用冷启主题，不能依靠 onCreate 才补上系统启动预览",
            styleId("Theme.Demo2.WidgetRouter.Cold"),
            info.themeResource,
        )
    }

    @Test
    fun coldThemeHasAnOpaqueWindowAndKeepsTheStartingPreviewEnabled() {
        assertVisibleColdWindow(theme(styleId("Theme.Demo2.WidgetRouter.Cold")))
    }

    @Test
    fun transparentThemeHasNoLogoBackgroundOrStartingPreview() {
        val attributes = theme(styleId("Theme.Demo2.WidgetRouter.Transparent"))
            .obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.windowIsTranslucent,
                    android.R.attr.windowDisablePreview,
                    android.R.attr.windowBackground,
                ),
            )
        try {
            assertTrue("热启中转窗口必须透明", attributes.getBoolean(0, false))
            assertTrue("透明中转主题不应请求额外的启动预览", attributes.getBoolean(1, false))
            val background = attributes.getDrawable(2)
            assertTrue("热启背景应是透明颜色，不能继承冷启 Logo 图层", background is ColorDrawable)
            assertEquals("热启背景不得遮挡原页面", 0, Color.alpha((background as ColorDrawable).color))
        } finally {
            attributes.recycle()
        }
    }

    /** Android 12 及以上使用平台 SplashScreen 属性；不引入新的兼容库依赖。 */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun coldThemeProvidesNativeSplashBackgroundAndLogoOnAndroid12Plus() {
        val attributes = theme(styleId("Theme.Demo2.WidgetRouter.Cold"))
            .obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.windowSplashScreenBackground,
                    android.R.attr.windowSplashScreenAnimatedIcon,
                ),
            )
        try {
            assertTrue("原生启动屏必须配置背景", attributes.hasValue(0))
            assertNotEquals("冷启不能是完全透明的启动屏", 0, Color.alpha(attributes.getColor(0, Color.TRANSPARENT)))
            assertNotNull("原生启动屏必须提供 Logo", attributes.getDrawable(1))
        } finally {
            attributes.recycle()
        }
    }

    /** 冷启默认窗口需允许预览，并有背景可在应用初始化期间提供视觉反馈。 */
    private fun assertVisibleColdWindow(theme: Resources.Theme) {
        val attributes = theme.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowIsTranslucent,
                android.R.attr.windowDisablePreview,
                android.R.attr.windowBackground,
            ),
        )
        try {
            assertFalse("冷启默认窗口不能透明，否则 Launcher 会一直留在屏幕上", attributes.getBoolean(0, false))
            assertFalse("冷启不能禁用系统启动预览，否则慢初始化期间没有反馈", attributes.getBoolean(1, false))
            assertNotNull("冷启窗口必须提供背景，不能到 onCreate 才补画 Logo", attributes.getDrawable(2))
        } finally {
            attributes.recycle()
        }
    }

    /** 动态查找使旧版本代码也能编译测试，缺失新主题时以明确断言失败而非编译失败。 */
    @Suppress("DiscouragedApi") // 本测试有意不引用尚未添加的 R.style 字段，以支持先验证红灯。
    private fun styleId(name: String): Int = context.resources
        .getIdentifier(name, "style", context.packageName)
        .also { assertNotEquals("缺少主题资源：$name", 0, it) }

    /** 不依赖当前页面的 theme，避免页面测试执行顺序影响资源断言。 */
    private fun theme(styleId: Int): Resources.Theme = context.resources.newTheme().apply {
        applyStyle(styleId, true)
    }
}
