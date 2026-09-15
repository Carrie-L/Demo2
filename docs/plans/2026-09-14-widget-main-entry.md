# 小组件广播点击与主 App 通用 deeplink 入口

2026-09-15 用户确认版。替代中转 Activity、主模块解析 WidgetAction、点击队列和 Activity 生命周期监听方案。

## 单次点击链路

1. 静态按钮或集合 item 发送 HINT_WORD_NEXT，显式目标为 widget 的 SearchToolsWidgetProvider。
2. Provider 在 onReceive 保存本次 data URI 和 extra keyword，然后调用 widget 内的 advanceAll。
3. 全部已添加实例各自 showNext 一次，不按点击实例隔离，也不 setDisplayedChild(0)。
4. widget 内的 WidgetAppLauncher 查询本 App 启动入口，仅取 ComponentName，重新创建 ACTION_VIEW Intent。
5. Intent.data 是真实 deeplink，唯一可选业务 extra 是 keyword；没有按钮 action、appWidgetId 或冻结标志。
6. App 的 LauncherActivity 仅 Mock 通用 URI 接收和 ARouter 跳转。搜索页自己根据 keyword 冻结原暗词轮播。

纯 HINT_WORD_NEXT（没有 URI）只推进，不打开 App。处理后不再次发送相同广播，否则会重复推进甚至循环。

## 文件职责

- widget/router/WidgetAction.kt：六个按钮对应的真实 deeplink，仅在 widget 内使用；不是跨模块枚举协议。
- widget/router/WidgetPendingIntents.kt：getBroadcast 点击凭据及集合 Fill-in，无 appWidgetId 参数。
- widget/provider/SearchToolsWidgetProvider.kt：区分点击、池变化和系统更新，点击推进只在本模块执行一次。
- widget/router/WidgetAppLauncher.kt：PackageManager 查入口 + Android Intent 启动；不引用 LauncherActivity::class.java。
- app/searchmock/ui/LauncherActivity.kt：仅 Mock App 通用入口，onCreate/onNewIntent 接收 URI；不处理组件按钮。
- app/searchmock/navigation/*：主 App 自己的 RoutePath/keyword 协议，widget 不依赖这些类。
- 原有 app/searchtoolswidget/sync/* 是既有搜索数据/调度接入层，本次不调整同步业务。

widget 不依赖 app 或 ARouter。主 App 不包含 WidgetRoute、MainWidgetNavigation、WidgetNavigationQueue，也不通过生命周期回调推进组件。

## URI 与 PendingIntent

真实 URI 示例：demo2://app/search/activation、demo2://app/search/result、demo2://app/tools/favorites。
host 为 app，path 为 /search/activation 等 App 已注册的 ARouter 路径。正式迁移时替换 widget 的地址常量和协议键名。

同一种静态按钮共享不可变 PendingIntent，requestCode 按入口区分，不按组件实例区分。
集合共用可变模板，模板 data 留空，当前 item 的 Fill-in 同时提供真实 URI 和 keyword。
旧版 RemoteViewsService 的 adapter data/poolToken 仍然保留：它属于绑定服务的缓存身份，不发送给 App 路由。

Provider 不导出；系统可以交付系统事件，桌面通过本 App 创建的显式 PendingIntent 发送点击，不对外开放任意推进或启动。

## 启动与异常边界

- 查询启动入口后只取 component，不沿用 MAIN action、LAUNCHER category 或查询结果的 flags。
- NEW_TASK | CLEAR_TOP | SINGLE_TOP：进入 App 任务，已有入口时关闭其上方页面并交付 onNewIntent；没有入口则创建。入口不必是任务根。
- CLEAR_TOP 会影响返回栈；不使用 CLEAR_TASK，不创建独立任务或透明中转页。
- 正式 App 如果有多个 Launcher/alias，必须核对查询结果是否就是实际处理 URI 的入口，不能仅凭 Activity 名字判断。
- 通用入口消费后清掉 URI/keyword，并保存消费状态，配置恢复不重放旧路由；热入口先 setIntent 新请求。没有点击队列。
- 不使用 requireNotNull/!! 强行取得启动组件。查不到入口安全返回；PendingIntent 创建、点击参数读取、推进、启动分别捕获可恢复异常。
- 推进失败仍尝试打开 App。底层持久化写入失败仍保留失败语义，不能吞掉异常后假报写入成功。

## 必须记住的体验限制

Android 官方允许用户点击 widget 的 PendingIntent 后由广播接收器启动 Activity，但此路径不使用 Android 12+ 增强的 widget 启动过渡动画。
冷启必须先执行 App 初始化和 Receiver，再启动 Activity；Logo 无法覆盖这之前等待进程初始化的阶段。
广播内不查数据库、不等待 Worker，不异步延迟启动。Android 后台启动规则仍适用；启动被系统拒绝可能只记日志，不抛异常。

官方依据：
- [Widget 启动过渡动画与广播中转](https://developer.android.com/develop/ui/views/appwidgets/enhance#enable-smoother-transitions)
- [后台 Activity 启动限制](https://developer.android.com/guide/components/activities/secure-bal)
- [CLEAR_TOP 与任务栈](https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_CLEAR_TOP)

## 验证及推送约定

用户要求本次代码写完立即推送，不等待完整测试通过。
已添加 WidgetBroadcastNavigationTest（广播类型、URI/keyword、六个入口、集合 Fill-in、异常边界），并让 WidgetClickAdvanceTest 通过 Provider 验证全部实例推进。
旧的直接进入 Main 的 action/队列测试、旧中转主题测试和旧 action 冷启脚本不再适用，已移除。

推送不等于完整验收。仍需 MagicOS 10 桌面与负一屏实测：进程冷启、连续热点击、回桌面动画未结束点击、多个实例推进。
定位时对照 WidgetClick、AppDeepLink 与 ActivityTaskManager 日志。模拟器直接发送 PendingIntent 不能证明荣耀宿主触摸交付正常。
