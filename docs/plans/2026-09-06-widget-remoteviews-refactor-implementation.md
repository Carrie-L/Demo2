# Search Tools Widget RemoteViews Refactor Implementation Plan

> 当前点击接入以 [主 App 入口方案](2026-09-14-widget-main-entry.md) 为准，本文中转 Activity 步骤不再执行。

> 历史执行记录，不再据此实现“来源实例隔离 / partial showNext”。2026-09-11 已改为全部实例
> 完整更新 + 单次 showNext，原因与当前实现见 [点击链路修复说明](2026-09-11-widget-click-fixes.md)。

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the search tools widget into a documented, instance-isolated implementation that keeps host-driven 8-second rotation, uses `showNext()` for click advancement, and selects the native API 31+ collection path or the API 29–30 service path without a new AndroidX RemoteViews dependency.

**Architecture:** The root RemoteViews owns static tool buttons while each collection item owns only the current hint and search button. A process-session registry prevents redundant `onUpdate()` rendering, click routing advances only the source `appWidgetId`, and data replacement remains the only operation that resets all instances to child zero.

**Tech Stack:** Kotlin, XML RemoteViews, AdapterViewFlipper, RemoteViews.RemoteCollectionItems, RemoteViewsService/Factory, MMKV, WorkManager, ARouter, JUnit4

---

### Task 1: Define and test platform and render-session policies

**Files:**
- Create: `widget/src/test/java/com/carrie/demo/searchtoolswidget/provider/WidgetCollectionModeTest.kt`
- Create: `widget/src/test/java/com/carrie/demo/searchtoolswidget/provider/WidgetRenderSessionTest.kt`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/WidgetCollectionMode.kt`
- Create: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/WidgetRenderSession.kt`

**Steps:**
1. Write tests proving SDK 29/30 selects the service-backed collection and SDK 31+ selects inline collection items.
2. Run the tests and verify they fail because the policy does not exist.
3. Implement the smallest documented collection mode policy.
4. Write tests proving a widget ID renders once per process session, a new ID renders independently, and deletion permits reinitialization.
5. Run the tests and verify they fail because the render session does not exist.
6. Implement a synchronized in-memory render-session registry with Chinese KDoc.
7. Run both tests and verify they pass.

### Task 2: Test and implement click payload separation

**Files:**
- Modify: `widget/src/test/java/com/carrie/demo/searchtoolswidget/router/WidgetRouteTest.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/router/WidgetClickContract.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/router/WidgetPendingIntents.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/router/WidgetRouterActivity.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/WidgetInstanceUpdater.kt`
- Delete: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/HintPositionPolicy.kt`
- Delete: `widget/src/test/java/com/carrie/demo/searchtoolswidget/provider/HintPositionPolicyTest.kt`

**Steps:**
1. Update tests to require no hint position and no tool keyword.
2. Run the route test and verify the old position-based contract fails.
3. Remove the position extra and route only the current keyword for search actions.
4. Replace `advanceAll()` with `advanceOne(appWidgetId)`, implemented as a partial `RemoteViews.showNext()` command.
5. Make `WidgetRouterActivity` read `EXTRA_APPWIDGET_ID`, advance only that instance, then navigate through ARouter.
6. Remove the obsolete absolute-position policy and run widget tests.

### Task 3: Refactor widget layouts and versioned collection binding

**Files:**
- Modify: `widget/src/main/res/layout/widget_search_tools.xml`
- Modify: `widget/src/main/res/layout/widget_hint_item.xml`
- Create: `widget/src/main/res/anim/widget_no_transition.xml`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/WidgetRemoteViewsRenderer.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/remote/HintRemoteViewsFactory.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/remote/HintRemoteViewsService.kt`

**Steps:**
1. Move the four tool buttons out of the item and into the static root layout.
2. Limit each item to the hint field and search button.
3. Assign an explicit zero-duration, constant-alpha animation to the Flipper.
4. Add an API 31+ inline collection builder using `RemoteViews.RemoteCollectionItems`.
5. Keep `RemoteViewsService/Factory`, Adapter Intent, per-widget URI, and `poolToken` only for SDK 29–30.
6. Bind search item fill-in intents with action and keyword only; bind static tools with normal per-instance PendingIntents.
7. Build resources and run widget unit tests.

### Task 4: Protect `onUpdate()` and global resets

**Files:**
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/SearchToolsWidgetProvider.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/WidgetInstanceUpdater.kt`
- Modify: `widget/src/main/java/com/carrie/demo/searchtoolswidget/provider/WidgetBroadcasts.kt`

**Steps:**
1. Initialize only widget IDs not yet rendered in the current process session.
2. Remove deleted IDs from the session and clear it when the final instance is removed.
3. Keep pool-change handling global: reload data and explicitly display child zero for every instance.
4. Ensure privacy and empty-pool rendering stops the Flipper and displays the fallback row.
5. Run unit tests and assemble the debug APK.

### Task 5: Document the full widget and WorkManager flow

**Files:**
- Modify: every Kotlin source under `widget/src/main/java/com/carrie/demo/searchtoolswidget/`
- Modify: every Kotlin source under `app/src/main/java/com/carrie/demo/searchtoolswidget/sync/`
- Modify: `app/src/main/java/com/carrie/demo/DemoApplication.kt`
- Modify: relevant widget XML under `widget/src/main/res/`
- Modify: `README.md`

**Steps:**
1. Add Chinese KDoc to every widget-related class, object, enum, data class, method, and constant.
2. Explain every system callback, API-level branch, PendingIntent boundary, MMKV snapshot decision, and multi-instance update scope.
3. Add teaching comments to WorkManager scheduling: periodic versus immediate work, unique names, replacement policies, retry behavior, minimum interval, and lifecycle gates.
4. Rename stale or misleading methods, including names that still imply a separate process.
5. Update README with end-to-end lifecycle and click flows.
6. Scan for undocumented widget declarations and stale all-instance click logic.

### Task 6: Verify behavior

**Files:**
- Test: all widget and app unit tests
- Verify: merged manifest and debug APK

**Steps:**
1. Run `gradlew testDebugUnitTest lintDebug assembleDebug --rerun-tasks` and require success.
2. Install on API 35 x86_64 emulator.
3. Verify the hint changes without a visible whole-widget fade.
4. Add or retain two widget instances and verify clicking one advances only that instance.
5. Verify hint click, search click, and all four tool routes.
6. Verify pool replacement resets every instance to the first new item.
7. Inspect logs for crashes, RemoteViews inflation failures, or ARouter misses.
