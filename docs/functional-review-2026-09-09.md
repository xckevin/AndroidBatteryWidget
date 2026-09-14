# Battery Widget 功能复核与改进顺序

复核对象：当前 `feature/upgrade_sdk` 工作区（3.2 / versionCode 32）。本次只读审查应用代码、布局、既有 API 36 验证记录，并对照 Android 官方接口说明；没有新增真机测试，没有修改应用代码或提交 Google Play 审核。蓝牙硬件与不同厂商桌面的表现仍需实机验收。

建议把下一轮的重点放在“可靠添加、真实监控状态、可信电量、可选择设备”。当前已经有手机电量 Widget、蓝牙设备 Widget、外观预览、深浅色背景、前台事件监控、亮屏补查、开机恢复和 WorkManager 兜底，不需要再将这些已有能力作为新增功能。

## 应优先修复的现有流程

| 优先级 | 发现与用户影响 | 代码依据 | 验收方式 |
| --- | --- | --- | --- |
| P0 | 从系统桌面添加手机 Widget 的配置闭环缺失。配置页没有接收 Widget ID，也没有向桌面返回配置成功；保存只写全局配置。符合标准协议的桌面不能据此完成添加。代码缺失已确认，具体桌面表现待复现。 | `app/src/main/res/xml/battery_widget_info.xml:11`；`activity/MainActivity.java:10`；`activity/fragment/phone/BatteryWidgetConfigFragment.java:198` | 分别从桌面组件列表和应用内添加；保存后只产生一个可用 Widget；取消不添加；不支持应用内固定的桌面展示手动添加指引。 |
| P1 | “持续监测已开启”与实际运行状态不一致。通知权限或通知渠道被关闭时服务会停止，但开关仍只读取原来的用户偏好。用户可能一直以为实时监控在工作。 | `activity/SettingsActivity.java:82`；`service/WidgetUpdateService.java:117`、`:136`；`utils/NotificationUtil.java:23` | 关闭通知权限、关闭通知渠道、恢复权限时，界面准确显示正在运行/已降级/未启用及恢复入口，不能仅依据开关判断服务健康。 |
| P1 | 蓝牙页面没有完整订阅连接和电量变化。主要在进入页面时重建列表；BLE 回调只修改已有行的电量，普通蓝牙电量广播、断开和重连没有对应的页面列表更新。 | `activity/fragment/BtDeviceFragment.kt:49`、`:109`、`:148`；`repo/BatteryRepo.kt:343` | 停留在蓝牙页连接、断开、关闭蓝牙；无需退出重进即可更新列表、状态和电量；与桌面 Widget 保持一致。 |
| P1 | 预览修改直接改动共享配置对象。“未保存”的颜色等修改可被下一次 Widget 刷新使用，但进程重启后恢复旧值，保存语义不一致。 | `utils/BatteryWidgetPrefHelper.java:32`；`activity/fragment/phone/BatteryWidgetConfigFragment.java:98`、`:152`、`:198` | 编辑使用草稿副本；取消/返回保留原配置，保存后立即生效且重启不回退，重置也遵循相同规则。 |
| P1 | 插电状态被当作充电状态。仓库读取了 `EXTRA_STATUS` 但没有保留到模型；绘图只根据 USB/AC/无线供电决定显示闪电。插电但暂停充电时会造成误导。手机两种电量读取都失败时还会回退为 0%。 | `repo/BatteryRepo.kt:55`、`:64`；`model/PhoneBatteryState.java:5`；`utils/Utils.java:65` | 区分充电中、已充满、已连接电源但未充电、放电、未知；无有效读数显示未知，不显示虚假的 0%。 |

