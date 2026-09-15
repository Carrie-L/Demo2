# 小组件点击推进与路由修复（2026-09-11）

> 中转 Activity 部分为历史实现：2026-09-14 后续要求已改为 [主 App 直接接收并路由](2026-09-14-widget-main-entry.md)。showNext 的系统合并结论仍然适用；下文历史测试不代表当前主入口的验收结果。

本说明覆盖旧设计中的“点击只推进来源实例”“showNext 局部更新”“中转页空 taskAffinity/noHistory”规则。数据库与 WorkManager 同步策略不变。

本文保留 2026-09-11 的修复根因和当时验收记录。2026-09-14 的新需求将“中转始终无 Logo”调整为“冷启动 theme 显示 Logo、已有页面的进程采用透明中转”；当前主题方案以 [冷热启动主题说明](2026-09-14-widget-launch-theme.md) 为准，点击推进与路由队列规则不变。

## 主进程与点击范围

Provider、RemoteViewsService、Router、Worker 与搜索页面都运行在 App 主进程。Manifest 不配置独立 `android:process`；MMKV 保持 `SINGLE_PROCESS_MODE`。Flipper 的 View 和自动 8 秒计时仍在桌面/负一屏宿主进程，这与 Provider 在主进程并不矛盾。

点击任意合法入口，`WidgetInstanceUpdater.advanceAll()` 获取此 Provider 的全部实例，再让每个实例从自己的当前索引前进一条。不触碰其他类型 Provider，也不要求多个宿主严格同相位。工具按钮不用携带 keyword；搜索入口用点击 item 自带的旧词导航，不能在推进之后重新从池里猜当前词。

## 为什么旧 showNext 没生效

旧调用为 `partiallyUpdateAppWidget(ids, RemoteViews(...).apply { showNext(...) })`。

Android 系统实际会先把局部 RemoteViews 合并到旧完整快照，再把合并结果交给宿主。`showNext()` 的 `ViewContentNavigation` 动作采用 `MERGE_IGNORE`，因此动作被合并过程丢弃。若旧快照含有 `setDisplayedChild(0)`，局部更新还会重新应用这个归零动作。

依据：[AOSP AppWidgetServiceImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java) 的 `updateAppWidgetInstanceLocked`，以及 [RemoteViews](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/widget/RemoteViews.java) 的 `mergeRemoteViews` / `ViewContentNavigation.mergeBehavior`。

修复：点击时从当前 MMKV 构建完整 RemoteViews，在末尾仅附加一次 `showNext()`，使用 `updateAppWidget()`；不带 `setDisplayedChild(0)`。相同布局在存活宿主上 reapply，保留当前位置后推进；底部工具区仍是静态 View，无淡入淡出。API 31+ 用系统内联集合，API 29～30 保留 Service/Factory 与相同池对应的 Adapter URI。没有新增依赖、透明覆盖层或应用计时器，也不每 8 秒全量刷新。

保留 `showNext()` 的边界必须记住：它是非幂等的相对动作，缓存 RemoteViews 在宿主重建或重新应用时可能再执行；宿主不可用时连续多次点击也不承诺每个动作都逐条恢复。用户已接受进程/宿主重建不保存轮播位置。本次不改成应用持久化索引；若以后要严格保证任何重建场景下一次且仅一次，必须重新讨论索引所有权。

## 路由与 Logo

旧入口只处理 `onCreate()`，且 `ARouter.navigation()` 后立即 `finish()`。ARouter 可能先异步执行拦截器，此时目标尚未启动；空 affinity 加目标 NEW_TASK 还会额外切任务。这些是需要消除的时序/窗口风险，并不是仅凭源码就能认定每台设备的 Logo 都由同一个原因造成。

2026-09-11 修复后的链路（其中第 6 项外观规则已于 2026-09-14 调整）：

