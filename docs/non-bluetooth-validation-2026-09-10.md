# 非蓝牙功能补测 — 2026-09-10

本轮可执行补测已完成，发现并修复 1 个应用问题。最终通过 33 个 JVM 用例、11 个普通包功能用例及 4 个隔离 QA 用例，并完成下表中的实际系统操作。三星本轮未连接；实际 Play 交易和长期真机待机仍未验证。

## 环境与边界

- 专用 `FeatureReview` 模拟器，`emulator-5582`，Android 16 / API 36。普通包 3.3（33），targetSdk 36。
- 本轮未操作蓝牙配件、配对、蓝牙权限或连接设置。
- 已解锁流程使用当前源码的临时 `.qa` 副本，仅该副本强制 Pro，Firebase 采集关闭；普通源码仍校验真实购买凭据。QA 结果不能当作 Play 购买证据。
- 电量、拔插电及 Doze 使用模拟器系统测试命令，不能据此声称真实电池变化或三星整夜待机已通过。
- 证据目录：`play/artifacts/3.3-33/non-bluetooth-2026-09-10/`。

## 本轮修复

静默时间选择器在 Activity 因旋转等原因重建后，Fragment 能恢复，但“确定”回调没有重新绑定。实际复现：输入 22:30，重建后点确定，页面仍显示 22:00（`qa-alert-ui-third.log`）。

`AlertsActivity.kt` 现在对新建和恢复的时间选择器统一绑定当前 Activity 的回调。普通包持久回归覆盖开始时间 22:30、结束时间 07:45 在重建后确认生效，以及再次修改后取消保留原值。独立只读复核确认 Material 1.7.0 不持久化正向监听器，此修复与其生命周期一致。FreeReflection 和普通包权益校验未改动。

## 功能回归

| 范围 | 用例数 | 最终结果 |
| --- | ---: | --- |
| 普通提醒草稿重建、未解锁保存拦截、关闭已存提醒、时间选择器重建与取消 | 3 | 通过 |
| 小组件配置保存/重建、取消不落盘 | 2 | 通过 |
| Pro 无商品不可购买、付费组件引导且不丢草稿 | 2 | 通过 |
| 本地购买凭据中断写入、截断和过大输入 | 2 | 通过 |
| 小组件横竖屏真实滚动及点击保存 | 2 | 通过 |
| QA 自选阈值校验、静默时间保存/重开、取消修改 | 1 | 通过 |
| QA 实际刷新入口：阈值与周期去重、静默补发、FULL 100% | 3 | 通过 |

QA 表单验证 0% 被拒绝并显示错误；87% 和静默开始 22:30 经重建、确认、保存和重开后保留；再输入 91% 后返回取消，持久配置仍为 87%。

QA 执行测试从 BatteryService 的真实 sticky intent 读取模拟电量，经 `WidgetUpdateService.refreshWidgets` → `BatteryRepo` → 应用监听器触发提醒，未直接调用通知发送函数。覆盖 79%→80% 阈值、81% 与 NOT_CHARGING 同周期不重发、拔电后 82% 再插电重发；全天静默不消费待提醒状态，关闭静默后补发；FULL 100% 有效。

## 实际系统操作

