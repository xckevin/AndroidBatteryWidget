# OPPO 蓝牙与 Pro 验证 — 2026-09-11

设备为 OPPO PKT110 / Android 16 / ColorOS 16.1，原桌面手机组件 ID 7、蓝牙组件 ID 8 保留。用户确认华为手表不能读取电量是已知限制，后续范围转为 Pro 蓝牙功能。

## 普通包与真实蓝牙

已保留数据安装 [HiddenApiBypass 6.1 迁移包](hidden-api-bypass-migration-2026-09-11.md)，正常包名、33/3.3、minSdk 21 / targetSdk 36。

- 隐藏 API 初始化成功：`reflectionBackend=HiddenApiBypass`、`hiddenApiAccessEnabled=true`。
- OPPO Enco Free4：框架直接读取与 `ReflectUtil` 均返回连接 true、电量 100；App 显示已连接 100%，系统蓝牙页显示左右耳均为 100%。
- 华为手表：框架直接读取与 `ReflectUtil` 均返回连接 true、电量 -1；系统页显示已连接但无电量。普通 App GATT 读取 15 秒后关闭连接，UI 显示暂无电量读数及设备未响应。用户确认该设备电量不可读，未再扩展兼容修复。
- 显式启用的 `LiveBluetoothDiagnosticsTest` 完成 1 项。它只证明诊断完成；上述实际值及界面对照才是连接和读数证据。

## 普通包未解锁状态

新增 `LiveBluetoothProTest`，真实读取配对设备与 App 权益，使用独立测试配置 ID 91073。最终 **3 项通过、无跳过**：

1. 在实际提醒表单开启蓝牙提醒、选择已连接且电量有效的耳机并保存；未解锁时保存值不变，提醒执行入口保持关闭。
2. 在实际组件编辑器选择组合样式和同一真实耳机并保存；未解锁时引导到 Pro，原独立测试配置的样式和设备选择不变。
3. 对真实快照运行提醒状态机，确认已连接的 100% 耳机和电量未知设备均不产生低电量候选。此项未投递通知，不代表真实通知到达或去重已经验证。

每项保存和恢复 `BATTERY_PREF`、`battery_alerts`、`bluetooth_device_preferences`；测试后独立归档比较，三份业务偏好的所有键值均一致。正常提醒页面允许创建其标准通知渠道，未更改通知授权；测试使用全天免打扰基线避免意外提醒。原组件 ID 7/8 仍属于 OPPO 桌面，未重新添加或删除。

本机测试结束页面后曾返回 Play，后台进程出现执行停顿；将正常 App 带回前台后继续完成，总计 175.93 秒。不能以此推断具体冻结机制，也不能当作后台及时性或长待机通过。

## Play 权益阻断

正常 Pro 页和恢复购买未取得权益。独立 Billing 查询为：setup 0、product query **3 / Billing Unavailable**、purchase query 0 / 空列表。这与之前三星的商品级 `PRODUCT_NOT_FOUND` 不同，本次是整个商品查询失败。

当前 OPPO 已有先前三星测试配置中的工作账号，也有完成免费测试购买的个人账号。已核对后尝试切换到该已有个人账号，并仅重启 Play 进程；再次查询仍是上述结果。商店页在工作账号显示内部测试版，在个人账号显示普通版。没有清应用/Play 数据、卸载正常包、注入或复制收据，也没有进行真实或测试扣款。

