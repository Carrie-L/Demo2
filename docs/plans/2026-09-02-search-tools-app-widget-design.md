# 搜索工具桌面与负一屏小组件技术设计

> 历史文档：其中“完整 item、点击推进全部实例”等方案已废弃。当前有效设计请以
> [2026-09-06 RemoteViews 正式化重构设计](2026-09-06-widget-remoteviews-refactor-design.md) 为准。

- 状态：已评审并完成 Demo 实现，待 MagicOS 10 真机验收
- 日期：2026-09-02
- 目标仓库：`Carrie-L/Demo2`
- 目标设备：荣耀 MagicOS 10（Android 16 / API 36）
- Android 配置：`minSdk 29`、`compileSdk 36`、`targetSdk 36`

## 1. 背景

本项目实现一个可同时运行在 Android 桌面和荣耀负一屏标准宿主中的 4×2 App Widget。组件顶部展示搜索暗词与搜索按钮，底部展示四个快捷入口。暗词按 8 秒间隔循环展示，并按默认 60 分钟、可由云配下发的 `frequency` 周期，从主 App 搜索模块已有数据库表中读取最新有序列表。

Demo 的页面与数据源允许 Mock，但模块边界、调度、存储、异常处理、ARouter 跳转协议和测试标准均按可迁移到正式项目的质量设计。

旧项目 `Carrie-L/WidgetDemo` 的 `b` 分支已经验证过独立组件进程。本项目现已改为单主进程，只复用其中的组件模块边界和中转 Activity 思路，不复用旧锁屏组件“依赖宿主 onUpdate 广播推进内容”的轮换算法。

## 2. 目标与非目标

### 2.1 目标

1. 提供一个 4×2 搜索工具组件，同时适配标准桌面和荣耀负一屏宿主。
2. 顶部暗词由 `AdapterViewFlipper` 每 8 秒轮播，暗词数量不设固定上限。
3. 默认每 60 分钟读取一次主 App 搜索数据库，云配支持以分钟为单位下发 `frequency`。
4. 暗词池由主进程中的 Worker 写入 MMKV，并由同一主进程中的组件代码读取。
5. 默认仅在数据库查询返回的完整有序记录列表发生变化时换池；换池后所有搜索工具组件实例立即回到第一条重新轮播。
6. 点击搜索区域或搜索按钮时，准确携带当前展示暗词进入 App。
7. 点击任意搜索入口或底部快捷按钮后，所有搜索工具组件实例各自立即推进一条暗词。
8. 新池为空时清空旧池、显示兜底文案并停止轮播；读取失败时保留旧池。
9. 支持桌面和荣耀负一屏同时添加多个实例；所有实例共享同一暗词池，换池时统一归零，点击时各自推进一条。

### 2.2 非目标

1. Demo 不接入真实网络搜索接口、真实搜索数据库或真实云配服务。
2. Demo 不实现荣耀私有负一屏 SDK；按系统已支持的标准 App Widget 能力接入。
3. 不要求熄屏、宿主不可见或 Launcher 进程重建期间保持严格的 8 秒墙钟轮播。
4. 不实现暗词池增量合并、回滚或业务版本管理。
5. 主搜索模块只提供数据库读取契约，不负责主动通知或刷新小组件。
6. 不在小组件侧对搜索数据库结果做额外去重、过滤、排序或文案归一化。

## 3. 已确认决策

