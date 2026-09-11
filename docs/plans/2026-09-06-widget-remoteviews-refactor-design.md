# 搜索工具小组件正式化重构设计

## 目标

将现有搜索工具小组件整理为可迁移到正式项目的实现：桌面宿主负责每 8 秒轮播；系统重复触发 `onUpdate()` 时不打断当前进程内已有实例的轮播；点击推进全部组件实例；API 31 及以上使用系统原生 `RemoteViews.RemoteCollectionItems`，API 29～30 保留 `RemoteViewsService/RemoteViewsFactory`；小组件和 WorkManager 核心链路具备完整、可维护的中文注释。点击和路由规则已按 2026-09-11 的需求更新。

## 已确认的产品语义

- `AdapterViewFlipper` 保持 `autoStart=true`，8 秒计时由桌面或负一屏宿主执行。
- 取消 Flipper 的淡入淡出动画，切换时直接显示下一条，避免整个 item 闪烁。
- 点击暗词、搜索按钮或任一工具按钮，都立即让全部已添加实例显示下一条。
- 点击实例 A 时，A 和 B 都各自前进一条，不将不同宿主的当前位置强制对齐。
- 进程被杀后允许从第一条重新开始，不持久化每个实例的轮播位置。
- 暗词池真正变化时，全部实例换用新池并从第一条开始。
- 工具按钮只携带动作和来源 `appWidgetId`，不携带暗词。
- 搜索框和搜索按钮必须携带当前可见暗词，分别进入搜索激活页和搜索结果页。

## RemoteViews 数据实现

### API 31 及以上

使用平台原生 `RemoteViews.RemoteCollectionItems` 将暗词 item 一次性交给宿主，不新增 `androidx.core:core-remoteviews` 依赖，也不经过 `RemoteViewsService`。

### API 29～30

继续通过 `RemoteViewsService/RemoteViewsFactory` 提供集合数据。Adapter Intent 的 URI 用于区分不同 `appWidgetId` 的远程 Adapter；`poolToken` 仅在暗词池内容变化时改变 Adapter 身份，作为旧系统及 OEM Launcher 集合缓存的兼容手段。

## 布局与点击

- 根布局保留静态的四个工具按钮，工具按钮不再复制到每个暗词 item。
- Flipper item 只包含暗词与搜索按钮。因为搜索按钮必须取得宿主当前显示的暗词，它仍与暗词属于同一个集合 item。
- Flipper 使用显式的零时长动画资源，避免搜索按钮随 item 淡入淡出；暗词直接切换，不做过渡动画。
- 集合 item 使用 PendingIntent Template + Fill-in Intent：Fill-in Intent 只提供搜索动作和当前暗词。
- 静态工具按钮使用普通 PendingIntent：提供固定工具动作和来源 `appWidgetId`。
- RouterActivity 在 ARouter 跳转前，对全部实例发送完整 RemoteViews，末尾附加一次 `RemoteViews.showNext()`，不带 `setDisplayedChild(0)`，不读取或维护集合 position。局部更新合并会忽略 showNext，不能使用。
- 中转页同主进程、同任务，透明无内容且禁用预览/过渡，不自行显示 Logo。`onCreate/onNewIntent` 共用入口；等待异步 ARouter 回调后才结束，等待期间保留最新目标。详见 [修复说明](2026-09-11-widget-click-fixes.md)。

## `onUpdate()` 与进程会话

`onUpdate()` 不能整体留空，因为每个新添加的实例都必须在这里绑定集合 Adapter 和点击事件。新增进程内 `WidgetRenderSession`：

- 当前进程第一次看到某个 `appWidgetId` 时执行完整初始化并登记。
- 同一进程内再次收到该实例的系统 `ACTION_APPWIDGET_UPDATE` 时跳过完整重建，避免破坏宿主当前轮播进度。
- 新增第二个实例时只初始化新 ID。
- 删除实例时移除登记；最后一个实例移除时清空登记。
- 进程被杀后登记自然丢失，下一次启动允许重新从第一条初始化。

新池、隐私状态变化属于全局业务状态变化，不受会话保护限制：它们主动刷新所有实例并回到第一条。

## WorkManager 数据同步

- `WidgetHintSyncScheduler` 只负责创建、替换或取消工作，不读取数据库。
- `PERIODIC_WORK_NAME` 是长期存在的唯一周期任务名；默认每 60 分钟运行，云配置变化时更新周期任务。
- `IMMEDIATE_WORK_NAME` 是一次性立即同步任务名，只用于首次添加组件或重新同意隐私后的首轮取数；正式流程没有手动同步入口。
- 云配频率变化只以 `UPDATE` 修改同名周期任务，不创建立即任务。`UPDATE` 保留旧任务已经过去的计时时间，再按新周期判断下一次何时具备执行资格。
- 首次添加时 `onUpdate()` 先用当前 MMKV 快照完成初始 UI；立即 Worker 异步读库后，只有新旧池不同才发送 `HINT_POOL_CHANGED` 触发第二次完整 render。池相同不会二次 render。这个顺序用于避免新组件在首次取数期间保持空白。
- `WidgetHintSyncWorker` 是两种任务共同执行的 Worker：检查隐私与实例门槛，读取搜索数据库，通过同步引擎更新 MMKV，并仅在池变化时通知 Provider。
- Worker 失败最多重试两次；最终仍失败时保留旧池。
- 空列表属于成功结果：替换为兜底状态并停止暗词轮播。

## 注释标准

- Widget 模块每个类、对象、枚举、数据类、常量和方法都补充中文 KDoc 或行内说明。
- App 侧 `searchtoolswidget.sync` 包完整解释 WorkManager 的唯一任务、策略、调度、门槛、重试和广播关系。
- XML 注释说明根布局、集合 item、空状态和零时长动画的目的。
- 注释解释“为什么”，不重复 Kotlin 语法本身；所有兼容分支和系统回调都说明触发者与影响范围。

## 验收

- API 29～30 构建走 Service/Factory 路径；API 31+ 构建并运行原生 RemoteCollectionItems 路径。
- 8 秒切换时底部按钮不闪，顶部搜索行无淡入淡出。
- 点击任意区域，所有实例从各自当前位置立即前进；连续点击和循环回到开头也要验证。
- 同一进程内向已有实例发送系统更新广播后，当前暗词不归零。
- 新池变化后全部实例显示新池第一条。
- 搜索框、搜索按钮、四个工具页的 ARouter 跳转全部正确。
- 单元测试、Lint 和 Debug 构建通过。
