# OPPO widget 修复 — 2026-09-11

## 交付范围

本轮实现三项修复：相同有效电量超过 30 分钟误显示未知、桌面设备点击未定位对应卡片、组合 widget 左右卡片大小不一致。

- `FrameworkBatteryReading` 增加 `validatedAt`，有效系统查询（含相同电量）刷新显示有效期；`successAt` 仍只在首次、数值变化或明确上报时更新。持续得到相同值时显示为缓存，不伪装成新上报；读取失败不延长有效期，断开/权限撤回仍由仓库清理状态。
- `TabActivity` 使用 widget 实际发送的 `WidgetConstants.EXTRA_DEVICE_ADDRESS`，替换不匹配的字面量；有目标地址时路由到蓝牙页并刷新。`BtDeviceFragment` 定位目标后滚动真正承载页面的 `NestedScrollView`，而非只滚动内部展开的 RecyclerView。
- 组合 widget 恰好显示一个设备时，使用独立 `widget_single_device_area` / `widget_single_device_indicator`，与手机区域采用相同 InnerView、match_parent 和 centerInside 缩放。此分支绕开 GridView 的自动列数和 wrap-content 高度，横向、纵向均保证容器等分与位图等大。设备筛选由 `BtWidgetService.getWidgetItems()` 共享，避免单项/集合规则分歧；零项保留空态，多项及 devices-only 继续集合。`bt_widget_img.xml` 同时移除重复内边距。耳机、手表及未知电量均使用真实状态，点击单设备直接携带对应地址跳转。

## 构建和回归

使用现有 AGP/Gradle 配置、Android Studio JBR、离线缓存和已验证的 HiddenApiBypass 6.1 依赖仓库：

- 正常包 `testDebugUnitTest`：41 项，0 失败、0 错误、0 跳过。
- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 成功；lint 0 错误、224 警告。
- 新增/更新的时钟测试覆盖模拟两小时反复查询相同值、失效后的同值恢复、读取失败不续期、非法值/时钟倒退，以及缓存显示不会被低电量提醒当成新鲜数据。模拟时钟测试不等于真实待机两小时。
- 新导航 instrumentation 测试覆盖冷/热 intent 路由，另有显式开启的 `LiveWidgetNavigationRegressionTest` 验证真实首屏外设备卡片进入外层视口；新 QA 实机缓存测试仅老化实际读数的内存时间戳，不改变真实电量或手机时钟。解锁后的实机结果见下文：缓存 1 项与导航 3 项全部通过。
- 涉及文件 `git diff --check` 通过。

## 安装与现场状态

已成功覆盖安装正常调试包和独立 QA 包，版本仍为 3.3 / versionCode 33；本轮没有上架或提交送审。

- 正常 APK SHA256：`193434a1c1bcf027ef23007e698af92d8c6c08fffe7996c4c5d6cdaf62dd1ceb`
- QA APK SHA256：`e4f66a1cfe4a4b5e0b25fc964d03900f4f07d543de5f0a5a27ce99c8bb02c83b`
- QA 运行源码除 manifest 和包名保护的 ProBilling 测试实现外，与正常源码一致。真实购买/恢复状态未修改。
- 两个包的组件、设备偏好和提醒偏好均逐键保留；正常桌面 ID 7/8 和 QA 组合 ID 18 仍绑定 OPPO 桌面。

## 解锁后实机验收结果

当前约 4×2 横向组合已验证三项修复。正常与 QA 运行包仍为上列 SHA256，本次恢复测试仅调整了两份 instrumentation 测试的启动方式，没有再改运行代码。

