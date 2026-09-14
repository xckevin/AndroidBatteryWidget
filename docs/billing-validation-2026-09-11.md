# Google Play Billing 验证 — 2026-09-11

## 当前结论

**截至 2026-09-11 14:15 HKT，三星上的核心 Play 付费链路已通过真机验证。** 商品查询、取消、拒付、慢速拒绝/批准、订单确认、已购买者离线、收据丢失后的恢复、测试退款撤权，以及提醒和组合组件的实际保存门控均已执行。基础价格仍为 **US$1.99，一次性解锁**，三星本地价格 IQD 2,600。全程使用明确标记的免费测试卡，没有实际扣费；最终保留最后一笔已确认测试购买，App 已解锁 Pro。

仍有一个已复现的体验问题：普通测试拒付被显示成“设备无法使用结算”，须点击恢复购买才能重新购买。另已验证离线退款不会立即撤权，需联网成功查询。未执行完整卸载重装、切换购买账号、Play 签名安装包交易和蓝牙配件验证；不能把本轮结果等同于全部上线验收。

## 已完成的后台配置

- 应用：`com.github.xckevin927.android.battery.widget`。
- 商品：`battery_widget_pro`；购买选项 `buy`，`ACTIVE`，Buy，向后兼容。
- 英文和简体中文名称/说明已保存，商品为非消耗型 Pro 权益。应用不调用 consume。
- 173 个地区可供应，Play 自动换算价格。API 回读：美国 USD 1.99、香港 HKD 15.00、伊拉克 IQD 2,600。新增地区默认 USD 1.99 / EUR 1.71。
- 三星上的两个 Google 账号均已加入原有已选中的许可测试名单，连同原有开发者账号共 3 人；原有成员保留。没有授予服务账号财务、订单或用户管理权限。
- 首次创建商品必须让 Play 识别含 BILLING 权限的制品。上传并保存 33 草稿后创建入口仍未开放；通过 Console 完成内部版本发布后入口开放。
- **内部版本 33 (3.3) 为 PUBLISHED，内部轨道现为“有效”。** 继续测试时恢复了轨道，并额外选中许可测试名单，原有灰度测试名单仍保留。用户明确允许后，三星工作账号接受内部测试邀请；手机网页显示已是测试人员，Play 商店显示“Battery Widget（内部测试版）”。
- **正式版仍为 32 (3.2)，PUBLISHED**，没有发布 33 到正式版。

## 制品和本地验证

签名 AAB：`play/artifacts/3.3-33/billing-2026-09-11/battery-widget-3.3-33.aab`。

- SHA-256：`2157e1cd5a25d8d8ee2f1581c46a9988e5ba3a8948f30051521d54a6361fea00`，与 Play 上传响应一致。
- versionCode 33 / versionName 3.3 / minSdk 21 / targetSdk 36，包含 `com.android.vending.BILLING` 和 Billing 8.0.0。
- 上传证书 SHA-256：`87:BF:F4:26:94:A2:E1:0C:9B:71:81:EF:25:08:8D:74:B1:42:18:4F:F7:00:6C:F5:7B:A7:EE:4B:78:4C:66:4D`，与上一正式包及 Play 上传证书一致。
- `:app:bundleRelease :app:testDebugUnitTest :app:lintRelease` 成功。单测任务本轮由 Gradle 判定 UP-TO-DATE，现有报告为 33 tests / 0 failures / 0 errors；不能声称本轮重新执行了 33 项。Lint 0 errors / 228 warnings。
- `bundletool validate` 与 JAR 签名完整性检查通过。独立核验直接检查 AAB 的清单、DEX、签名：正常包名，`hasPro()` 仍走收据验签，无 QA 强制解锁；FreeReflection 实现仍在制品中。
- 内部发布页只有警告：相对旧内部版本 3 的设备覆盖变化、权限/包大小变化、已有广告 ID 声明提示及无混淆映射。没有改变声明或提交正式版。

## 首轮三星操作记录

设备为 Galaxy S23 Ultra / Android 15。正常包 33/3.3 的 Pro 页面可以打开；商品尚未创建时正确显示“此购买项目目前不可用”。商品启用后的首次读取也尚未显示价格，不能据此确定是传播、账号资格还是客户端问题。