1. PendingIntent 显式进入 Router，同 App 任务，`NEW_TASK | CLEAR_TOP` 配合入口 `singleTop`。
2. `onCreate()` 和 `onNewIntent()` 共用参数处理；后者先 `setIntent()`，不再复用旧参数。
3. 每次有效点击立即推进组件，再提交 ARouter 路由。保留正式拦截器，不使用 greenChannel 绕过。
4. 一条路由进行中时，仅记住最新待执行目标；当前回调结束再执行最新选择，避免旧异步请求最后盖回旧页。这个小队列在主进程中共享，配置重建只接管等待，不再重复消费旧 Intent；进程死亡后不重放已经消费过的旧点击。
5. `onArrival/onLost/onInterrupt` 才允许结束入口；失败记录路由路径，不输出用户关键词。
6. 当时入口不设置布局/Logo，使用透明背景、`windowDisablePreview`、无窗口动画和无导航过渡，不使用必须立即结束的 `Theme.NoDisplay`。这一版能移除业务中转 Logo，但慢冷启时也没有启动预览反馈，因此 2026-09-14 改为两套 theme，见下文。
7. 异步导航只持有 Application Context，当前入口用弱引用关联；目标 `NEW_TASK | CLEAR_TOP | NO_ANIMATION` 配合同 App affinity 复用主任务，避免回调持有已重建的 Activity。它仍受系统后台启动限制，不能据此从后台任意拉起页面。

2026-09-14 主题调整：Manifest 默认使用非透明、允许系统启动预览的冷主题，在 `Application` 初始化之前就提供 Logo。Router 在创建窗口前，依据 `WidgetLaunchTracker` 的进程内“是否创建过任意 Activity”标记选择冷主题或透明主题；后台任务唤起过进程但尚未进入页面仍走冷外观，配置重建沿用原外观。热路径不添加业务 Logo，也不播放额外退出动画。它不改变上述异步路由与连续点击修复，详细实现见 [冷热启动主题说明](2026-09-14-widget-launch-theme.md)。

升级注意：`PendingIntent.FLAG_UPDATE_CURRENT` 只刷新 extras，不能把旧 token 中的 Activity flags 换新。本次新增明确的 `ACTION_OPEN_WIDGET_ROUTE` 作为入口身份的一部分，让完整 render 绑定新 token；不反复 CANCEL_CURRENT，使正在显示的旧按钮 token 在重绑前仍可使用。

参考：[ARouter 1.5.2 源码](https://github.com/alibaba/ARouter/blob/1.5.2/arouter-api/src/main/java/com/alibaba/android/arouter/launcher/_ARouter.java)、[Android 任务和返回栈](https://developer.android.com/guide/components/activities/tasks-and-back-stack)。

迁移注意：正式目标若是 `singleTop/singleTask`，目标页自己也必须在 `onNewIntent()` 更新搜索框与冻结标记；入口不能代替目标消费参数。系统 SplashScreen 与 Activity 实际窗口不同；2026-09-14 的动态主题选择不能追溯取消 `onCreate()` 之前已绘制的预览，也不承诺所有温启动/厂商场景都没有系统启动屏。详见 [Android 启动屏迁移说明](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)。

## 回归方法

在专用模拟器安装 Debug App 和测试 APK 后，授权测试宿主绑定组件：

```text
adb shell appwidget grantbind --package com.carrie.demo --user 0
adb shell am instrument -w com.carrie.demo.test/androidx.test.runner.AndroidJUnitRunner
adb shell appwidget revokebind --package com.carrie.demo --user 0
```

`WidgetClickAdvanceTest` 使用真实系统 AppWidgetService 与两个 AppWidgetHostView，不 mock 系统合并逻辑；测试宿主不附着窗口，排除自动轮播干扰。覆盖旧局部动作被忽略、不同起点的两个实例连续推进六次并回环、同进程 onUpdate 不归零、最终清单的主进程配置。只删除测试分配的组件 id，结束后恢复 MMKV 快照。

路由回归覆盖真实 PendingIntent 连续工具跳转、可变集合模板 + Fill-in Intent 的新动作/关键词、旧 token 升级、异步入口再次收到点击，以及等待期间 Activity recreate 不重复消费点击。

2026-09-11 验证记录：API 35 x86_64 模拟器上 11 项 instrumentation 通过；34 项 JVM 单元测试通过；Debug 构建及 Lint 通过（0 errors，仍有依赖版本等警告）。完整更新还断言了底部工具 View 实例复用。额外从 Pixel Launcher 的实际组件连续点击收藏、Home、天气、Home、历史，最终 Activity 与本次按钮一致，且复用同一 App task；没有用测试权限代替这次实际桌面点击。API 29～30 兼容分支保留并参与编译，当次未完成该版本设备运行验收。此记录不代表 2026-09-14 主题修改的验收结论。

荣耀 MagicOS 10 的桌面/负一屏宿主行为、冷启动 Logo 及自动轮播临界点，仍需荣耀真机验收；模拟器测试不能代替这一步。
