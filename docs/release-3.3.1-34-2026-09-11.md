# Google Play 3.3.1（34）发布记录

2026-09-11 完成签名、上传和发布准备。2026-09-14 用户批准具体隐私补充说明后，已发布并核验公开页面，正式版 34 和 Data Safety 两项更改已通过快速检查，**已进入 Google Play 审核**。自管式发布关闭，审核通过后自动上线；当前尚未确认新版本上线。

- 应用：`com.github.xckevin927.android.battery.widget`，正式 `:app:release`，minSdk 21 / targetSdk 36。
- 版本：3.3.1 / 34。发布前正式版为 3.2 / 32，内部测试为 3.3 / 33；34 为新版本。
- AAB：`play/artifacts/3.3.1-34/battery-widget-3.3.1-34.aab`。
- SHA-256：`0f558b800395c092f2485b8edb1bc8d6b24e0103a1e7bf44ffa753c53363fe0e`，与 Play 上传响应一致。
- 上传证书 SHA-256：`87:BF:F4:26:94:A2:E1:0C:9B:71:81:EF:25:08:8D:74:B1:42:18:4F:F7:00:6C:F5:7B:A7:EE:4B:78:4C:66:4D`，与历史上传证书一致。JAR 签名、bundletool 校验及 Play edit validation 通过。
- 本次执行单元测试、Release Lint、Release Bundle：41 个测试通过，0 失败/错误/跳过；Lint 0 错误、225 警告。既有实机覆盖见 [OPPO 微件修复验证](oppo-widget-fixes-2026-09-11.md) 和 [计费验证](billing-validation-2026-09-11.md)。本次发布未重跑全部真机流程。
- 最终 DEX 包含 HiddenApiBypass、正常 BillingClient 和 PurchaseSecurity，不含 FreeReflection、QA 权益或测试类。Pro 商品 `battery_widget_pro` / `buy` 保持 ACTIVE，US$1.99 一次性购买。
- Console 确认 171 个目标国家/地区、100% 发布，设备支持范围与 32 相同，自管式发布关闭。

## 校验提示

gplay 离线扫描器仍报告 WorkManagerGcmService 缺少 exported；最终 AAB 实际声明 `@bool/enable_gcm_scheduler_default`，默认 true、API 23 起 false，并受 `BIND_NETWORK_TASK_SERVICE` 保护。这是资源引用解析误报；原始报告和核验解释均已保留。

Console 两项警告为当前包有意不申请 AD_ID（旧测试轨道仍涉及广告 ID 声明）、未提供混淆映射（本项目关闭混淆，故无 mapping）。本次未改变相关声明或开启混淆。

快速检查另提示 HiddenApiBypass 使用不受支持的 API，可能受 ART 更新影响。按用户保留反射能力及忽略此类提醒的既有要求，选择“发布这些更改内容比解决此问题更为重要”并确认继续；Console 已解除这项阻断。更换库不代表消除了非 SDK API 的兼容性风险。

## 隐私补充与送审

新增 Pro 后，旧隐私政策未涵盖购买收据/购买事件。本次已保存 Data Safety 的“财务信息 → 交易记录”：收集、非共享、非临时、可选、用途分析；其余五类声明保留。Google Play 的支付卡信息由 Play 直接处理，未申报为应用持有银行卡信息。

准备的补充说明在 `play/artifacts/3.3.1-34/privacy-pro-supplement.txt`，包括可选的一次性 Pro、Google Play 付款、本地且不备份的签名收据、Firebase/Play 集成开启时的购买事件、蓝牙本地数据与普通偏好备份。

9 月 11 日 Google Sites 普通填充曾显示文字及“发布成功”，但公开 GET 和重新打开编辑器证实文本未持久化，因此未计为成功。自动审批随后要求用户批准具体公开隐私声明；9 月 14 日用户已明确同意。

9 月 14 日通过 Google Sites 编辑框的粘贴事件写入已批准原文，重新加载编辑器确认持久化后发布。公开 GET 核验五段文字全部一致，原隐私政策保留；证据为 `play/artifacts/3.3.1-34/privacy-verification.json`。原文 SHA-256：`dc983e862fb49185256f3e6413f8ce16da590867bd25f406c3a14501e051149e`。

发布概览确认待提交内容仅为正式版 34 全面发布及 Data Safety，随后确认“将更改内容送审”。Publisher API 返回 `RELEASE_LIFECYCLE_STATE_IN_REVIEW`。送审后的快速检查已结束，Console 明确显示“您的更改目前正在接受审核”；最终证据为 `play/artifacts/3.3.1-34/console-in-review-2026-09-14.txt`，时间见 `release-record.json`。送审时已上线正式版仍为 3.2（32）。

当前工作树原有改动已保留，本次未执行 Git 提交。已保存源码快照、patch、构建日志、产物身份、上传和 Console 记录；恢复时先回读 Play，勿重复上传或递增版本。
