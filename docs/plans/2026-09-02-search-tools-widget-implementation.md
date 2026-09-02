# 搜索工具桌面与负一屏小组件实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 创建一个可由 Android Studio 直接打开运行的 Kotlin + XML Demo，实现搜索 Mock 数据库、MMKV 跨进程暗词池、WorkManager 周期同步以及桌面/荣耀负一屏 4×2 搜索工具组件。

**Architecture:** 工程采用 `:app + :widget` 双模块。`:app` 提供 Mock 搜索域、隐私 SP、SQLite DAO、页面和主进程 Worker；`:widget` 提供 `:widgetProvider` 进程中的 Provider、RemoteViewsService、MMKV Store 和 Router。多个实例共享池，新池到达时统一回到第一条；每个宿主随后独立执行 8 秒 Flipper 计时，不保证永久严格同步。

**Tech Stack:** Android API 29–36、Kotlin、XML/RemoteViews、SQLiteOpenHelper、WorkManager 2.11.2、MMKV 2.4.1、Gson、JUnit4。

---

### Task 1：工程骨架与构建基线

**Files:**

- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/*`
- Create: `app/build.gradle.kts`
- Create: `widget/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `widget/src/main/AndroidManifest.xml`

**Steps:**

1. 创建 `:app + :widget` Kotlin/XML 工程，配置 `minSdk 29`、`compileSdk 36`、`targetSdk 36`。
2. 配置 WorkManager、MMKV、Gson、AppCompat 与测试依赖。
3. 使用 JDK 17 运行 `./gradlew projects`，确认两个模块均被识别。
4. 提交工程骨架。

### Task 2：暗词池纯逻辑（TDD）

**Files:**

- Test: `widget/src/test/java/com/carrie/demo/searchtoolswidget/domain/HintPoolDecisionTest.kt`
- Test: `app/src/test/java/com/carrie/demo/searchtoolswidget/sync/FrequencyPolicyTest.kt`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/model/WidgetHintRecord.kt`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/domain/HintPoolDecision.kt`
- Create: `app/src/main/java/com/carrie/demo/searchtoolswidget/sync/FrequencyPolicy.kt`

**Steps:**

1. 先写列表完全相同、字段变化、顺序变化、空池和失败语义测试。
2. 运行目标单测，确认因实现缺失而失败。
3. 实现 `newPool == oldPool` 的完整有序记录比较，不做去重或清洗。
4. 先写 `frequency` 缺失、低于 15、正常值测试并确认失败，再实现钳制逻辑。
5. 运行两个模块单测并提交。

### Task 3：搜索 Mock 数据库与页面

**Files:**

- Create: `app/src/main/java/com/carrie/demo/searchmock/data/*`
- Create: `app/src/main/java/com/carrie/demo/searchmock/ui/*`
- Create: `app/src/main/res/layout/activity_*.xml`
- Test: `app/src/test/java/com/carrie/demo/searchmock/domain/SearchLaunchStateTest.kt`

**Steps:**

1. 先测试“组件带词进入时冻结 App 暗词轮播；普通进入时允许轮播”的页面状态决策。
2. 实现 SQLite 表与唯一 `SearchHintDao.queryHints()`；搜索页和 Worker 必须调用同一方法。
3. 实现隐私同意/撤回、Mock 数据同值/换值/清空、云配周期切换入口。
4. 实现搜索激活页、搜索结果页和四个独立快捷页。
5. 运行单测与资源构建并提交。

### Task 4：MMKV 快照与同步引擎（TDD）

**Files:**

- Test: `widget/src/test/java/com/carrie/demo/searchtoolswidget/storage/HintPoolCodecTest.kt`
- Test: `app/src/test/java/com/carrie/demo/searchtoolswidget/sync/HintSyncEngineTest.kt`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/storage/*`
- Create: `app/src/main/java/com/carrie/demo/searchtoolswidget/sync/HintSyncEngine.kt`

**Steps:**

1. 先写记录池 JSON 往返、空池和坏 JSON 测试并确认失败。
2. 实现 Gson Codec 和 MMKV 多进程 Store；暗词池使用单 key 整体覆盖。
3. 先写成功相同、成功变化、成功空、数据库失败的同步引擎测试并确认失败。
4. 实现同步引擎，只有变化池才要求刷新；成功时间独立更新。
5. 运行单测并提交。

### Task 5：隐私/实例双门槛与 WorkManager

**Files:**

- Test: `app/src/test/java/com/carrie/demo/searchtoolswidget/sync/WidgetEligibilityTest.kt`
- Create: `app/src/main/java/com/carrie/demo/searchtoolswidget/sync/WidgetHintSyncWorker.kt`
- Create: `app/src/main/java/com/carrie/demo/searchtoolswidget/sync/WidgetHintSyncScheduler.kt`
- Create: `app/src/main/java/com/carrie/demo/searchtoolswidget/sync/WidgetScheduleReceiver.kt`
- Create: `app/src/main/java/com/carrie/demo/DemoApplication.kt`

**Steps:**

1. 先测试“隐私同意且实例数大于 0”才允许读取的门槛。
2. 实现唯一周期任务，默认 60 分钟、最低 15 分钟。
3. Worker 执行前重新校验双门槛，调用同一 DAO，写入成功后才发显式刷新广播。
4. 撤回隐私时取消任务、清池、派生门禁置 false 并刷新兜底；无实例时取消任务但保留合法缓存。
5. 数据库异常最多退避补偿两次，随后以本轮无更新结束，避免周期任务永久失败。
6. 运行单测并提交。

### Task 6：RemoteViews 小组件与路由

**Files:**

- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/*`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/remote/*`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/router/*`
- Create: `widget/src/main/res/layout/widget_search_tools.xml`
- Create: `widget/src/main/res/layout/widget_hint_item.xml`
- Create: `widget/src/main/res/xml/search_tools_widget_info.xml`
- Test: `widget/src/test/java/com/carrie/demo/searchtoolswidget/router/WidgetRouteTest.kt`

**Steps:**

1. 先写六种路由动作、keyword/freeze 参数和空池语义测试并确认失败。
2. 实现 `AdapterViewFlipper + RemoteViewsService/Factory`，8 秒宿主自动轮播。
3. Provider 的 `onUpdate()` 渲染首次 UI；自定义换池 action 在 `onReceive()` 中处理并刷新本 Provider 的全部实例。
4. Router 运行在 `:widgetProvider`；集合整页携带稳定 `position`，点击时先把全部搜索工具实例定位到下一项，再显式跳转主进程页面。
5. 配置 Provider、RemoteViewsService、Router 的进程、权限、导出和 PendingIntent 标志。
6. 运行单测、Manifest 合并与 Debug 构建并提交。

### Task 7：验收、说明与推送

**Files:**

- Modify: `docs/plans/2026-09-02-search-tools-app-widget-design.md`
- Create: `README.md`

**Steps:**

1. 将 P0 更新为“宿主独立计时，不要求永久严格同步”的已确认结论。
2. 写明 Android Studio 打开、隐私开关、Mock 换池、添加组件与验证跳转方法。
3. 运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug` 和可用设备检查。
4. 检查 Git diff、敏感信息和未跟踪构建产物。
5. 合入 `main` 并推送 GitHub。
