# 搜索工具桌面与负一屏小组件技术设计

- 状态：待评审
- 日期：2026-09-02
- 目标仓库：`Carrie-L/Demo2`
- 目标设备：荣耀 MagicOS 10（Android 16 / API 36）
- Android 配置：`minSdk 29`、`compileSdk 36`、`targetSdk 36`

## 1. 背景

本项目实现一个可同时运行在 Android 桌面和荣耀负一屏标准宿主中的 4×2 App Widget。组件顶部展示搜索暗词与搜索按钮，底部展示四个快捷入口。暗词按 8 秒间隔循环展示，并按默认 60 分钟、可由云配下发的 `frequency` 周期，从主 App 搜索模块已有数据库表中读取最新有序列表。

Demo 的页面与数据源允许 Mock，但模块边界、多进程、调度、存储、异常处理、跳转协议和测试标准均按可迁移到正式项目的质量设计。

旧项目 `Carrie-L/WidgetDemo` 的 `b` 分支已经验证过 `:widgetProvider` 独立进程、MMKV 多进程可见性和主/组件双进程初始化分流。本项目复用这些工程经验，但不复用旧锁屏组件“依赖宿主 onUpdate 广播推进内容”的轮换算法。

## 2. 目标与非目标

### 2.1 目标

1. 提供一个 4×2 搜索工具组件，同时适配标准桌面和荣耀负一屏宿主。
2. 顶部暗词由 `AdapterViewFlipper` 每 8 秒轮播，暗词数量不设固定上限。
3. 默认每 60 分钟读取一次主 App 搜索数据库，云配支持以分钟为单位下发 `frequency`。
4. 暗词池写入支持主进程与 `:widgetProvider` 进程跨进程可见。
5. 默认仅在去重后的有序暗词列表发生变化时换池；换池后立即回到第一条重新轮播。
6. 点击搜索区域或搜索按钮时，准确携带当前展示暗词进入 App。
7. 点击任意搜索入口或底部快捷按钮后，当前组件实例立即展示下一条暗词。
8. 新池为空时清空旧池、显示兜底文案并停止轮播；读取失败时保留旧池。
9. 支持多组件实例；各实例轮播进度独立，暗词池共享。

### 2.2 非目标

1. Demo 不接入真实网络搜索接口、真实搜索数据库或真实云配服务。
2. Demo 不实现荣耀私有负一屏 SDK；按系统已支持的标准 App Widget 能力接入。
3. 不要求熄屏、宿主不可见或 Launcher 进程重建期间保持严格的 8 秒墙钟轮播。
4. 不实现暗词池增量合并、回滚或业务版本管理。
5. 主搜索模块只提供数据库读取契约，不负责主动通知或刷新小组件。

## 3. 已确认决策

| 主题 | 决策 |
|---|---|
| UI 技术 | Kotlin + XML + RemoteViews，不使用 Glance |
| 轮播控件 | `AdapterViewFlipper` + `RemoteViewsService/RemoteViewsFactory` |
| 自动轮播 | 宿主进程内 `autoStart=true`、`flipInterval=8000ms` |
| Provider | `SearchToolsWidgetProvider` |
| 组件进程 | 与旧锁屏组件共用 `:widgetProvider` |
| 周期调度 | 主进程中的唯一 `PeriodicWorkRequest` |
| 默认周期 | 60 分钟；最低有效值 15 分钟 |
| 数据来源 | Worker 只读主搜索模块数据库表 |
| 组件缓存 | MMKV `MULTI_PROCESS_MODE` |
| 换池默认策略 | 去重后的有序列表变化才换池、刷新并归零 |
| 相同列表 | 不更新池、不刷新宿主，只更新最近成功刷新时间 |
| 去重 | 按最终展示文案精确稳定去重，保留第一次出现的位置 |
| 顺序 | 数据库查询顺序就是轮播顺序；顺序变化视为新池 |
| 空结果 | 覆盖为空、显示兜底、停止轮播 |
| 读取失败 | 不覆盖、不刷新，保留旧池 |
| 持久化进度 | 不保存轮播下标；轮播位置由各宿主实例维护 |

## 4. 待产品确认

### 4.1 相同数据是否强制重置

当前默认采用“仅列表变化才重置”：

- 内容和顺序完全相同：保留当前 Flipper 进度。
- 内容或顺序不同：主动换池并从第 0 条重新轮播。