Wi-Fi 原先关闭，本轮为查询 Play 启用已保存网络，飞行模式保留。中途 USB 在系统中可见而 ADB 列表为空，恢复原生 USB 后端后连接恢复。随后手机多次被切到另一扫码应用；为避免并发操作干扰，停止手机 UI 操作。跨应用产生的两个布局文件已移除，不计作 Billing 证据。没有执行清数据、卸载、QA 权益注入或购买。

Wi-Fi 暂保留开启，避免打断另一项手机任务；本轮没有恢复关闭或改动其他网络/账号设置。

## 继续测试的结果

- 正常 debug 签名主应用保持版本 33/3.3，没有替换主 APK、清应用数据或注入权益。
- 新增 `LiveBillingDiagnosticsTest`，仅在传入 `liveBilling=true` 时执行真实 Play 查询。它只输出连接状态、商品/购买选项/价格、未获取原因，以及已购状态和 acknowledge 布尔值；不输出账号、收据、购买令牌、签名或订单号，不购买、确认或消费订单。
- `:app:assembleDebugAndroidTest` 编译成功。三星上 4 次只读查询均为：setup `0`；product query `0`，商品列表为空，`battery_widget_pro` unfetched status `3`；purchase query `0`，已购列表为空。诊断测试的 `OK` 仅表示查询完成，不表示商品或交易验收通过。
- 已排除客户端购买选项筛选导致本次缺价：Play 在筛选之前就没有返回商品。后台 API 回读商品仍 ACTIVE。已补齐测试账号、恢复轨道、成功加入测试，重启 Play 及清理其缓存后仍未返回商品。未清 Play 数据。传播延迟或 Play 账号侧缓存仍是待验证假设，不保证等待一定时间即可解决。[官方商品状态码说明](https://developer.android.com/reference/com/android/billingclient/api/UnfetchedProduct.StatusCode)。
- **未购买者离线测试通过 1 项**：飞行模式为 1、Wi-Fi 为 0，在新进程内打开 Pro 并重建 Activity 后，权益仍为锁定，购买按钮不可用，恢复入口存在。执行 `ProFlowTest#unavailableCatalogHasNoBuyablePriceAndSurvivesRecreation`；没有执行会改写收据的存储测试。
- 首次离线测试因共享 ADB 服务器连接变化中断，不计通过。使用独立端口 5038 的原生 USB ADB 后完成重跑。后续命令必须显式指定三星序列号，避免操作同时存在的模拟器。
- 测试结束已恢复 Wi-Fi，并确认连接到原保存网络；飞行模式保留。持续监测 `monitoringEnabled=true`、`monitoringRunning=true`、`lastStartError=null`。前后私有状态归档对比：业务配置未改变，无新增/删除文件，无 Pro 收据；变化仅在 WorkManager 和系统/Firebase 运行数据。

以上是商品不可用时的历史记录；该阻断已在下面的 13:25 复测中解除。

2026-09-11 11:15 HKT 再次只读复查，仍是连接/查询成功、商品级 `PRODUCT_NOT_FOUND`。证据为 `resumed/diagnostic-explanation-recheck.txt`。下一轮先在配置传播后复查；若持续存在，再对照同一测试账号从 Play 安装的内部版本，以区分侧载/账号绑定与服务端目录问题。当前三星为 debug 签名主包，切换 Play 签名包前须安排配置和原组件的恢复，不能直接卸载作为无损排查。跨环境仍复现时整理已保存证据交 Play 支持；尚未对外发送支持请求。

## 13:25 后真实测试购买结果

- 未改变商品、价格或主 APK，查询恢复：商品 `battery_widget_pro`，购买选项 `buy`，IQD 2,600，存在 offer token，非租赁，unfetched 列表为空。证据 `resumed/diagnostic-1325.txt`。延后查询恢复与配置传播/缓存刷新相符，但不能据此确定是哪项修复单独起效。
- Pro 页显示“可以解锁”，价格和购买按钮正常。Play 弹窗明确显示测试卡及“这是测试订单，我们不会向您收取任何费用”。支付方式页面确认实际使用的是手机 Gmail 测试账号，并非先前参加内部测试的工作账号；两个账号都已在许可测试名单内。
- 取消结账后仍锁定，价格与购买按钮正常，可再次发起。
- “测试卡，一律拒绝”返回 `Declined by always denied test instrument`，App 未解锁。点击恢复购买后重新可购买。发现文案问题：拒付返回后 App 显示“此设备当前无法使用 Google Play 结算服务”，购买按钮暂禁用；它实际是这笔测试付款被拒绝，后续可改善为与付款场景相符的失败提示及重试入口。本轮未修改生产代码。
- 使用“一律批准”测试卡完成购买。Google 显示付款成功，跳过首次付款后的可选购物身份验证设置建议后，App 显示“此设备已解锁 Pro”，购买按钮消失。
- 独立只读 Play 查询返回 `state=1`（PURCHASED）、`acknowledged=true`。证据 `resumed/diagnostic-purchased.txt`；这是 Play 已购记录的确认状态，不仅是本地 UI 提示。查询重启过 App 进程，但尚未执行断网后的已购买者恢复测试。
- 当时保留第一笔测试购买权益，尚无退款/撤权；该订单随后已在下面的继续测试中退款并撤权。测试后持续监测启用且运行，`lastStartError=null`。私有备份 `resumed/app-state-test-purchased.tar` 权限 0600，包含已退款的历史测试收据，不应上传、打印或恢复到设备。
- UI 证据为 `resumed/20-pro-price-ready.xml` 至 `resumed/36-purchase-app-result.xml`（其中包含账号的文件均为 0600）。

## 继续完成的真实 Play 验收

- **已购买者离线：3 项通过。** 关闭 Wi-Fi、保留飞行模式，在新 App 进程中验证正常 RSA 收据权益、Pro 购买入口状态、提醒/组合组件策略。证据 `offline-purchased-entitlement-fixed.txt`。首次运行因测试启动 Activity 缺少 `FLAG_ACTIVITY_NEW_TASK` 失败，修正测试后通过；不是应用权益故障。
- **丢失本地收据后恢复：通过。** 安全备份并临时移走收据，断网新进程确认锁定（`missing-receipt-locked.txt`，1 项）。联网后正常前台 Play 查询重新生成收据，手动点击恢复购买显示“购买已恢复”；恢复收据与原订单匹配。没有把备份写回冒充恢复。设备临时备份已移除。此测试不等同于完整卸载重装。
- **退款并撤权：通过。** 仅对本轮第一笔明确标为测试的订单，在 Console 执行全额退款并勾选移除权限，确认状态已退款、金额归零。退款时手机离线，本地有效缓存仍保留权益（1 项）；联网查询返回空购买列表后，正常 App 撤权，3 项锁定检查通过，购买入口重新可用。证据 `order-1-refunded-console.txt`、`diagnostic-refunded.txt`、`refunded-offline-cache.txt`、`refunded-online-locked.txt`。
- **慢速拒绝：通过。** Play 初始返回 `PENDING`、`acknowledged=false`，UI 显示处理中并禁用购买；重启后 3 项锁定检查通过。自动拒绝后查询变为空列表，Pro 页恢复可以购买。证据 `diagnostic-pending-decline.txt`、`pending-decline-locked.txt`、`diagnostic-pending-decline-final.txt`、`51-pending-decline-ready.xml`。
- **慢速批准：通过。** 同样先确认 `PENDING`、未 acknowledge、新进程不授予 Pro（3 项）。自动批准后，独立查询为 `PURCHASED`、`acknowledged=true`，最终 Pro 页面显示已解锁。证据 `diagnostic-pending-approve.txt`、`pending-approve-locked.txt`、`diagnostic-pending-approve-check-2.txt`、`59-final-pro-owned.xml`。
- **实际付费功能保存：未购买 2 项、已购买 2 项全部通过，无跳过。** `LivePaidFeatureGateTest` 在正常 App 中实际勾选提醒并点击保存，验证无权益时原设置不变、有权益时设置生效；实际选择组合组件并点击保存，验证无权益时跳转 Pro 且不写入，有权益时保存组合样式并返回 `RESULT_OK`。使用独立测试 ID 91072，不修改原桌面组件 ID 8；每项结束恢复完整业务偏好。提醒测试采用全天免打扰避免发出测试通知，不创建或连接蓝牙设备。证据 `paid-gates-locked.txt`、`paid-gates-purchased.txt`。
- **收尾：通过。** 独立归档比对确认 `BATTERY_PREF` 和 `battery_alerts` 的所有键值与保存测试前一致，原 One UI 组件 ID 8 保留；监测启用且运行，`lastStartError=null`。Wi-Fi 开启、飞行模式保留，设备没有临时收据备份。最终测试收据和状态私有备份为 `app-state-final-test-purchased.tar`（0600）；取证 `final-state-validation.json`、`final-widget-8.txt`。第一笔测试订单已退款，最后一笔慢速批准订单保留有效权益。

本阶段仅新增/修正显式启用的真机测试用例和测试记录，未替换主 APK、修改生产 Billing 逻辑、注入权益或改变 FreeReflection。测试 APK 编译成功，新增实际保存测试在两种真实权益状态下均通过。涉及收据/账号的取证仅保存在被忽略的私有目录。

| 场景 | 验收要求 | 状态 |
| --- | --- | --- |
| 商品与账号 | 正常包查询本地化价格；结账账号正确且明确显示测试付款方式 | 通过 |
| 取消/拒付 | 测试取消、测试拒付后仍锁定，可重试；重启离线无权益 | 取消、拒付及刷新重试通过；拒付后的离线重启未单独重测 |
| 测试批准 | 签名收据验证、解锁，组合组件和提醒实际门控生效；订单侧已确认 acknowledge | 通过，包含未购买/已购买各 2 项真实保存检查 |
| Pending | 慢速测试卡待付款时锁定；批准后解锁，拒绝后成功查询恢复可购买 | 两种路径通过，Pending 阶段均未 acknowledge |
| 恢复购买 | 同一账号在本地缺失收据后恢复，不注入 QA 权益 | 通过；完整卸载重装未执行 |
| 离线 | 已验证权益在离线/进程重启后保留；无购买者离线不解锁 | 两种状态均通过 |
| 退款并撤权 | 仅操作测试订单；联网成功查询后撤权，付费门控恢复 | 通过；未单独在真实桌面组合组件上观察退款降级渲染 |
| 其他上线边界 | 切换购买账号、Play 签名安装包交易、蓝牙配件行为 | 未执行 |

必须核对付款弹窗的测试标识，不能以价格为零或账号名推断测试资格。用户授权的是测试，不包含真实扣款。官方允许许可测试账号侧载正常包名的 debug 签名构建，因此可先验证现有手机安装，无需卸载用户配置。[官方测试说明](https://developer.android.com/google/play/billing/test)。

## 只读复核发现的重点边界

- 授权持久化发生在 acknowledge 之前。App 已解锁不能证明订单确认成功；确认失败后的重试依赖后续前台查询/恢复，暂无后台定时重试。
- 离线收据无新鲜度上限，退款后断网仍保留权益，直到后续成功查询。本轮已在真实测试退款中验证这一边界。
- 成功查询返回目标商品但签名无效时，现有逻辑会报校验失败并保留旧缓存权益；是否改为撤权需要定义明确策略。
- `ReceiptStore.clear()` 不报告删除失败；已有测试只覆盖删除成功。存储失败后的冷启动路径仍缺验证。

除已明确标注实测的离线退款边界外，其余是代码审查发现，尚未注入网络/存储故障验证。拒付文案问题已真机复现，仍未修复。本轮没有修改生产 Billing 逻辑。

## 取证位置

忽略目录 `play/artifacts/3.3-33/billing-2026-09-11/` 保存构建日志、清单、上传响应、内部/正式轨道回读、商品 API 回读、Console 截图和制品记录。继续测试证据位于 `resumed/`，包括测试资格/商品 UI、后台配置、诊断、离线/恢复/退款/Pending/实际门控测试，以及权限 0600 的状态归档；结构化结果见 `live-test-results.json`。账号信息留在私有取证，不写入本报告。任务浏览器会话 `cpud`、`yrka` 已关闭，先前 `bplo` 已失效。


## 后续 OPPO 蓝牙 Pro 验证

用户换用 OPPO 后，正常包 setup 成功，但整个商品查询返回 `3 / Billing Unavailable`，购买列表为空；这不同于前述三星已解决的商品级 `PRODUCT_NOT_FOUND`。切换已有测试购买账号并重启 Play 进程后仍未恢复，最终已恢复 Play 原账号。没有新增订单或复制收据。普通包的蓝牙未解锁检查 3 项通过；解锁后的蓝牙表单、设备选择及实际组合组件改用独立 QA 包验证，结果不能替代真实购买/恢复。参见 [OPPO Pro 蓝牙记录](oppo-pro-bluetooth-validation-2026-09-11.md)。