| 主题 | 决策 |
|---|---|
| UI 技术 | Kotlin + XML + RemoteViews，不使用 Glance |
| 轮播控件 | `AdapterViewFlipper` + `RemoteViewsService/RemoteViewsFactory` |
| 自动轮播 | 宿主进程内 `autoStart=true`、`flipInterval=8000ms` |
| Provider | `SearchToolsWidgetProvider` |
| 组件进程 | 不设独立进程，统一运行在主进程 |
| 周期调度 | 主进程中的唯一 `PeriodicWorkRequest` |
| 默认周期 | 60 分钟；最低有效值 15 分钟 |
| 数据来源 | Worker 只读主搜索模块数据库表 |
| 组件缓存 | MMKV `SINGLE_PROCESS_MODE` |
| 页面跳转 | `WidgetRouterActivity` 中转后使用 ARouter `RoutePath` |
| 换池默认策略 | 数据库返回的完整有序记录列表变化才换池、刷新并归零 |
| 相同列表 | 不更新池、不刷新宿主，只更新最近成功刷新时间 |
| 数据处理 | 与搜索模块调用同一个 DAO 查询，不额外去重、过滤、排序或归一化 |
| 比较方式 | 与旧锁屏组件一致，直接比较新旧完整有序记录列表（`newPool == oldPool`） |
| 顺序 | 同一 DAO 查询的返回顺序就是轮播顺序；顺序变化视为新池 |
| 空结果 | 覆盖为空、显示兜底、停止轮播 |
| 读取失败 | 不覆盖、不刷新，保留旧池 |
| 多实例 | 共享池；换池时全部归零；点击时全部各自推进一条；不要求宿主计时器严格同步 |
| 持久化进度 | 默认不保存轮播下标；自动轮播位置由宿主维护 |

## 4. 已锁定的实施语义

### 4.1 多宿主不要求持续严格同步

“多个工具组件同一时刻显示同一条”需要区分两层语义：

1. **共享数据与统一触发（本次采用）**：桌面和负一屏的所有 `SearchToolsWidgetProvider` 实例读取同一 MMKV 池；新池到达时全部定位第 0 条；点击任一实例时，当前集合页携带自己的稳定 `position`，全部搜索工具实例定位到 `(position + 1) % poolSize`。这里的“全部实例”只指搜索工具组件，不包含锁屏组件或其他 Provider。
2. **8 秒计时永久严格同步（`AdapterViewFlipper` 无法保证）**：桌面 Launcher 与荣耀负一屏是不同宿主，各自维护 Flipper 计时。某个实例晚添加、宿主隐藏/暂停、进程重建或调度抖动后，两个宿主可能出现轮播相位偏移。

评审结论采用第 1 层，不要求第 2 层。桌面与负一屏因晚添加、隐藏暂停或宿主重建出现相位偏移是允许行为，只要各实例的顺序、8 秒轮播、新池归零和点击推进逻辑正确。

### 4.2 相同数据是否强制重置

实施采用“仅列表变化才重置”：

- 内容和顺序完全相同：保留当前 Flipper 进度。
- 内容或顺序不同：主动换池并从第 0 条重新轮播。

“每次成功读取都重置”不在本次 Demo 中实现。

### 4.3 读取失败后的下一次尝试

实施采用有限退避补偿：首次失败后最多补偿 2 次；持续失败则将本轮作为无更新结束，保留旧池并继续后续周期任务。周期 Worker 不会因一次本地读取错误进入永久失败态。

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
- 搜索激活页、搜索结果页和四个 Mock 目标页

#### `:widget`

- `SearchToolsWidgetProvider`
- `HintRemoteViewsService`
- `HintRemoteViewsFactory`
- `WidgetHintStore`
- `WidgetRemoteViewsRenderer`
- `WidgetInstanceUpdater`
- `WidgetRouterActivity`
- RemoteViews XML、ProviderInfo XML、图标与文案
- 面向 `:app` 的最小刷新 Facade

依赖方向为 `:app -> :widget`。`:widget` 不依赖 App 页面类或数据库实现，只通过共享 `RoutePath`、ARouter 参数契约和存储 Facade 协作。

### 5.2 包边界

Mock 搜索域和搜索工具组件域必须分包，避免 Demo 迁移时互相缠绕：

| 包族 | 内容 |
|---|---|
| `com.carrie.demo.searchmock.*` | Mock 数据库、Entity/DAO、搜索激活页、搜索结果页、四个 Mock 目标页 |
| `com.carrie.demo.searchtoolswidget.*` | Provider、RemoteViews、MMKV Store、Worker/Scheduler/Receiver、Router、刷新与路由协议 |

其中 Worker 虽运行在 `:app` 模块并依赖 Mock DAO，源码仍归入 `searchtoolswidget.sync` 包；Router 归入 `searchtoolswidget.router` 包。包表示业务归属，模块表示依赖边界，两者不混为一谈。

### 5.3 进程归属