多账号手机的结算账号选择还与安装来源关联，单独切换商店当前账号不能证明结算账号已切换。[Google 官方测试说明](https://developer.android.com/google/play/billing/test)。本轮不能据此认定普通包已购恢复通过。

## 制品与取证

私有忽略目录：`play/artifacts/3.3-33/oppo-hiddenapibypass-2026-09-11/`。

- `live-bluetooth-initial.txt`：实际初始化、连接和电量读取。
- `ui-bluetooth-initial.json`、`ui-system-bluetooth-initial.json`、`ui-watch-app-connected.json`：普通 App 和系统对照。
- `pro-bluetooth-locked.txt`：3 项未解锁检查。
- `pro-locked-preferences-validation.json`、`pro-prefs-before.tar`、`pro-prefs-after-locked.tar`：业务偏好恢复验证。
- `appwidget-after-locked.txt`：原组件身份保留。
- `live-billing-pro.txt`、`live-billing-purchased-account.txt`、`live-billing-after-play-restart.txt`：正常 Play 查询。
- `pro-test-build-verified.log`：新增测试 APK 编译成功。先前编译因新测试 Java boolean setter 的 Kotlin 属性访问方式错误失败，已修正为显式 setter 后重编译通过；普通主包未因此改变。

## 独立 QA 解锁功能验证

使用独立包 `com.github.xckevin927.android.battery.widget.oppoqa`，版本名 `3.3-OPPO-QA`，应用名 Battery Widget OPPO QA，且为 testOnly。QA 的 ProBilling 只在该包名下返回测试权益，不创建 BillingClient、访问收据或发起购买；此结果不是普通包的购买/恢复验收。QA 移除了 Firebase 和非必需的 GCM 构建依赖，未修改普通项目的业务依赖。

逐文件核对 `app/src/main`：267 个文件一致，只有 QA manifest 和 ProBilling 不同，无遗漏文件。蓝牙仓库、反射工具、提醒、组件配置、组件 Provider 和 collection service 均使用当前普通包的实际业务代码。哈希和差异范围保存在 `qa-source-validation.json`。

`LiveBluetoothProTest` 在 QA 上 **3 项通过、无跳过**，耗时 208.884 秒：

1. 实际提醒表单开启蓝牙提醒、选择当前已连接耳机、阈值 1%，保存值正确，提醒执行入口开启；全天免打扰基线避免通知。
2. 实际组件编辑器选择组合样式和真实耳机，成功保存独立测试配置并返回成功。
3. 实际耳机 100% 和手表未知电量的快照均不产生低电量候选。

测试页面关闭后也出现过执行停顿，将 QA 包重新带回前台后完成；没有因此放宽断言。此结果覆盖前台配置操作，不覆盖后台及时性。系统禁止 shell 授予运行时权限，改为实际点击附近设备权限弹窗完成 QA 蓝牙授权。

真实耳机目前 100%，而提醒阈值上限 95%；正向低电量通知、去重及静默补发仍需自然降电后的新鲜读数，不能用模拟电量充当真机通过。组件实际渲染结果见下一节。


## 组合组件实际渲染与“无法添加微件”的处理

QA 通过实际 `AppWidgetHost` 绑定 `BatteryWidget`，使用实际 `BtWidgetService` collection adapter、真实设备与真实电量。独立宿主创建的临时 ID 与原桌面 7/8 无关，每次结束均删除并撤销临时绑定权限。

首次失败与现场取证复现均在宿主中显示“无法添加微件”。根因在本次 QA 测试工具：`host.createView(activity, ...)` 使用 AppCompat Activity 上下文，布局中的 `ImageView` 被替换为 `AppCompatImageView`。单纯 inflate 布局能成功，执行真实电量图像的 `RemoteViews.setImageViewBitmap` 动作后失败：

```text
android.widget.RemoteViews$ActionException:
view: androidx.appcompat.widget.AppCompatImageView can't use method with RemoteViews:
setImageBitmap(class android.graphics.Bitmap)
```

QA 宿主改为 `host.createView(context.applicationContext, widgetId, info)`，仍将宿主视图附加到实际 Activity 展示。对照时使用相同布局和图片设置动作，应用上下文保留标准 `ImageView` 并执行成功。普通项目的生产代码没有自建 AppWidgetHost；原有 `WidgetIntegrationTest` 也已使用应用上下文。本次不需要修改产品布局、替换组件 Provider 或清除用户数据。

修正后的真实宿主测试通过（`pro-bluetooth-qa-widget-host-context.txt`，**1 项 / 4.421 秒**）：

- 手机电量区域和蓝牙设备区域同时可见。
- OPPO Enco Free4 显示真实 100%，华为手表显示未知而非伪造百分比。
- 取消选择手表后，实际 collection service 更新，手表消失，耳机仍显示 100%。

已查看实际宿主图片 `qa-real-bluetooth-combined.png` 和 `qa-real-bluetooth-earbud-only.png`。这是运行在 OPPO 上的 Android 宿主渲染，未声称是 OPPO 桌面新建 Pro 组合组件的手势验收。

随后先完成宿主 + 纯布局上下文对照 **2 项通过 / 5.347 秒**（`pro-bluetooth-qa-widget-host-layout-only.txt`）。最后补上图片动作的对照时，具体异常被成功捕获，应用上下文仍通过；同轮宿主用例因未读取到“已连接且电量未知”的手表，在前置条件处失败（`pro-bluetooth-qa-widget-host-final.txt`）。因此最后一轮不能报告为全部通过。未进一步判定该瞬时缺失是设备连接变化还是冷启动快照时序，不以此覆盖之前的真实宿主成功证据。

取证：`qa-context-comparison-layout-only.json`、`qa-context-comparison.json`、`qa-host-context-tree.txt`；最终 QA 测试源码保存在私有 `qa-test-source/`。修复前失败记录保留，未掩盖为产品已通过。

## 普通包检查与范围边界

OPPO 原桌面 ID 7 手机组件与 ID 8 蓝牙组件仍存在。实际桌面手机组件显示 96%，当时系统栏为 97%；可确认正常渲染，不能据此声称刷新无延迟。原蓝牙组件所在格子很小，截图只露出耳机图标，未显示“无法添加微件”；当前尺寸下可读性仍需另行适配或扩大组件核对，不算完整电量内容通过。截图 `normal-launcher-final.png`、`normal-launcher-bluetooth-final.png`。

本轮普通包未购 3 项 + QA 解锁功能 3 项通过，另外真实 QA 组合宿主通过 1 项。真实 Play 恢复仍受 `Billing Unavailable` 阻断；低电量通知正向投递、通知去重、静默补发、断连重连、长待机及时性尚未获得本轮实测结论。


## 收尾核验

独立 QA 主包、QA 测试包及 QA 宿主状态均已移除，正常包保留。`BATTERY_PREF`、`battery_alerts`、`bluetooth_device_preferences` 的所有键值与测试前一致，原 OPPO 桌面 ID 7/8 保留。正常包蓝牙权限仍开启，通知权限仍关闭；蓝牙、Wi-Fi 与低电量模式均保留原值 1。Play 原账号此前已回读确认恢复。

按用户要求测试期间每 10 秒发送唤醒键；结束后两个临时唤醒进程均停止，临时标记删除，`stay_on_while_plugged_in` 恢复为原值 0。清理证据 `final-cleanup-validation.json`，偏好验证 `pro-final-preferences-validation.json`，总记录 `pro-validation.json`。未提交代码、上传 Play、发起新付款或修改普通包生产业务代码。


## 后续：用户要求实际放到 OPPO 桌面（进行中）

本次是新的桌面添加测试，区别于上面的已完成宿主测试。重新只读确认普通包仍未解锁（`LiveEntitlementStateTest` 的缓存签名权益检查 1 项通过），因此重新安装此前同 SHA256 的独立 QA 包。经真实 UI 授予附近设备权限，选择“手机和选中的设备”，关闭所有可见设备，仅勾选 OPPO Enco Free4，并点击“保存并添加”。QA 偏好回读确认 `combined`、所有设备=false、选中设备数=1。

点击后系统尚未创建新的 QA 桌面 widget；不能宣称添加成功。快捷添加无结果的具体原因尚未建立，正转向系统桌面组件入口。期间手机进入锁屏，已请求用户解锁，等待后继续。普通组件 ID 7/8 保留。

用户要求定时实际触摸。起初单纯唤醒键后改成每 8 秒轻触状态栏；该触摸未阻止本机息屏。随后临时启用 USB 保持亮屏，已回读 `mWakefulness=Awake`、`mStayOn=true`、`mPlugType=2`。触摸记录保存在 `screen-touch-heartbeat.jsonl`，不能把返回 0 等同于系统绝不息屏。测试进行中暂保留 QA 包与 USB 常亮，完成后应停止触摸任务并恢复 `stay_on_while_plugged_in=0`。

当前状态、页面和配置证据：`play/artifacts/3.3-33/oppo-launcher-combined-2026-09-11/launcher-test-state.json` 及同目录私有文件。尚未完成实际桌面添加、尺寸适配及点击验收。


### 实际桌面添加已完成

用户随后解锁，已通过 OPPO 系统卡片中心完成 QA 组合组件的真实添加、配置、缩放和点击检查，新 ID 18 保留在桌面，最终手机与耳机均显示 100%。同时发现有效电量长期不变后因 30 分钟过期策略显示未知、耳机点击不定位目标的问题，尚未修复。见 [实际桌面测试、截图与限制](oppo-launcher-combined-widget-2026-09-11.md)。此阶段的新 QA 包和组件按查看需要保留，临时触摸已停止，USB 常亮恢复为 0。