| 检查 | 结果与证据 |
| --- | --- |
| 手机＋OPPO 耳机等大对齐 | 实际桌面截图 `fixed-phone-earbud-home.png`、`final-phone-earbud-restored.png` 已视觉核对。最终两张 ImageView 均为 **409×584 px**、中心 y=499；同为 600×600 位图经 centerInside 后等大。原蓝牙 y=506 偏移已消失。见 `desktop-alignment.json`。 |
| 手机＋华为手表等大对齐 | 在 ID 18 的真实配置页只选手表并保存，桌面两区中心均 y=499，截图 `fixed-phone-watch-home.png` 已视觉核对。手表真实电量仍不可用，显示“— / 未知”，未生成假读数。 |
| 桌面耳机点击 | 真实触摸进入蓝牙页，目标 OPPO 名称在视口上部 y=611；热启动与 QA 普通进程被回收后的重新启动均通过。后者实际 PID 9863 → 无进程 → 27109，见 `ordinary-cold-widget-process.json`、`fixed-earbud-ordinary-cold.json`。 |
| 桌面手表点击 | 真实触摸定位到 HUAWEI 手表卡片，见 `fixed-watch-click.json`；未知电量不会阻碍定位。 |
| 真实电量过期边界 | `LiveFrameworkCacheRegressionTest` **1 项通过，0.101 秒**。将真实系统读数的内存时间戳老化到超过 30 分钟后，仓库查询恢复显示真实 100%，状态为 cached，原 report 时间未刷新，提醒引擎不把缓存当新上报。见 `live-framework-cache-result.txt`。 |
| 导航自动回归 | 冷/热 namespaced intent 路由与真实首屏外设备进入外层视口共 **3 项通过，6.243 秒**，见 `live-navigation-results.txt`。 |

缓存测试只加速时间戳，不修改真实电量或设备时钟；先前 JVM 两小时模拟也不等于本次真实长期待机。

### 测试环境处理与边界

首次锁屏期间测试 APK 安装返回 `-99`。解锁后出现 OPPO 安装确认，点击“继续安装”后成功。第一轮组合 instrumentation 被 OPPO 的跨应用启动确认及 ActivityScenario 的 EmptyActivity 辅助页面阻塞；该轮被主动中止，不能记为通过。仅本次允许了测试启动。随后将两份导航测试改为 `Instrumentation.startActivitySync` 直接启动真实目标 Activity，保留正常 Intent/lifecycle 和实际视口断言；重新构建测试 APK 后，分开运行的 1＋3 项全通过。

另做了一次 force-stop 实验：之后首次点组件打开了概览，不能算设备定位通过。Android 15 起强制停止会取消 PendingIntent 并暂时停用组件；这与普通进程回收不同，依据 [Android 官方行为说明](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state)。因此另以 QA 自身 UID 回收进程（不设置 stopped state）后，实际点击桌面耳机完成了上述冷进程定位验证。

两次实际缩放拖动后，OPPO 的组件尺寸都未变化，因此 **纵向实机布局未验证**；`watch-vertical-resize-attempt.json` 和 `watch-narrow-resize-attempt.json` 仅为尝试记录。零/多设备模式及更宽尺寸未在本轮重新做视觉验证。本轮不扩展为长期待机测试，也不声称华为手表电量读取已解决。

## 恢复与交付

- 已在真实配置页恢复 ID 18 为“手机＋OPPO Enco Free4”，仍为原约 4×2，最终截图已核对并留在 QA 组件所在桌面页。
- 正常包和 QA 包的 `BATTERY_PREF`、`battery_alerts`、`bluetooth_device_preferences` 均与修复前逐键一致；正常 ID 7/8 和 QA ID 18 均保留。证据：`preservation-after-resumed-tests.json`。
- 定时触摸实际执行 **136 次**；系统确认和缩放浮层期间暂停，普通页面约每 8 秒触摸已核对空白位置。所有临时触摸进程和标记均已停止/移除。
- `stay_on_while_plugged_in` 恢复并回读为原值 **0**。临时 QA 测试 APK 已卸载；QA 主应用与组合组件保留供查看。未更改真实购买权益、蓝牙配对或通知权限。
- 结构化总记录：`resumed-validation.json`。源码、构建和私有现场证据位于 `play/artifacts/3.3-33/oppo-widget-fixes-2026-09-11/`，保持忽略与受限文件权限。
