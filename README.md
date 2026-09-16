# Demo2：搜索工具桌面与负一屏小组件

这是一个按正式项目边界实现的 Android Demo：页面和搜索数据是 Mock，组件、数据库查询、MMKV、WorkManager、RemoteViews 和 ARouter 路由流程可迁移到真实项目。

## 环境

- Android Studio（JDK 17）
- Android SDK 36
- `minSdk 29` / `targetSdk 36`
- Kotlin + XML，不使用 Compose/Glance
- 目标设备：荣耀 MagicOS 10（Android 16）

用 Android Studio 直接打开仓库根目录即可。命令行构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 两种无中转点击写法（当前代码）

两种实现均不查找 MAIN 入口、不指定 Activity component，统一使用真实 URI + 本 App 包名。旧 `WidgetAppLauncher` 仅留作对照，不再被 Provider 调用。2026-09-16 已完成首轮构建、27 项单元测试和 lint（0 错误）；设备回归仍在进行，不能替代荣耀真机验收。

- **方案 A，默认：** `WidgetTaskLauncher.kt`。Provider 先推进，已有普通主任务时调用 `AppTask.startActivity()`，不加 `NEW_TASK`；没有可用主任务时才用普通 `startActivity() + NEW_TASK`。入口需为 `standard/singleTop`，本例按 application 默认 affinity 筛选主任务；多主任务、自定义入口 affinity 的正式项目需调整该类的任务选择条件。
- **方案 B：** `WidgetDirectEntryIntents.kt`。桌面直接发送 Activity PendingIntent，携带 `from_search_tools_widget=true`；Launcher 只判断该标记，发一条不带 URI 的 `HINT_WORD_NEXT`，Provider 只推进不再导航。它是直接启动的对照实现，不宣称解决方案 A 针对的任务恢复问题。
- **切换：** `WidgetPendingIntents.USE_DIRECT_ENTRY=false` 使用 A，改为 `true` 使用 B。修改后覆盖安装触发已有 `MY_PACKAGE_REPLACED` 重绑，或重新添加组件；宿主旧 RemoteViews 不会因只修改 Kotlin 常量就自动改变。
- Mock Launcher 已补独立 deeplink IntentFilter，并接受 uri-only / ACTION_VIEW；正式项目保持自己的 action/category 协议。若系统严格匹配要求非空 action，按入口声明填写，方案 B 的模板和静态按钮要一起改。
- 两套写法都不改变 App 分发后的启动行为，也不绕过系统后台启动限制。A 的广播先到 Provider；B 的推进要等 Launcher 执行，冷启等待期间不会提前推进。

## Demo 操作

1. 启动 App，点击“切换隐私同意状态”，直到页面显示“已同意”。
2. 在桌面或荣耀负一屏添加“常用搜索工具”组件，尺寸选择 4×2。
3. 第一个组件添加后会立即安排一次 Worker；成功后组件显示数据库暗词并每 8 秒轮播。
4. “写入初始/全新/含重复文案/清空数据库”只修改搜索 Mock 表，不主动通知组件；组件等下一次周期任务读取。
5. 点击暗词进入搜索激活页：当前词写入 App 搜索框，App 自身暗词轮播被冻结。
6. 点击“搜索”进入对应结果页；点击收藏、历史、天气、设置进入四个不同 Mock 页面。
7. 点击任意组件入口后，所有已添加的工具组件都通过 `showNext()` 从各自当前位置前进一条。

## 已实现语义