| 进程 | 组件 | 职责 |
|---|---|---|
| 主进程 | Worker、Scheduler、ScheduleReceiver、Mock DB、云配、页面 | 校验隐私与实例门槛、读取数据库、更新 MMKV、承接最终页面 |
| 主进程 | Worker、Scheduler、ScheduleReceiver、Mock DB、云配、页面、Provider、RemoteViewsService/Factory、Router、MMKV | 读库、更新缓存、向宿主提供组件 UI并承接页面跳转 |
| Launcher/荣耀负一屏宿主 | RemoteViews、AdapterViewFlipper | 实际渲染并执行 8 秒翻页 |

组件不再声明 `android:process`，Provider、RemoteViewsService、Router 与主 App 共用默认进程。进程被宿主拉起时会先执行同一个 `Application.onCreate()`，因此 ARouter、MMKV、数据库与调度均在这一处初始化。

普通进程被系统杀死后可以由宿主绑定或显式广播重新拉起；用户对整个 App 执行“强行停止”后，WorkManager 和广播通常不会恢复，直到用户再次打开 App。

## 6. 暗词数据模型与 MMKV

### 6.1 数据边界

搜索 Mock 包和 Worker 调用同一个 DAO 查询方法；迁移到正式项目后，也必须复用搜索模块读取暗词的同一条查询语句和同一返回顺序。小组件不另写一套 SQL，也不额外添加 `DISTINCT`、`ORDER BY`、去重、过滤、大小写归一化或其他清洗。

数据库查询返回的完整有序记录列表就是比较对象和池数据来源。若跨模块存储需要映射为 `WidgetHintRecord`，映射必须逐项、一一对应地保留用于判断的字段和顺序，不能只留下展示文案后再比较。最终展示取记录中的暗词字段，列表本身的返回顺序就是轮播顺序。

### 6.2 MMKV Key

| Key | 类型 | 用途 |
|---|---|---|
| `widget_hint_pool_json` | JSON 字符串 | DAO 查询结果映射后的完整有序暗词记录列表 |
| `widget_last_successful_refresh_at_ms` | Long | 最近一次“读库 + 缓存提交”成功时间 |
| `widget_frequency_minutes` | Long | 当前生效的调度周期，默认 60 |
| `widget_privacy_allowed` | Boolean | 主进程依据 SP 同步出的只读派生门禁，避免展示隐私同意前的缓存 |

暗词池直接覆盖同一个 key，不执行“先删除再插入”。`lastSuccessfulRefreshAt` 不参与请求闸门，只用于诊断、调试和未来可观测性。

不保存业务版本号：当前只有唯一 Worker 写入口，没有增量、回滚和并发新旧请求裁决需求。若未来增加多个可并发写入口，应先收敛为串行刷新协调器，再根据真实业务引入代次，而不是提前生成无语义版本号。

### 6.3 比较规则

与旧锁屏组件保持一致：直接使用完整有序记录列表的结构相等判断，即 `newPool == oldPool`。它不是“按展示文案比较”，也不做额外去重；记录字段、数量或顺序任一变化都会得到新池。

| 读取结果 | 处理 |
|---|---|
| 失败 | 保留全部旧状态，不更新成功时间，不刷新宿主 |
| 成功且与旧池相同 | 只更新成功时间，Flipper 继续当前位置 |
| 成功且与旧池不同 | 覆盖新池、更新时间、主动刷新所有搜索工具组件实例并归零 |
| 成功且为空、旧池非空 | 覆盖为空、更新时间、主动切换到兜底状态 |
| 成功且为空、旧池也为空 | 只更新时间，不重复刷新宿主 |

## 7. WorkManager 调度

### 7.1 为什么使用 WorkManager

WorkManager 负责小时级持久调度，不负责 8 秒轮播。8 秒轮播完全由宿主进程内的 `AdapterViewFlipper` 完成，避免高频唤醒 App、AlarmManager 或 Binder 更新。

周期任务使用固定唯一名称 `search_tools_widget_hint_sync`，确保同一时间只有一个周期请求。首次添加或重新满足门槛时，另行提交唯一的一次性 Worker 立即取数；周期 Worker 的首次执行延迟一个完整周期，避免和这次立即取数竞争。后续每个周期执行一次，但实际运行时间可能因 Doze、负载和省电策略延迟。

