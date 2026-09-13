# 小组件冷启动 Logo / 热路径透明主题

**Goal:** 应用冷启动初始化期间立即提供 Logo 反馈；进程已有页面后的中转窗口透明，不额外播放 Logo 退出动画。

**Architecture:** Manifest 使用允许系统启动预览的 Cold style；Activity 创建窗口前选择 Cold / Transparent style。全 App 的进程内 UI 标记用于选择实际中转主题，不写 MMKV，不把进程初始化是否结束误当成启动来源。

**Tech Stack:** Kotlin、平台 Activity / SplashScreen、XML styles，API 29+，不新增依赖。

## 实现和验证顺序

1. 在 `app/src/androidTest/java/com/carrie/demo/WidgetRouterThemeTest.kt` 添加主题检查，先确认旧实现因禁止预览且透明而失败。
2. `widget/src/main/res/values/styles.xml` 增加冷/热样式，`values-v31/styles.xml` 定义原生启动屏图标和背景；Manifest 默认用 Cold。Logo 使用可替换的 Demo drawable。
3. 在 widget 的 router 包注册全 App Activity 生命周期观察；`DemoApplication.onCreate()` 最早安装。任何页面创建过后，后续 Router 使用透明主题。配置恢复沿用原窗口的选择，避免等待异步路由时突然切换外观。
4. `WidgetRouterActivity.onCreate()` 在 super 前选主题，API 30+ 热路径调用公开 `setTranslucent(true)`；API 29 的热路径用 `setVisible(false)` 不呈现中转窗口。API 31+ 移除退出动画，不加延时，也不改路由、点击推进或 WorkManager。
5. 运行主题、实际热路径、连续跳转、Activity 重建与全部组件测试；构建和 Lint 后推送。验证系统冷启动画面必须从已退出的进程启动，不能只在活进程里改一个布尔值冒充冷启。

## 必须保留的边界说明

- 系统在 Application 执行前读取 Manifest 主题创建 starting window，所以不能继续 `windowDisablePreview=true`，也不能等到 Router.onCreate 才创建 Logo 页面。
- 这里的进程标记是“是否已经创建过 Activity”。后台 Worker/Provider 先启动了进程但没有页面时，首次进入 UI 仍可显示 Logo；这不是系统对 cold/warm/hot 的完整分类。
- Android 12+ 冷启动和 Activity 需要新建的 warm start 都可能显示系统启动屏。我们的热路径保证实际中转窗口无 Logo；`setTheme()` 无法撤回它执行前已经显示的系统预览，不承诺所有 ROM 的 warm start 一帧 Logo 都没有。
- Cold style 保留 Logo 背景直到目标页接管，避免“Logo → 透明桌面 → 页面”。不为展示 Logo 人为等待。
- API 31+ 系统启动图标有平台自己的尺寸与遮罩，不能假设和背景中的 84dp Logo 完全一致。冷路径收到退出回调后保留这一份系统启动屏，随 Router 销毁清理，避免等待 ARouter 时出现两种大小的 Logo 跳变；热路径立即移除。这里只跟随已有路由生命周期，不添加计时或新的等待条件。
- API 29 没有公开的动态透明转换 API，不使用反射；热路径在 `onPostResume` 隐藏自身窗口，下一次 `onResume` 先恢复可见性标记通过框架校验，但系统预览仍按平台生命周期退出。不要将隐藏提前到 onCreate（随后被 performCreate 覆盖）或 onStart/onResume（target > 22 且尚未 finish 会被判为异常）。
- 不使用 `SplashScreen.setSplashScreenTheme()` 作冷热开关，它是系统持久化配置，会影响下一次真正冷启动。

官方依据：[启动屏机制](https://developer.android.com/develop/ui/views/launch/splash-screen)、[Activity.setTranslucent](https://developer.android.com/reference/android/app/Activity#setTranslucent(boolean))。

## 正式项目接入位置

1. 在主进程的 `Application.onCreate()` 中，`super.onCreate()` 之后、业务初始化之前调用 `WidgetLaunchTracker.install(this)`。不要在初始化结束时直接把标记设成 true，否则从组件冷启也会误用透明主题。
2. 保留 Router 在 Manifest 中的 `Theme.Demo2.WidgetRouter.Cold`，以及 `values` / `values-v31` 两套资源。正式项目替换 `widget_router_logo` 和 `widget_router_starting_background` 颜色即可；这些资源由 widget 模块提供，宿主可用同名资源覆盖。
3. 保留 `onCreate` 在 super 前的主题选择和保存状态、API 29 的恢复/隐藏配对、API 30+ 的透明转换。不要只复制 `setTheme()` 而遗漏窗口处理。
4. 不需要新增依赖、额外线程、延时任务或 Logo 布局。ARouter 拦截器、目标页 RoutePath、隐私门槛、数据库同步与组件轮播规则保持原状。

## 本次验证与待验收项

- 先在旧实现上运行新增主题测试，4 项均按预期失败；添加两套主题与 Manifest 配置后通过。
- 最终代码的 Debug App / 测试 APK 构建、34 项 JVM 单元测试、App / widget Lint 通过；Lint 为 0 errors，保留已有版本升级类警告。
- API 35 x86_64 模拟器运行 15 项设备测试全部通过：4 项主题配置、6 项真实 PendingIntent / ARouter 路由、5 项真实 AppWidgetService 点击与刷新测试。路由测试另断言了等待及重建期间的实际透明主题。
- API 29 的窗口可见性时机已对照平台生命周期源码检查并参与编译，尚未在 API 29 设备运行；不能用 API 35 的结果代替该兼容分支验收。
- 主题配置和路由测试不等于慢冷启动观感实测。本次未完成初始化暂停期间的 Logo 画面验证，仍需在荣耀 MagicOS 10 上验收真实冷启动、回桌面后二次点击，以及异步路由等待时的画面。调试等待设置已清除，生产代码未添加任何人为启动延时。