- 搜索页和 Worker 调用同一个 `SearchHintDao.queryHints()`。
- 小组件不额外去重、过滤或排序；重复文案会按数据库原始记录保留。
- 新旧池按完整有序 `WidgetHintRecord` 列表比较，等价于 `newPool == oldPool`。
- 相同池只更新成功时间，不打断当前轮播；变化池整体覆盖 MMKV，并让所有实例回到第 0 条。
- 读取成功但结果为空：清空旧池、显示兜底文案并停止轮播。
- 读取失败：保留旧池，最多补偿 2 次，本轮结束后仍保留后续周期任务。
- 默认周期 60 分钟，Mock 云配可切换 15/60/120 分钟；低于 15 分钟会钳制为 15。
- 周期频率未变时 `KEEP` 既有 WorkManager 计时；频率变化时 `UPDATE` 同名任务并保留已经过去的计时时间。例如 60→15 时，若旧周期已经过了 15 分钟，新任务会尽快具备执行资格，不额外创建一次性立即任务。
- 只有“隐私已同意且至少存在一个组件实例”才读库；撤回隐私会取消任务、清池并刷新兜底。
- Provider、RemoteViewsService、Worker 和页面统一运行在主进程；MMKV 使用 `SINGLE_PROCESS_MODE`。
- API 29～30 使用 `RemoteViewsService/RemoteViewsFactory`；API 31+ 使用系统原生 `RemoteViews.RemoteCollectionItems`，未增加 AndroidX RemoteViews 依赖。
- Flipper item 只包含暗词与搜索按钮，底部四个工具按钮固定在根布局；轮播切换动画已取消。
- 默认方案 A 先发送 `HINT_WORD_NEXT` 给 Provider，再用 AppTask 进入 App；方案 B 直接进入 App 后通知 Provider。无 URI 的 next 只推进。
- 推进使用完整 RemoteViews + 一次 `showNext()`，不归零；不能用局部更新，系统合并会丢弃该动作。
- widget 不查找或指定入口组件，不引用 `LauncherActivity::class.java`；传真实 `data URI` 和可选 `keyword`，仅方案 B 额外传来源 boolean，工具按钮不传搜索词。
- 主 App 的 `LauncherActivity` Mock 通用 deeplink 接收及 ARouter 跳转；方案 B 只额外发送 next 广播，没有按钮 resolve、点击队列、直接调用组件推进或生命周期监听。搜索页自行处理 keyword 和冻结。
- A 的指定任务启动不带任务 flags，无任务才带 `NEW_TASK`；B 的 Activity PendingIntent 带 `NEW_TASK | REORDER_TO_FRONT`，将已有入口移到前台并交付新 Intent，入口需在 `onNewIntent` 处理新 URI。API 35 回归曾复现 B 只用 `NEW_TASK` 时第三次点击首个 URI 返回 result=3 却不通知入口，此修正待复测。不使用 `CLEAR_TOP`，普通返回、配置恢复不重放 URI 或 next；正式项目入口 launchMode/任务配置仍需验收。
- 删除中转页及专用主题；升级通过 `MY_PACKAGE_REPLACED` 重绑为新版广播点击，不读数据库、不推进、不归零。详见 [点击方案](docs/plans/2026-09-14-widget-main-entry.md)。
- 自定义 `ACTION_HINT_POOL_CHANGED` 在 Provider 的 `onReceive()` 处理；系统首次 UI 由 `onUpdate()` 设置。同一进程内重复的系统 `onUpdate()` 不重建已初始化实例，避免当前轮播归零。

## 多实例说明

桌面和负一屏实例共享同一数据池。新池到达时全部定位第一条；普通点击让全部实例各自前进一条，不把 B 的索引强制设成 A 的索引。8 秒计时由各宿主的 `AdapterViewFlipper` 独立维护，不要求不同宿主永久显示同一条；晚添加、隐藏暂停、应用进程死亡或宿主重建造成的后续相位偏移属于已接受行为。

`showNext()` 是相对动作，不是持久化索引。完整 RemoteViews 被宿主重新应用时，缓存的动作可能再次执行；宿主不可用期间多次点击也不保证逐条重放。因此本实现保证正常存活宿主的点击推进，不承诺宿主重建后精确恢复进度。这也是系统废弃 `showNext()` 的原因之一，详见 [点击链路修复说明](docs/plans/2026-09-11-widget-click-fixes.md)。

## 工程边界

- `app/.../searchmock/*`：Mock 搜索数据库、隐私/云配和页面。
- `app/.../searchtoolswidget/sync/*`：运行在主进程的 Worker、Scheduler 和 Receiver。
- `app/.../searchmock/navigation/*`：主 App 通用 RoutePath 和 keyword 协议，不包含小组件业务。
- `widget/.../searchtoolswidget/*`：MMKV Store、Provider、RemoteViews、真实 deeplink/PendingIntent、App 启动和组件资源；不依赖 ARouter，不包含 Activity。

当前两种点击实现以本页上方说明和两个新类为准；[先前广播入口方案](docs/plans/2026-09-14-widget-main-entry.md) 保留历史背景，其中 component/flags 部分已被替代。组件设计见 [RemoteViews 正式化重构设计](docs/plans/2026-09-06-widget-remoteviews-refactor-design.md)。

## 仍需荣耀真机验收

- MagicOS 10 负一屏是否完整复用标准 AppWidget 生命周期。
- API 29～30 `notifyAppWidgetViewDataChanged + setDisplayedChild(0)` 的实际执行顺序，以及 API 31+ 内联集合表现。
- 桌面/负一屏可见性切换、熄屏恢复和 Launcher 重建后的 Flipper 行为。
- 4×2 尺寸、无动画切换、点击 `showNext()` 与自动翻页临界时序。
- 六个入口的真实冷启动、热启动和回桌面动画未结束时的点击。系统/厂商没有交付点击时，App 无法凭空恢复新按钮，必须结合 `WidgetClick`、`AppDeepLink` 和系统启动日志定位；模拟器不能代替问题机型验收。正式项目若目标是 singleTop/singleTask，目标页也必须在 onNewIntent 重新处理搜索参数。


## 本次推送与体验注意

按用户要求，2026-09-15 的广播点击改造写完即推送，不以完整测试通过作为前置条件。新增协议回归与全实例推进测试不等于荣耀设备验收。

广播启动 Activity 不使用 Android 12+ 增强的小组件过渡动画；冷启 Logo 要等 App 初始化及 Receiver 执行后、Activity 开始启动才出现。详见 [Android 官方说明](https://developer.android.com/develop/ui/views/appwidgets/enhance#enable-smoother-transitions)。