### 7.2 生命周期

数据获取采用“隐私已同意 + 至少存在一个搜索工具组件实例”的双门槛：

1. 组件首次添加时，系统会调用 Provider 生命周期。`onUpdate()` 先立即提交初始 RemoteViews：隐私未同意或 MMKV 无池时显示兜底；存在可用缓存时显示缓存第一条并启动 Flipper。
2. 第一个实例对应的 `onEnabled()` 向主进程 `WidgetScheduleReceiver` 发送显式包内通知。主进程读取隐私协议 SP，并通过 `AppWidgetManager` 再确认至少存在一个 `SearchToolsWidgetProvider` 实例。
3. 两个条件同时满足时，Scheduler 注册唯一周期 Worker；不满足时不查数据库、不注册任务，并确保组件展示兜底。
4. Worker 首次符合调度条件并读库成功后，先提交 MMKV，再发送 `ACTION_HINT_POOL_CHANGED`。Provider 在 `onReceive()` 中刷新 RemoteViews，这才是首次无缓存场景真正显示暗词数据的位置。
5. 后续实例添加不重复注册周期任务；其 `onUpdate()` 直接读取共享 MMKV，因此初始内容与当前共享池一致。
6. 最后一个实例删除时，`onDisabled()` 通知主进程取消周期任务；Worker 每次开始也重新校验实例数量，防止删除与执行撞时后继续读库。
7. 用户后来同意隐私协议：主进程在同意事件中检查实例数量，存在实例才同步门禁并启动调度；不存在实例则不查库。
8. 用户撤回隐私协议：主进程立即取消任务、清空组件池、将派生门禁写为 false，并通知所有搜索工具实例切到兜底。
9. 云配周期改变：只有在双门槛满足时，主进程才更新 `frequency`。频率未变时使用 `KEEP`；频率变化时使用 `UPDATE`，保留旧任务已经过去的计时时间并按新周期判断下一次执行资格，不额外触发立即同步。

隐私 SP 是业务事实源；Scheduler 把“当前允许展示”这一派生结果写入组件 MMKV，并在同意/撤回时发送显式刷新通知。Demo 使用一个 Mock SP boolean 模拟真实协议状态。

WorkManager、Provider 和组件数据访问都在主进程；Provider 仍通过显式通知交给 Scheduler 管理任务，保持生命周期入口单一。

### 7.3 周期约束

- 默认：60 分钟。
- 云配字段：`frequency`，单位分钟。
- 小于 15：按 15 分钟兜底并记录异常配置。
- 非法或缺失：回退 60 分钟。
- 本任务只读本地数据库，不设置网络约束。
- 只有配置值实际变化时才以 `UPDATE` 更新周期任务；相同配置以 `KEEP` 注册，任务不存在时创建、已存在时保留原计时。

### 7.4 Worker 结果

Worker 成功路径：

1. 查询数据库。
2. 将查询结果逐项映射为完整有序 `WidgetHintRecord`，不做额外去重、过滤、排序或归一化。
3. 与 MMKV 旧池比较。
4. 按比较结果写入 MMKV。
5. 需要换池时发送显式 `ACTION_HINT_POOL_CHANGED`。

数据库异常或 MMKV 暗词池写入失败时，不发送刷新通知。失败后的重试策略按产品确认项落地。

## 8. RemoteViews 与 8 秒轮播

### 8.1 布局

组件为 4×2：

- 第一行：占主要宽度的暗词搜索区域 + 搜索按钮。
- 第二行：四个等宽快捷按钮，不横向滚动。

组件内容由一个 `AdapterViewFlipper` 承载。每个集合项包含完整的“暗词区域 + 搜索按钮 + 底部四个按钮”，因此六个入口都能随当前页携带同一个暗词和稳定 `position`，无需向 Launcher 查询其内部当前下标。空池时显示结构相同的静态兜底层。

ProviderInfo 使用 `updatePeriodMillis=0`，因为周期读取由 WorkManager 管理。4×2 的实际 dp 会随 Launcher 网格变化，布局使用弹性宽度和等权底部按钮，并在 MagicOS 10 桌面与负一屏分别验证。

### 8.2 宿主轮播

`AdapterViewFlipper` 设置：