备选策略是“每次成功读取都重置”。若产品选择备选，只调整 Worker 的换池决策，不改变进程、存储、组件和调度架构。

### 4.2 读取失败后的下一次尝试

待确认两种业务语义：

1. 本轮失败后直接等待下一个 `frequency` 周期。
2. 本轮先做有限次数退避重试，仍失败再等待下一个周期。

工程推荐第 2 种：最多补偿 2 次，初始退避 30 秒，持续失败后结束本轮并保留周期任务。周期 Worker 不应因一次本地读取错误进入永久失败态。

## 5. 总体架构

### 5.1 模块

项目采用 `:app` + `:widget` 双模块：

#### `:app`

- Demo Application 与主进程初始化
- Mock 搜索数据库和 DAO
- Mock 云配入口
- `WidgetHintSyncWorker`
- `WidgetHintSyncScheduler`
- `WidgetScheduleReceiver`
- `WidgetRouterActivity`
- 搜索激活页、搜索结果页和四个 Mock 目标页

#### `:widget`

- `SearchToolsWidgetProvider`
- `HintRemoteViewsService`
- `HintRemoteViewsFactory`
- `WidgetHintStore`
- `WidgetRemoteViewsRenderer`
- `WidgetInstanceUpdater`
- RemoteViews XML、ProviderInfo XML、图标与文案
- 面向 `:app` 的最小刷新 Facade

依赖方向为 `:app -> :widget`。`:widget` 不依赖 App 页面类或数据库实现，只通过显式 Intent/URI 路由契约和存储 Facade 协作。

### 5.2 进程归属

| 进程 | 组件 | 职责 |
|---|---|---|
| 主进程 | Worker、Scheduler、Mock DB、云配、页面、Router | 读取数据库、更新 MMKV、导航 |
| `:widgetProvider` | Provider、RemoteViewsService/Factory、MMKV 读取 | 向宿主提供组件 UI 与暗词集合 |
| Launcher/荣耀负一屏宿主 | RemoteViews、AdapterViewFlipper | 实际渲染并执行 8 秒翻页 |

同一 `:widgetProvider` 进程被锁屏或桌面组件任一组件拉起时，会执行该进程的 `Application.onCreate()`，但只调用目标组件回调，不会自动触发另一 Provider 更新。因此 `Application` 必须按进程名分流，组件进程只做 MMKV 等必要的轻量初始化。

普通进程被系统杀死后可以由宿主绑定或显式广播重新拉起；用户对整个 App 执行“强行停止”后，WorkManager 和广播通常不会恢复，直到用户再次打开 App。

## 6. 暗词数据模型与 MMKV

### 6.1 数据边界

数据库层向 Worker 提供已经排序的非空暗词列表。正式数据库查询必须显式 `ORDER BY` 业务排序字段，并以稳定 ID 作为第二排序条件，不能依赖 SQL 默认返回顺序。

Worker 将数据库行映射成最终展示文案列表，然后做最小处理：

1. 按展示文案精确比较。
2. 稳定去重，保留第一次出现的位置。
3. 不做大小写归一化、模糊合并等清洗。

### 6.2 MMKV Key

| Key | 类型 | 用途 |
|---|---|---|
| `widget_hint_pool_json` | JSON 字符串 | 去重后的有序暗词列表 |
| `widget_last_successful_refresh_at_ms` | Long | 最近一次“读库 + 缓存提交”成功时间 |
| `widget_frequency_minutes` | Long | 当前生效的调度周期，默认 60 |

暗词池直接覆盖同一个 key，不执行“先删除再插入”，避免跨进程读取到人为制造的空窗。`lastSuccessfulRefreshAt` 不参与请求闸门，只用于诊断、调试和未来可观测性。

不保存业务版本号：当前只有唯一 Worker 写入口，没有增量、回滚和并发新旧请求裁决需求。若未来增加多个可并发写入口，应先收敛为串行刷新协调器，再根据真实业务引入代次，而不是提前生成无语义版本号。

### 6.3 比较规则

比较对象是去重后的有序文案列表。只有数量、每项内容和顺序都相同才视为相同池。

| 读取结果 | 处理 |
|---|---|
| 失败 | 保留全部旧状态，不更新成功时间，不刷新宿主 |
| 成功且与旧池相同 | 只更新成功时间，Flipper 继续当前位置 |
| 成功且与旧池不同 | 覆盖新池、更新时间、主动刷新所有组件实例并归零 |
| 成功且为空、旧池非空 | 覆盖为空、更新时间、主动切换到兜底状态 |
| 成功且为空、旧池也为空 | 只更新时间，不重复刷新宿主 |