Widget 配置协议要求返回对应的 `EXTRA_APPWIDGET_ID` 与成功结果，并由配置页负责首次更新。当前问题同时经独立代码复核确认。[Android 配置说明](https://developer.android.com/develop/ui/views/appwidgets/configuration)、[官方 Views 配置示例](https://github.com/android/user-interface-samples/blob/main/AppWidget/app/src/main/java/com/example/android/appwidget/rv/list/ListWidgetConfigureActivity.kt)。

供电来源与充电状态是不同的信息，Android 分别提供 `EXTRA_PLUGGED` 与 `EXTRA_STATUS`。[BatteryManager 文档](https://developer.android.com/reference/android/os/BatteryManager)。

## 值得增加的功能

| 顺序 | 功能 | 推荐的最小范围 | 当前缺口与收益 |
| --- | --- | --- | --- |
| 1 | 首页监控状态与“更新不及时”诊断 | 将“状态栏显示”改为清楚的“持续监测”；首页显示实际运行状态、最近检查、通知/蓝牙权限状态；提供立即刷新和恢复入口。用户主动反馈时可附带不含设备地址的诊断摘要。 | 关键开关藏在“高级”，运行信息只有调试 dump。解决用户不知道为什么没有更新的问题。 |
| 2 | 蓝牙设备选择、固定与排序 | 关注指定耳机/手表；隐藏不关心的设备；自定义别名和顺序；支持只显示已连接，或保留离线设备并明确标记。 | 当前 Widget 展示全部已连接设备，排序随连接状态/名称变化，无法固定一只耳机。选择控件虽然有代码，但布局是 invisible，功能未完成。 |
| 3 | 数据新鲜度和异常状态 | 分别记录最近尝试、最近成功读取；区分未授权、未连接、设备不提供电量、读取失败、缓存过期。短暂失败可保留旧值并标注，持续失败转为未知。 | 当前蓝牙模型只有设备、百分比和连接布尔值；GATT 缓存没有成功读取时间和过期展示策略，未知只显示“-”，空列表只显示“无数据”。 |
| 4 | 每个 Widget 独立配置与直达操作 | 按 Widget ID 保存样式、设备集合、点击行为；点击打开对应设备/监控状态；小尺寸单设备，大尺寸可同时显示手机与多个设备。 | 当前手机与蓝牙 Widget 共享一份外观配置。没有点击 PendingIntent；蓝牙使用 StackView 翻看，缺少并排总览。 |
| 5 | 低电量和充电阈值提醒 | 优先做指定蓝牙设备低电量提醒；手机可选 80%/100% 提醒、静音时段、每个充电周期只提醒一次。默认关闭。 | 当前只有持续状态通知及停止按钮，没有用户自定义阈值。提醒必须依赖新鲜数据，不能拿陈旧缓存反复报警。 |
| 6 | 轻量电量详情 | 显示设备支持的温度、充电状态、预计充满时间；可选本地最近 24 小时电量曲线，明确数据缺口。 | 当前主要只有百分比。预计时间属于估计，接口不支持/数据不足时显示不可用；不承诺所有设备都有准确容量或健康度百分比。 |
| 7 | 隐私控制 | 提供分析与崩溃报告开关、隐私政策入口、删除请求需要的安装标识及说明；实施后同步商店声明。 | 当前应用没有收集开关，也不展示定位某次安装所需的标识。能让已公开的删除请求流程更可操作。 |

预计充满时间可使用设备提供的估计，但 Android 明确允许返回不可用。[BatteryManager.computeChargeTimeRemaining](https://developer.android.com/reference/android/os/BatteryManager#computeChargeTimeRemaining())。

外观优化建议包含系统动态配色、大字适配和蓝牙 Widget 的无障碍描述；手机 Widget 已有电量 contentDescription。当前图形统一绘制为 600×600 位图，拉伸 Widget 不会自动增加内容密度。响应式布局应结合具体尺寸设计，而不是只放大图片。Android 12 起可采用系统动态配色。[Android Widget 增强说明](https://developer.android.com/develop/ui/views/appwidgets/enhance)。

## 更新及时性的实施判断

- 保留 FreeReflection，延续已有事件驱动监控。它读取的是系统记录的设备状态，不保证外设刚刚上报了新数据。
- 当前已实现亮屏每 60 秒补查和 15 分钟 WorkManager 兜底；本轮不建议再叠加一套重试定时循环。
- `lastRefreshElapsedRealtime` 当前在发出 Widget 更新广播后记录，只能说明刷新被请求，不能证明蓝牙 GATT 已读取成功，更不能证明 Launcher 已渲染完成。必须分开记录请求、取值和展示阶段。
- 页面、通知与 Widget 应消费一致的电量状态；相同状态可以跳过位图重绘，同时保留检查成功时间。这样才便于测量及时性与耗电的取舍。
- 外设没有提供新电量时，界面应如实显示来源和新鲜度。不能把每次反射读取系统缓存的时间标成“设备刚更新”。

相关依据：`service/WidgetUpdateService.java:61`、`:141`；`receiver/BatteryWorker.kt:14`；`repo/BatteryRepo.kt:119`、`:314`；`service/BtWidgetService.kt:49`。上述 Java/Kotlin 简写路径均位于 `app/src/main/java/com/github/xckevin927/android/battery/widget/`。

## 推荐迭代边界

第一轮完成上表 P0/P1 流程修复，并提供基础监控状态。验收优先覆盖桌面添加、权限关闭后的降级、应用前台蓝牙断连、插电但未充电、未保存配置取消，以及进程恢复后的状态一致性。

第二轮增加蓝牙选择/固定、数据新鲜度和每个 Widget 独立配置。这些能力最贴近应用的核心使用场景。

第三轮再增加提醒和详情曲线。真实蓝牙、Pixel/三星及至少一种限制后台较多的厂商系统应作为设备验收对象；既有模拟器电量事件测试不能代替这些验证。

本次不会改变已准备的发布制品；如果采纳修复，应重新构建、验证并按 Google Play 版本规则准备新制品。