- `android:autoStart=true`
- `android:flipInterval=8000`

RemoteViews 被 Launcher/负一屏宿主加载后，翻页计时器运行在宿主进程。App 主进程不需要每 8 秒主动发送更新。

宿主不可见、熄屏或系统省电时可能暂停动画；恢复可见后继续或重启。Launcher 进程重建后可以从第一条重新开始，本设计不保证跨宿主重建保存轮播位置。

### 8.3 新池主动刷新

默认仅列表变化时执行：

1. Worker 覆盖 MMKV 新池。
2. Worker 向 `SearchToolsWidgetProvider` 发送显式自定义广播，不伪造受保护的系统 `APPWIDGET_UPDATE` 广播。
3. `SearchToolsWidgetProvider.onReceive()` 识别 `ACTION_HINT_POOL_CHANGED`，调用内部 `refreshAll(displayedChild = 0)`；自定义 action 不会自动进入 `onUpdate()`。系统 `ACTION_APPWIDGET_UPDATE` 仍交给 `super.onReceive()`，由框架分发到 `onUpdate()`。
4. Provider 通过 `AppWidgetManager.getAppWidgetIds()` 获取属于 `SearchToolsWidgetProvider` 的全部实例 ID，包括桌面和荣耀负一屏宿主中的实例，但不包含锁屏或其他类型组件。
5. 通知 `AdapterViewFlipper` 数据集变化，使 Factory 的 `onDataSetChanged()` 重新读取 MMKV；RemoteAdapter 的 URI 带当前完整池的派生 hash，使变化池触发重新绑定而不额外持久化版本号。
6. 完整 RemoteViews 显式切换 Flipper/兜底可见性，并把各实例的显示位置定位到第 0 项。

数据集刷新和归零在 MagicOS 10 上必须做真机时序验证。Demo 已同时采用数据变化通知、新 RemoteAdapter 身份和完整 RemoteViews 更新，降低宿主缓存旧集合的概率。宿主自己的 8 秒定时器相位仍可能使不同宿主随后再次分离。

空池时切换到非 Flipper 的兜底布局，展示“暂无推荐内容”并停止轮播。后续非空新池到达时重新显示 Flipper 并从第一条启动。

## 9. 点击与页面路由

### 9.1 点击协议

集合项通过 PendingIntent Template + Fill-in Intent 携带：

- `appWidgetId`
- `action`
- `keyword`
- `position`

非空池下，搜索区和底部四个按钮都位于集合项内，统一使用 PendingIntent Template + Fill-in Intent。任一入口点击后，Router 根据点击项的 `position` 计算下一项，并用完整 RemoteViews 把所有 `SearchToolsWidgetProvider` 实例定位过去，再执行页面导航。这样跳出 Launcher 后重建宿主也不会丢失瞬时 `showNext()`；自动轮播下标仍不写入 MMKV。

### 9.2 搜索区域

点击当前暗词区域：

- 进入搜索激活页。
- 传入当前 `keyword`。
- 传入 `freezeHintRotation=true`。
- App 搜索框显示该暗词，并停止 App 原有暗词轮播。

点击搜索按钮：

- 传入当前 `keyword`。
- 直接进入搜索结果页并展示对应结果。

空池兜底状态没有真实暗词：点击搜索区域进入空白搜索激活页，点击搜索按钮进入空关键词结果页，均不把兜底文案当作关键词。

### 9.3 Router

`WidgetRouterActivity` 属于 `searchtoolswidget.router` 包并运行在主进程，采用透明/无历史中转样式。它先完成组件推进，再通过 ARouter 跳转：

1. 校验 action 与必要参数。
2. 根据 Fill-in Intent 中的稳定 `position` 计算下一位置，调用 `WidgetInstanceUpdater` 让全部搜索工具组件实例定位到该项。
3. 使用共享 `RoutePath` 构造 ARouter Postcard，并通过 `withString/withBoolean` 传递暗词与冻结标记，不直接依赖 Mock 页面类。
4. 启动目标页并结束自身。

Manifest 为 Router 配置透明主题、`noHistory=true`、`excludeFromRecents=true` 和空 `taskAffinity`，不再配置独立进程。Router 只做参数校验、组件推进和 ARouter 路由。

