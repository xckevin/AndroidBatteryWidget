# OPPO 桌面“手机＋耳机”组合组件测试 — 2026-09-11

## 结果

已通过 OPPO 自带桌面实际添加组合组件，系统 widget ID **18**，Provider 为独立 QA 包的 `BatteryWidget`，宿主为 `com.android.launcher`。组件位于 QA 应用图标旁的新桌面页，调整为约 4×2 格，手机与 OPPO Enco Free4 并排显示。最终实际截图中二者均为 **100%**，未出现“无法添加微件”。组件与 QA 包保留供用户查看。

普通包的真实签名权益只读检查仍为未购买（1 项通过），所以本轮使用 `com.github.xckevin927.android.battery.widget.oppoqa`。QA APK SHA256 为 `4cb4fca916fc396a6f13dadb8736bb27c72e9fc56fda3ef63fe046616c460cd0`，与前轮验证的 QA 主包一致。其 Pro 权益仅为测试实现，不代表普通包已购买或恢复成功。未触碰收据、发起付款、上传 Play 或修改产品业务代码。

## 实际操作与验收

- 经真实附近设备权限弹窗授权 QA 包；在组件编辑器选择“手机和选中的设备”，关闭“所有可见设备”，只勾选 OPPO Enco Free4。
- 点击 App 的“保存并添加”后，QA 偏好保存成功，但当时未生成新桌面 ID。该阶段存在额外定时触摸，可能干扰系统浮层，原因未单独确认，不能直接归因为 App 快捷添加 API 缺陷。
- 用户解锁后，从桌面空白处长按进入编辑 → 卡片 → 搜索 Battery → 选择右侧手机电量组件 → 添加到桌面。
- 系统启动新 widget 的实际配置 Activity，正确继承组合样式和耳机选择；点击“保存小组件”完成添加。
- 默认 1×1 格只显示手机电量。拖动 OPPO 的右下角缩放手柄后，同一 ID 18 显示手机与耳机，最终扩大为横向布局。没有直接修改 launcher 数据库或向桌面写入假绑定记录。
- 手机区域点击：打开 QA 设置中的实际监测状态，符合配置。
- 耳机区域点击：可打开 QA 蓝牙页，但两次从桌面点击后均停留在列表顶部的其他设备，没有定位到 OPPO 耳机。页面等待后仍未定位，属于本轮发现的未解决问题。

## 发现：有效电量长期不变后显示“未知”

首次完成放大时，组件与 QA 设备页将耳机显示为“暂无电量读数”，标注最近观测到电量为 33–39 分钟前。同期系统蓝牙页显示耳机使用中，左右耳均为 100%，手表已连接。

代码对应逻辑：`FrameworkBatteryReading.observe()` 仅在首次有效读数、读数变化或明确电量广播时更新 `successAt`；对相同数值的普通查询只更新 `checkedAt`。`statusAt()` 使用 `BatteryReading.EXPIRE_AFTER_MS`（30 分钟），超时后 `displayedLevel()` 返回 -1。因此即使系统仍能提供同样的 100%，App 也可能将其隐藏为未知。这个策略需要后续修正，且应区分显示缓存值和低电量提醒所需的新鲜数据，不能把反复查询伪装成外设新上报。

本轮用 QA 进程重启验证恢复：先尝试后台 `am kill`，PID 没有变化，故不把那次记录为成功冷启动；随后仅 force-stop QA 包并正常打开，PID 从 22463 变为 8368，未清数据。同一个桌面 widget ID 18 恢复显示耳机真实 100%。这只是恢复实验，**不是过期策略已修复**。最终截图展示恢复后的状态，原“未知”现场、系统对照和进程记录均保留。

## 取证

私有忽略目录：`play/artifacts/3.3-33/oppo-launcher-combined-2026-09-11/`。

- `normal-entitlement-current.txt`：普通包真实未购权益检查。
- `qa-earbud-selected.json`、`qa-prefs-before-desktop.xml`：组合与单设备选择。
- `cards-battery-results.png`、`system-add-result.json`、`widget-config-save.json`：真实系统添加流程。
- `new-widget-on-launcher.json`、`widget-final-layout.json`、`final-widget-bindings.txt`：OPPO 桌面绑定与渲染。
- `widget-after-resize.png`、`earbud-current-reading.json`、`system-earbud-battery-comparison.json`：未知电量与系统 100% 的对照。
- `phone-widget-click.json`、`earbud-widget-click.json`、`earbud-click-settled.json`、`earbud-click-after-restart.json`：点击行为。
- `qa-process-restart.json`、`widget-after-qa-restart.json`：实际进程重启与电量恢复。
- `final-phone-earbud-widget.png`：实际 OPPO 桌面最终截图，已视觉核对。
- `normal-prefs-before.tar`、`normal-prefs-final.tar`、`final-preservation-validation.json`：普通配置与原组件保留。

## 保留与清理

普通包 `BATTERY_PREF`、`battery_alerts`、`bluetooth_device_preferences` 所有键值与本轮开始时一致。原桌面 ID 7、8 保留，新 QA ID 18 单独存在；本轮没有卸载普通包、修改配对关系或开启监测通知。

用户要求定时实际触摸。最初每 8 秒触摸状态栏仍发生息屏；之后临时启用 USB 常亮，回读确认 Awake/StayOn=true。系统菜单与添加浮层期间暂停额外点击，普通页面恢复只针对已核对空白区域的触摸，避免关闭菜单或点击其他应用。测试结束停止临时触摸进程并删除标记，`stay_on_while_plugged_in` 恢复并回读为原值 **0**。

待修复：相同有效电量超过 30 分钟后误变未知；桌面耳机点击未定位目标。默认组合 1×1 只显示手机的体验也可改善。本轮不声称长期待机、低电量通知或真实 Play 已购流程通过。
