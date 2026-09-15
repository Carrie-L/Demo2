# Widget 广播点击链路实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** widget 自己接收 HINT_WORD_NEXT、推进全部实例，再用真实 deeplink 和 keyword 打开主 App。

**Architecture:** 复用 SearchToolsWidgetProvider.onReceive。PendingIntent 使用 getBroadcast；Provider 调用 widget 内的推进和启动方法。主 App 只 Mock 通用 LauncherActivity + ARouter URI 路由，不处理 WidgetAction、不调用 advanceAll、无点击队列。

**Tech Stack:** Kotlin、AppWidgetProvider、RemoteViews、MMKV；ARouter 仅存在于 app。

**Spec:** 本会话 2026-09-15 用户已确认的广播点击方案。

## Global Constraints

- minSdk 29；API 29～30 保留 RemoteViewsService，API 31+ 使用原生 RemoteCollectionItems。
- 点击只传真实 data URI 和可选 extra `keyword`，不传 appWidgetId 或业务 action。
- 系统更新 API 仍查询全部实例 ID；保留宿主 8 秒轮播及完整 RemoteViews + showNext。
- 不引用 LauncherActivity::class.java；用 PackageManager 查询本包入口 component。
- 不用 requireNotNull/!! 处理外部输入；捕获可恢复异常，推进失败不阻断导航。
- 用户要求写完立即推送，不等待完整测试成功。仅如实报告验证结果。

## Task 1: 广播及 URI 协议

Files: widget/router/WidgetPendingIntents.kt、WidgetAction.kt、WidgetClickContract.kt，widget/provider/SearchToolsWidgetProvider.kt、WidgetBroadcasts.kt，widget/remote/WidgetHintItemRemoteViews.kt，widget/provider/WidgetRemoteViewsRenderer.kt。

- [x] 先运行 WidgetBroadcastNavigationTest.buttonsSendBroadcastInsteadOfOpeningActivity，确认旧 getActivity 链路失败。
- [x] 使用 `PendingIntent.getBroadcast(context, requestCode, intent, flags)`；模板 data 留空，Fill-in 提供真实 URI。
- [x] Provider 的 HINT_WORD_NEXT 分支读取 URI/keyword，推进一次，再调用 widget 内启动方法，不再次发推进广播。
- [x] 新 Intent 使用查询得到的入口 component、ACTION_VIEW、真实 URI，以及 NEW_TASK | CLEAR_TOP | SINGLE_TOP。
- [x] 查询失败安全返回；推进和启动异常分别记录，不输出暗词。

## Task 2: 主 App 只保留通用路由 Mock

Files: app/searchmock/ui/LauncherActivity.kt、app/searchmock/navigation/RoutePath.kt、SearchNavigationContract.kt，搜索页面及 AndroidManifest.xml。

- [x] 移除主模块里的 WidgetRoute、MainWidgetNavigation、WidgetNavigationQueue 及旧测试。
- [x] Launcher 冷/热入口均接收新 Intent；通用实现 `ARouter.getInstance().build(uri).withString("keyword", keyword).navigation(this, callback)`，无按钮判断。
- [x] keyword 存在时由搜索页面自己冻结暗词轮播；主入口不判断搜索业务。
- [x] 用协议测试覆盖广播类型、六个真实 URI、当前 keyword、无 URI 时仅推进、输入异常和连续点击。

## Task 3: 清理文档并立即推送

- [x] 更新 README 和现行方案；记录广播启动缺少增强过渡动画、Logo 要等 Activity 启动、荣耀真实宿主仍需验证。
- [x] 静态核查、编译检查后提交当前实现；不以完整设备回归作为 push 前置条件。
- [ ] 直接推送 origin/main，核对远端提交；明确标记未执行的测试，不冒称完整验证通过。


## 提交前记录

已在旧代码运行最小 RED 测试：getActivity 不满足广播契约，1 项预期失败。改造后生产 APK 和 androidTest APK 均编译成功，git diff --check 无错误。完整设备回归未运行，按用户要求先推送。