集合 Fill-in Intent 需要可变 PendingIntent Template；Template 必须显式指向本应用 Router，并最小化可填充字段。只有空池兜底层使用按实例构建的 immutable PendingIntent 和唯一 requestCode。

## 10. 单进程初始化与安全

1. `Application.onCreate()` 初始化 ARouter、MMKV、Mock 数据库和调度，所有本应用组件共用默认进程。
2. MMKV 使用固定文件 ID 和 `SINGLE_PROCESS_MODE`，由进程内单例 Store 访问。
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
| App 进程被系统回收 | 宿主需要集合数据或处理点击时重新绑定并拉起 |
| App 被用户强行停止 | 不保证自动恢复，用户再次启动后恢复调度 |
| 云配低于 15 分钟 | 兜底为 15 并记录配置异常 |

## 12. 性能设计

1. 8 秒轮播不产生 App 进程唤醒和周期 Binder 更新。
2. `AdapterViewFlipper` 通过 RemoteViewsFactory 按需生成单项，避免把未知规模的全部 View 一次性塞入 RemoteViews。
3. MMKV 只保存比较与展示需要的轻量记录池；暗词池变化时才刷新宿主。
4. 数据相同时不触发 RemoteViews 更新，避免宿主重绑和动画重置。
5. 数据库查询和序列化在 Worker 后台线程执行。
6. 组件回调避免启动不必要的长任务，小时级工作统一交给 WorkManager。

## 13. 测试计划

### 13.1 JVM 单元测试

- DAO 查询结果不被额外去重、过滤、排序或归一化。
- 相同有序列表判定为不换池。
- 任一记录字段变化、增删元素、顺序变化判定为换池。
- 空池状态转换。
- 读取失败不覆盖旧池。
- `frequency` 缺失、非法、小于 15 和正常值处理。
- Router action 与 extras 生成规则。

### 13.2 Instrumentation/集成测试

- Worker 写 MMKV 后 Provider 刷新可立即读到新池。
- 新池变化后主动刷新并从第 0 条展示。
- 相同池刷新成功但 Flipper 不归零。
- 空池显示兜底并停止轮播；再次有数据时恢复。
- 当前可见暗词的区域和搜索按钮携带正确 keyword。
- 搜索激活页冻结自身暗词轮播。
- 搜索结果页直接接收并展示对应 keyword。
- 六个入口点击后全部搜索工具组件实例各自推进一条。
- 多实例共享池；新池到达后桌面与负一屏实例都回到第 0 条。
- 唯一周期任务不会重复注册；云配变化后周期正确更新。
- 未同意隐私、没有组件实例、撤回隐私三种状态均不继续读库。
- 自定义换池广播由 `onReceive()` 处理；首次系统更新由 `onUpdate()` 提交初始 UI。
- 杀死 App 进程后宿主重新绑定恢复。

### 13.3 MagicOS 10 真机验收

- 桌面 4×2 与荣耀负一屏 4×2 均完整显示，无裁切。
- 可见状态下 8 秒轮播节奏。
- 切桌面页、进入负一屏、熄屏再恢复后的宿主行为。
- 新池主动刷新、立即回到第一条并重新轮播。
- 相同池不重置。
- 点击时 keyword 与屏幕当前文字严格一致。
- 点击推进与自动 8 秒翻页临界时序。
- 桌面与负一屏同时可见时记录轮播相位偏移，确认不影响各自顺序和点击逻辑。
- Launcher 和 App 进程分别被杀后的恢复。
- 100、500、1000 条暗词下的加载耗时、内存和交互稳定性。

## 14. 可观测性

建议记录：

- Worker 开始/结束、调度来源与生效 frequency。
- 数据库读取数量、池是否变化。
- 数据库耗时、MMKV 写入耗时、组件刷新耗时。
- 最近成功刷新时间。
- 重试次数与最终错误分类。
- Provider/Factory 当前进程名和组件实例数量。

禁止在正式日志中输出完整暗词列表或用户搜索行为。

## 15. 与旧锁屏 Demo 的关系

### 15.1 可复用

- `:app + :widget` 多模块边界。
- 独立组件模块和 MMKV Store 抽象。
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

## 17. 注意事项速查