## 7. WorkManager 调度

### 7.1 为什么使用 WorkManager

WorkManager 负责小时级持久调度，不负责 8 秒轮播。8 秒轮播完全由宿主进程内的 `AdapterViewFlipper` 完成，避免高频唤醒 App、AlarmManager 或 Binder 更新。

周期任务使用固定唯一名称 `search_tools_widget_hint_sync`，确保同一时间只有一个周期请求。首次周期执行可以立即或在系统约束满足后尽快发生；后续每个周期执行一次，但实际运行时间可能因 Doze、负载和省电策略延迟。

### 7.2 生命周期

1. 第一个组件实例添加：`SearchToolsWidgetProvider.onEnabled()` 向主进程 `WidgetScheduleReceiver` 发送显式包内通知。
2. 主进程调用 Scheduler，注册唯一周期 Worker；组件先展示 MMKV 旧池或兜底，等待首次 Worker 完成。
3. 后续组件实例添加：不重复注册周期任务。
4. 最后一个组件实例删除：`onDisabled()` 通知主进程取消周期任务。
5. 云配周期改变：主进程更新 `frequency` 并使用周期任务更新策略替换/更新同名任务。

不从 `:widgetProvider` 进程直接访问 WorkManager，避免普通 WorkManager 在多进程中的初始化与调度归属问题。组件进程只发送显式通知，由主进程统一管理任务。

### 7.3 周期约束

- 默认：60 分钟。
- 云配字段：`frequency`，单位分钟。
- 小于 15：按 15 分钟兜底并记录异常配置。
- 非法或缺失：回退 60 分钟。
- 本任务只读本地数据库，不设置网络约束。
- 只有配置值实际变化时才更新周期任务，避免无意义重排。

### 7.4 Worker 结果

Worker 成功路径：

1. 查询数据库。
2. 映射为展示文案并稳定去重。
3. 与 MMKV 旧池比较。
4. 按比较结果写入 MMKV。
5. 需要换池时发送显式 `ACTION_HINT_POOL_CHANGED`。

数据库异常或 MMKV 暗词池写入失败时，不发送刷新通知。失败后的重试策略按产品确认项落地。

## 8. RemoteViews 与 8 秒轮播

### 8.1 布局

组件为 4×2：

- 第一行：占主要宽度的暗词搜索区域 + 搜索按钮。
- 第二行：四个等宽快捷按钮，不横向滚动。

顶部不是单独的静态 TextView，而是一个 `AdapterViewFlipper`。每个集合项包含完整的“暗词区域 + 搜索按钮”，因此当前可见项的两个点击入口都能绑定同一个暗词，无需向 Launcher 查询当前下标。

ProviderInfo 使用 `updatePeriodMillis=0`，因为周期读取由 WorkManager 管理。4×2 的实际 dp 会随 Launcher 网格变化，布局使用弹性宽度和等权底部按钮，并在 MagicOS 10 桌面与负一屏分别验证。

### 8.2 宿主轮播

`AdapterViewFlipper` 设置：

- `android:autoStart=true`
- `android:flipInterval=8000`

RemoteViews 被 Launcher/负一屏宿主加载后，翻页计时器运行在宿主进程。App 主进程和 `:widgetProvider` 进程不需要每 8 秒存活或发送更新。

宿主不可见、熄屏或系统省电时可能暂停动画；恢复可见后继续或重启。Launcher 进程重建后可以从第一条重新开始，本设计不保证跨宿主重建保存轮播位置。

### 8.3 新池主动刷新

默认仅列表变化时执行：

1. Worker 覆盖 MMKV 新池。
2. Worker 向 `SearchToolsWidgetProvider` 发送显式自定义广播，不伪造受保护的系统 `APPWIDGET_UPDATE` 广播。
3. Provider 获取全部实例 ID。
4. 通知 `AdapterViewFlipper` 数据集变化，使 Factory 的 `onDataSetChanged()` 重新读取 MMKV。
5. 将各实例的显示位置重置为第 0 项，并确保自动轮播重新运行。