| 场景 | 观察结果 | 主要证据 |
| --- | --- | --- |
| 亮屏周期刷新 | 两次服务定时更新时间差 59.986 秒 | `monitor-channel-restored.txt`、`background-events.jsonl` |
| 熄屏 65 秒及电量事件 | 无定时刷新；42%→43% 后熄屏下读取到 43% | `background-events.jsonl` |
| 强制 Doze 180.12 秒 | 服务存活，内部定时计数不增长；退出并唤醒后恢复刷新 | `background-events.jsonl`、`jobs-during-doze.txt` |
| force-stop 与显式重开 | 停止后服务不存在；显式重开后恢复 | `background-events.jsonl` |
| 通知中的“停止监测” | 服务停止、开关持久化为 false，重开仍未开启 | `stop-notification-service.txt`、UI 层级记录 |
| 全局通知权限与常驻通知类别恢复 | 撤销/关闭后状态显示暂停；真实系统界面允许后恢复服务 | `global-notification-*.txt`、`monitor-channel-restored.txt` |
| 开启监测后实际重启 | 未打开 App，20.57 秒采样已出现服务和通知 13364，Worker SUCCESS | `reboot-*.txt`、`reboot-result.log` |
| 关闭监测后实际重启 | 未打开 App，22.9 秒采样仍无监测服务，开关仍为 false；Worker SUCCESS；随后打开 App 也保持关闭 | `reboot-off-*.txt`、`reboot-off-result.log` |
| 提醒进程重开去重 | PID 6357→6752，已提醒状态保留；重开后 18 秒内未重复发出 | `qa-runtime-events.jsonl` |
| 仅关闭提醒类别 | 达到 82% 时无提醒，待提醒状态未消费，同一 QA App 监测服务继续运行 | `qa-channel-blocked-threshold-*.txt` |
| 恢复提醒类别 | 返回应用后的刷新补发 82% / 目标 80% 提醒 | `qa-channel-restored-*.txt` |
| 点击提醒通知 | 打开正确的提醒页面，通知自动清除，去重状态保持 | `qa-notification-opened-*.txt`、`qa-phone-alert-notification.png` |

`WidgetUpdateService.refreshCount` 只统计服务内部 runnable，不能用它单独判断 Worker、静态 receiver 或 Activity 的直接刷新。`cmd jobscheduler run -f` 返回 Running job 也不等于 doWork 已执行；提前触发仍可能被 WorkManager 延期。本轮 Worker 执行使用重启后 SUCCESS 日志取证，不保证自然每 15 分钟准时执行。

手机提醒按连续插电周期去重；充电保护切换为 NOT_CHARGING 不会重置周期。force-stop 恢复、18 秒去重与三分钟 Doze 都是短时检查，不能替代低内存杀进程和长期待机测试。

## 构建与失败记录

- 修复后 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintRelease` 通过；JVM 33 项，0 失败/跳过；Lint 0 错误 / 228 警告。
- 普通包首轮 11 项中 10 项通过。新增 picker 用例第三次输入点击到容器后，EditText 未显示；改为点击真实分钟 Chip，并等待弹窗稳定后，提醒 3 项定向复跑全部通过（19.456 秒）。其余 8 项已在首轮通过。日志：`normal-nonbt-suite.log`、`normal-alert-form-final.log`。
- QA 提醒执行 3 项在 `qa-nonbt-suite-final.log` 通过；表单最终在 `qa-alert-form-final-pass.log` 通过（13.606 秒）。前者保留同轮表单失败，不把原整轮记为通过。
- QA 输入测试遇到 IME/滚动同步、时间选择器先点分钟 Chip、返回根 Activity 的 Espresso 处理问题。页底自动跳回手机输入框时触摸未聚焦；真实点按截图证明可正常输入。最终脚本按页面顺序填写，再检查静默时间并返回取消；保留相同保存/取消断言，移除临时观察探针。未为测试增加程序化点击或普通包权益绕过。
- ADB 服务中途连接中断，确认既有 IPv4 监听后使用 `127.0.0.1` 恢复，未停止其他调试会话。

## 清理与仍未验证的部分

电池模拟已 reset、强制 Doze 已解除、临时 QA 和 QA.test 已卸载；普通包监测恢复为测试前的开启状态，电量显示 100%。清理证据：`qa-cleanup.txt`、`final-battery.txt`、`final-packages.txt`、`final-service.txt`。专用模拟器随后正常关闭。

- 三星真机本轮未连接，因此修复后的三星回归仍待完成。
- 三星长时间熄屏、OEM 省电与真实低内存杀进程仍待验证。
- Play 只读查询显示一次性商品目录为空，internal 轨道仍为旧版 `3 (0.2.0)`。尚不能测试实际购买、恢复购买、退款及待付款。未创建/启用商品、上传、送审或付款。

修复后普通调试 APK：`play/artifacts/3.3-33/non-bluetooth-2026-09-10/battery-widget-3.3-33-debug.apk`。

SHA-256：`44eef92e6feda21fed2c74afdc5aa61ff2c922ec3b839f3d22a728c30674385f`。