1. **查询只能有一份定义**：搜索 Mock 页面和 Worker 调用同一个 DAO 方法；正式迁移时直接接搜索模块已有查询，组件侧不另写 SQL。
2. **不做组件侧去重**：不按展示文案比较，也不添加 `DISTINCT` 或清洗；新旧池沿用锁屏组件的完整有序记录列表 `newPool == oldPool` 语义。
3. **顺序不能二次加工**：DAO 返回顺序就是轮播顺序；组件不得自行排序。
4. **池用一个 MMKV key 整体覆盖**：不要先删后写，避免另一进程短暂读到人为制造的空池。
5. **成功时间不是请求闸门**：`lastSuccessfulRefreshAt` 只用于诊断，不用来阻止 Worker；唯一周期任务负责防止重复调度。
6. **隐私和实例是双门槛**：未同意隐私只显示兜底；没有任何搜索工具组件实例就不查库；Worker 执行前再次校验。
7. **隐私事实源只有 SP**：Scheduler 将派生门禁写入组件 MMKV；撤回协议时必须取消任务、清池并刷新兜底。
8. **首次 UI 在 `onUpdate()` 设置**：它只负责从 MMKV 渲染缓存或兜底，不直接查数据库；首次读库完成后的真实暗词由自定义广播触发刷新。
9. **自定义广播在 `onReceive()` 处理**：`ACTION_HINT_POOL_CHANGED` 不会自动回调 `onUpdate()`；系统 `APPWIDGET_UPDATE` 才由框架分发给 `onUpdate()`。
10. **“全部实例”范围要准确**：只获取 `SearchToolsWidgetProvider` 的实例 ID，覆盖桌面和荣耀负一屏，不触碰锁屏组件或其他 Provider。
11. **数据更新顺序固定**：先完整提交 MMKV，再发显式广播；写入失败绝不能刷新宿主。
12. **8 秒不交给 WorkManager**：WorkManager 只做分钟级读库；`AdapterViewFlipper` 的 8 秒计时运行在桌面/负一屏宿主。
13. **宿主 Flipper 不是全局时钟**：换池时全部定位第 0 条，点击时全部定位到被点击 `position` 的下一条；晚添加、隐藏暂停和宿主重建造成的后续自动轮播相位漂移属于已接受行为。
14. **Router 运行在主进程并使用 ARouter**：先完成组件推进，再按 `RoutePath` 跳转；Router 必须保持透明、无历史、轻量并与页面类解耦。
15. **包边界不可混写**：搜索 Mock 统一放 `searchmock` 包族，桌面小组件全部放 `searchtoolswidget` 包族。
16. **不要伪造系统更新广播**：应用内部刷新使用显式自定义 action，不发送受保护的 `APPWIDGET_UPDATE`。
17. **空与失败语义不同**：成功空列表要清旧池、显示兜底并停播；读取失败保留旧池且不更新时间。
18. **真机结论优先**：RemoteViews 集合刷新、`setDisplayedChild(0)`、点击与 8 秒临界时序必须在 MagicOS 10 桌面和负一屏分别验证。
19. **单进程 MMKV 模式要一致**：所有入口统一使用 `SINGLE_PROCESS_MODE`，不要在同一文件 ID 上混用多进程模式。

## 18. 实现结论与真机门禁

Demo 已按以下默认结论完成：

1. 多实例不做跨宿主永久严格同步；换池定位第 0 条，点击时全部定位到被点击项的下一条。
2. 相同完整有序列表保持轮播进度。
3. 数据库失败最多补偿 2 次，仍失败则保留旧池并等待后续周期。
4. 成功空池清空旧池，展示“搜索你感兴趣的内容”并停播；点击进入空白搜索激活页。
5. 点击后不要求所有宿主重新计算统一的完整 8 秒间隔。
6. 隐私撤回立即取消任务、清空组件缓存并刷新兜底。
7. 云配默认 60 分钟、最低 15 分钟，本 Demo 不设业务上限。

仍必须在 MagicOS 10 真机确认：荣耀负一屏是否完整复用标准 AppWidget 生命周期，以及集合刷新、归零、8 秒轮播、点击临界时序和 4×2 尺寸是否符合宿主实际行为。