数据集刷新和归零在 MagicOS 10 上必须做真机时序验证。如果宿主对 `notifyAppWidgetViewDataChanged + setDisplayedChild(0)` 的应用顺序不稳定，降级方案是对变化后的池构建新的 RemoteAdapter 身份并执行完整 RemoteViews 更新，强制宿主重新绑定。

空池时切换到非 Flipper 的兜底布局，展示“暂无推荐内容”并停止轮播。后续非空新池到达时重新显示 Flipper 并从第一条启动。

## 9. 点击与页面路由

### 9.1 点击协议

集合项通过 PendingIntent Template + Fill-in Intent 携带：

- `appWidgetId`
- `action`
- `keyword`

底部四个按钮使用按实例构建的显式 PendingIntent。所有点击先让“被点击的组件实例”立即显示下一条，再执行页面导航；不同组件实例不互相推进。

### 9.2 搜索区域

点击当前暗词区域：

- 进入搜索激活页。
- 传入当前 `keyword`。
- 传入 `freezeHintRotation=true`。
- App 搜索框显示该暗词，并停止 App 原有暗词轮播。

点击搜索按钮：

- 传入当前 `keyword`。
- 直接进入搜索结果页并展示对应结果。

空池兜底状态没有真实暗词：点击搜索区域或搜索按钮均进入空白搜索激活页，不把兜底文案当作关键词。

### 9.3 Router

`WidgetRouterActivity` 位于主进程，采用透明/无历史中转样式：

1. 校验 action 与必要参数。
2. 调用 `WidgetInstanceUpdater` 立即推进指定 `appWidgetId`。
3. 构造目标页面任务栈。
4. 启动目标页并结束自身。

集合 Fill-in Intent 需要可变 PendingIntent Template；Template 必须显式指向本应用 Router，并最小化可填充字段。普通按钮 PendingIntent 使用 immutable 标志和唯一 requestCode，避免实例间覆盖。

## 10. 多进程初始化与安全

1. `Application.onCreate()` 根据进程名分流；`:widgetProvider` 禁止初始化 Room、完整导航、统计 SDK 和主业务对象图。
2. MMKV 使用固定文件 ID 和 `MULTI_PROCESS_MODE`，各进程懒加载单例 Store。
3. AppWidgetProvider 仅处理系统 AppWidget action 和本应用显式自定义 action。
4. 自定义刷新 Intent 设置明确 Component/Package，不暴露隐式广播面。
5. RemoteViewsService 按平台要求声明绑定权限和导出属性。
6. Router 只接受显式 PendingIntent 调用并校验 action，不信任外部 URI 直接携带的任意页面类名。
7. 日志和指标不记录完整暗词内容，只记录数量、是否变化、耗时和错误类型。

## 11. 异常与降级

| 场景 | 行为 |
|---|---|
| MMKV 首次无数据 | 兜底文案，等待首次 Worker |
| MMKV JSON 损坏 | 解码为空并记录错误；下次成功同步自愈 |
| 数据库读取异常 | 保留旧池，不刷新，不更新成功时间 |
| MMKV 写入失败 | 保留宿主当前显示，不发送刷新通知 |
| 数据库返回空 | 视为成功新池，清空并显示兜底 |
| 相同列表 | 不打断轮播，只更新成功时间 |
| Launcher 暂停动画 | 接受宿主行为，恢复后继续/重启 |
| `:widgetProvider` 被系统杀死 | 宿主需要数据时重新绑定并拉起 |
| App 被用户强行停止 | 不保证自动恢复，用户再次启动后恢复调度 |
| 云配低于 15 分钟 | 兜底为 15 并记录配置异常 |

## 12. 性能设计

1. 8 秒轮播不产生 App 进程唤醒和周期 Binder 更新。
2. `AdapterViewFlipper` 通过 RemoteViewsFactory 按需生成单项，避免把未知规模的全部 View 一次性塞入 RemoteViews。
3. MMKV 只保存轻量文本池；暗词池变化时才刷新宿主。
4. 数据相同时不触发 RemoteViews 更新，避免宿主重绑和动画重置。
5. 数据库查询和序列化在 Worker 后台线程执行。
6. `:widgetProvider` 只初始化渲染和存储依赖，控制独立进程基础内存。

## 13. 测试计划

### 13.1 JVM 单元测试

