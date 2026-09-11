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
- Provider、RemoteViewsService、Router、Worker 和页面统一运行在主进程；MMKV 使用 `SINGLE_PROCESS_MODE`。
- API 29～30 使用 `RemoteViewsService/RemoteViewsFactory`；API 31+ 使用系统原生 `RemoteViews.RemoteCollectionItems`，未增加 AndroidX RemoteViews 依赖。
- Flipper item 只包含暗词与搜索按钮，底部四个工具按钮固定在根布局；轮播切换动画已取消。
- 组件点击先进入无内容、无 Logo 的 `WidgetRouterActivity`，向所有实例下发完整 RemoteViews + 一次 `showNext()`，不归零，再通过共享 `RoutePath` 和 ARouter 跳转。不能用局部更新发送 `showNext()`，系统合并时会丢弃它。
- 中转页同任务运行，处理 `onCreate/onNewIntent`，在 ARouter 成功/失败回调后才结束；异步等待期间的新点击保留最新目标，避免旧请求随后盖回旧页。
- 自定义 `ACTION_HINT_POOL_CHANGED` 在 Provider 的 `onReceive()` 处理；系统首次 UI 由 `onUpdate()` 设置。同一进程内重复的系统 `onUpdate()` 不重建已初始化实例，避免当前轮播归零。

## 多实例说明

桌面和负一屏实例共享同一数据池。新池到达时全部定位第一条；普通点击让全部实例各自前进一条，不把 B 的索引强制设成 A 的索引。8 秒计时由各宿主的 `AdapterViewFlipper` 独立维护，不要求不同宿主永久显示同一条；晚添加、隐藏暂停、应用进程死亡或宿主重建造成的后续相位偏移属于已接受行为。

`showNext()` 是相对动作，不是持久化索引。完整 RemoteViews 被宿主重新应用时，缓存的动作可能再次执行；宿主不可用期间多次点击也不保证逐条重放。因此本实现保证正常存活宿主的点击推进，不承诺宿主重建后精确恢复进度。这也是系统废弃 `showNext()` 的原因之一，详见 [点击链路修复说明](docs/plans/2026-09-11-widget-click-fixes.md)。

## 工程边界

- `app/.../searchmock/*`：Mock 搜索数据库、隐私/云配和页面。
- `app/.../searchtoolswidget/sync/*`：运行在主进程的 Worker、Scheduler 和 Receiver。
- `widget/.../searchtoolswidget/*`：MMKV Store、Provider、RemoteViews、Router 和组件资源。

详细设计见 [RemoteViews 正式化重构设计](docs/plans/2026-09-06-widget-remoteviews-refactor-design.md) 和 [点击链路修复说明](docs/plans/2026-09-11-widget-click-fixes.md)。

## 仍需荣耀真机验收

- MagicOS 10 负一屏是否完整复用标准 AppWidget 生命周期。
- API 29～30 `notifyAppWidgetViewDataChanged + setDisplayedChild(0)` 的实际执行顺序，以及 API 31+ 内联集合表现。
- 桌面/负一屏可见性切换、熄屏恢复和 Launcher 重建后的 Flipper 行为。
- 4×2 尺寸、无动画切换、点击 `showNext()` 与自动翻页临界时序。
- 冷启动的系统启动屏与中转页需分别验收：本项目不绘制中转 Logo，但不承诺关闭厂商系统启动屏。正式项目若目标是 singleTop/singleTask，目标页也必须在 onNewIntent 重新处理搜索参数。