- 稳定去重保留第一次出现位置与原顺序。
- 相同有序列表判定为不换池。
- 内容变化、增删元素、顺序变化判定为换池。
- 空池状态转换。
- 读取失败不覆盖旧池。
- `frequency` 缺失、非法、小于 15 和正常值处理。
- Router action 与 extras 生成规则。

### 13.2 Instrumentation/集成测试

- 主进程写 MMKV 后 `:widgetProvider` 立即可读。
- 新池变化后主动刷新并从第 0 条展示。
- 相同池刷新成功但 Flipper 不归零。
- 空池显示兜底并停止轮播；再次有数据时恢复。
- 当前可见暗词的区域和搜索按钮携带正确 keyword。
- 搜索激活页冻结自身暗词轮播。
- 搜索结果页直接接收并展示对应 keyword。
- 六个入口点击后只推进被点击实例。
- 多实例共享池、进度独立。
- 唯一周期任务不会重复注册；云配变化后周期正确更新。
- 杀死 `:widgetProvider` 后宿主重新绑定恢复。

### 13.3 MagicOS 10 真机验收

- 桌面 4×2 与荣耀负一屏 4×2 均完整显示，无裁切。
- 可见状态下 8 秒轮播节奏。
- 切桌面页、进入负一屏、熄屏再恢复后的宿主行为。
- 新池主动刷新、立即回到第一条并重新轮播。
- 相同池不重置。
- 点击时 keyword 与屏幕当前文字严格一致。
- 点击推进与自动 8 秒翻页临界时序。
- Launcher 和 `:widgetProvider` 分别被杀后的恢复。
- 100、500、1000 条暗词下的加载耗时、内存和交互稳定性。

## 14. 可观测性

建议记录：

- Worker 开始/结束、调度来源与生效 frequency。
- 数据库读取数量、去重后数量、池是否变化。
- 数据库耗时、MMKV 写入耗时、组件刷新耗时。
- 最近成功刷新时间。
- 重试次数与最终错误分类。
- Provider/Factory 当前进程名和组件实例数量。

禁止在正式日志中输出完整暗词列表或用户搜索行为。

## 15. 与旧锁屏 Demo 的关系

### 15.1 可复用

- `:app + :widget` 多模块边界。
- `:widgetProvider` 独立进程。
- 按进程分流 Application 初始化。
- MMKV 多进程 Store 抽象。
- 显式 PendingIntent/中转页和任务栈验证经验。
- 纯逻辑决策单测。

### 15.2 不复用

- 由锁屏宿主 `onUpdate()` 广播推进内容。
- `nextIndex`、`lastUpdateTime`、屏幕亮灭沿判断。
- 冷启、热启和 onUpdate 多入口网络请求。
- `lastFetchTime` 防多入口撞车。
- 请求完成后等待下一次宿主更新才显示新池。

新组件的唯一数据入口是 WorkManager 读数据库；唯一轮播执行者是宿主 `AdapterViewFlipper`；新池变化后由本应用主动刷新。

## 16. 参考资料

- Android App Widgets：<https://developer.android.com/develop/ui/views/appwidgets>
- Advanced widget updates：<https://developer.android.com/develop/ui/views/appwidgets/advanced>
- Collection widgets：<https://developer.android.com/develop/ui/views/appwidgets/collections>
- RemoteViews：<https://developer.android.com/reference/android/widget/RemoteViews>
- AdapterViewFlipper：<https://developer.android.com/reference/android/widget/AdapterViewFlipper>
- PeriodicWorkRequest：<https://developer.android.com/reference/androidx/work/PeriodicWorkRequest>
- 旧 Demo B 分支 Manifest：<https://github.com/Carrie-L/WidgetDemo/blob/b/widget/src/main/AndroidManifest.xml>
- 旧 Demo MMKV：<https://github.com/Carrie-L/WidgetDemo/blob/b/widget/src/main/java/com/example/widget/data/storage/MmkvStorage.kt>
- 旧 Demo 验证记录：<https://github.com/Carrie-L/WidgetDemo/blob/b/docs/verify-notes.md>

## 17. Review 门禁

本设计文档通过 Review 前，不进入 Demo 实现。Review 至少确认：

1. 相同列表是否保持轮播进度。
2. 数据库失败后是否短期补偿重试。
3. 空池交互与兜底文案。
4. 点击推进后是否要求重新计算完整 8 秒间隔。
5. 云配 frequency 的上下限与更新时机。
6. MagicOS 10 负一屏是否完全复用标准 AppWidget 生命周期。


