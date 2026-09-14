# OPPO 蓝牙兼容修复与待复测记录 — 2026-09-11

## 当前接续状态

用户随后明确要求用 LSPosed AndroidHiddenApiBypass 替换 FreeReflection。当前代码使用 HiddenApiBypass 6.1；最新制品与验证记录见 [HiddenApiBypass 迁移记录](hidden-api-bypass-migration-2026-09-11.md)。下面的 3.2.2 包和结果仅是过渡修复历史，不再作为待安装的当前版本。

后续手机重新连接，新包已安装并完成首次真实读取：耳机已连接且 100%，手表已连接但电量未知。用户确认手表电量不可读是已知限制，转为 Pro 蓝牙功能测试；接续记录见 [OPPO 蓝牙与 Pro 验证](oppo-pro-bluetooth-validation-2026-09-11.md)。本报告以下仍保留过渡阶段的基线与未完成清单。

## 过渡修复结果（已被替换）

已定位并修复 targetSdk 升级后 FreeReflection 初始化失败、备用反射调用错误这两个问题。本地 Debug/测试 APK 构建成功，35 项 JVM 测试通过（无失败、错误或跳过），Debug Lint 为 0 errors / 222 warnings。最终 APK 中已核验新的 FreeReflection 只读 DEX 加载逻辑。

**用户要求先修复、稍后再用真机测试。修复 APK 尚未安装到 OPPO，耳机/手表兼容性仍待真机验收。** 当前手机最后安装的是修复前 33/3.3。未修改或发布 Google Play 版本；已在内部轨道上传的旧 33 AAB 不包含本次修复。

## 已获取的真机基线

- OPPO PKT110，Android 16 / API 36，ColorOS 16.1。
- 系统蓝牙开启，已配对设备中包含 OPPO Enco Free4 和 HUAWEI WATCH GT 4；系统连接记录显示耳机和手表连接，用户也确认两者正在连接且旧版能读取耳机电量。
- 原应用 26/2.6、targetSdk 33、debug 签名。已私有备份原 APK 和偏好，保留数据覆盖安装修复前的 33/3.3、targetSdk 36。
- `BLUETOOTH_CONNECT` 已授权；通知权限未授权。持续监测初始关闭，未在本次故障定位中开启。
- 原桌面实例为手机组件 ID 7、蓝牙组件 ID 8；后续测试须保留这两个实例。不要使用三星测试的组件身份作为 OPPO 证据。
- App 显示已连接设备 0，用户确认与系统实际连接不符。
- 15:00 的普通 App 冷启动日志明确出现以下因果链，证据为私有目录中的 `app-cold-start-reflection.txt`：
  1. FreeReflection 3.1.0 的直接豁免失败后尝试加载 bootstrap DEX；系统报 `SecurityException: Writable dex file ... is not allowed`。
  2. `ReflectUtil.invoke` 备用分支查找不带参数的 `Class.getDeclaredMethod`，报 `NoSuchMethodException: java.lang.Class.getDeclaredMethod []`。
  3. 连接状态和电量读取返回空值，Repository 将没有 GATT 连接兜底的设备判断为断开，并清除其电量缓存。

Android 14 起，targetSdk ≥ 34 的应用动态加载 DEX 等文件必须先设为只读。旧版 targetSdk 33 未触发这一要求，这与本次升级后的表现一致。[Android 官方说明](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)。

## 修复内容

- `app/build.gradle`：FreeReflection 从 3.1.0 升至固定版本 3.2.2；保留 `Reflection.unseal(context)` 在 `Application.attachBaseContext` 中实际执行。上游 3.2.0 起包含 target Android U 的只读 DEX 兼容修复。[上游版本记录](https://github.com/tiann/FreeReflection/tags)。
- `ReflectUtil.java`：初始化失败时先尝试普通反射，避免丢弃仍可访问的方法；备用 meta-reflection 修正为查找 `(String, Class[])` 签名，并保留调用参数。
- `ReflectUtilTest.java`：覆盖初始化失败仍可读取，以及初始化成功/失败时多参数调用保持正确。隔离 JVM 对照探针运行旧实现失败、新实现通过；该探针只证明 Java 调用逻辑，不证明 Android 隐藏 API 已可用。
- `LiveBluetoothDiagnosticsTest.kt`：新增显式 `liveBluetooth=true` 才启用的真实设备诊断，读取初始化结果、反射返回值、公共 Profile 连接列表和 Repository 快照。只输出设备地址的哈希，不注入设备、电量或权益。它会调用现有 Repository 刷新，因此允许发生生产代码本身的 GATT 读取；诊断完成不能当作硬件验收通过。

## 本地验证与制品

| 检查 | 结果 |
| --- | --- |
| `assembleDebug` / `assembleDebugAndroidTest` | 成功；测试 APK 仅编译，未运行新增蓝牙诊断 |
| `testDebugUnitTest` | 本轮实际执行，35 tests / 0 failures / 0 errors / 0 skipped |
| `lintDebug` | 0 errors / 222 warnings；与旧报告的 release Lint 口径不同 |
| 上游依赖 | HTTPS 获取的 AAR SHA-256 与上游 Gradle module 元数据一致 |
| APK 反编译 | `Reflection.unsealByDexFile` 在 `DexFile` 加载前调用 `File.setReadOnly()` |
| APK 身份与签名 | 正常包名，33/3.3，minSdk 21，targetSdk 36；签名验证通过，与原 OPPO APK 证书相同 |
| 独立代码复核 | 无阻断问题；真机隐藏 API 访问和实际设备读数仍待确认 |

取证目录：`play/artifacts/3.3-33/oppo-bluetooth-2026-09-11/`（已被 Git 忽略）。

- 主 APK：`battery-widget-3.3-33-freereflection-fix-debug.apk`。
- SHA-256：`9446edc9e10b583a132835471837639966b56cdc9c30608ae4613e069ba66a17`。
- 测试 APK：`battery-widget-3.3-33-freereflection-fix-androidTest.apk`。
- 构建日志：`fix-build-validation-final.log`；结构化结果：`fix-validation.json`。

首次构建因本机 Java TLS 信任链无法下载新依赖失败，后续通过正常 HTTPS 校验的 curl 下载官方 3.2.2，再核对上游 SHA-256，使用临时、本次构建专用 Maven 仓库完成检查。没有禁用 TLS、改系统信任证书或把临时仓库写进项目配置。临时初始化脚本首轮 DSL 错误已修正，失败日志保留；最终构建成功。

## 下次连接后的执行顺序

1. 核对 OPPO 身份、锁屏和实际蓝牙连接；恢复或按新一轮测试临时设置 USB 常亮。原 `stay_on_while_plugged_in=0`，本轮临时请求 `usb`（2）后设备断开，尚未恢复，必须收尾归还原值。
2. 覆盖安装新迁移记录中的 HiddenApiBypass 主 APK 和测试 APK，冷启动确认初始化成功、两类已知异常消失。不要继续安装本报告上面的历史 FreeReflection 3.2.2 包。此前新增诊断安装因设备断开失败，不应记为已运行。
3. 运行 `LiveBluetoothDiagnosticsTest`，核对耳机/手表真实连接、框架电量与 GATT 结果，再对照系统蓝牙页面和 App。不能把“诊断 OK”当作“读到电量”。
4. 补测各设备断连/重连、读取期间断连、蓝牙总开关恢复；确认旧百分比不会在断开后继续显示。
5. 测试别名、排序、隐藏、组件设备选择与点击，保留原组件 ID 7/8；之后恢复业务偏好。
6. 在本机可验证的 Pro 权益下测试真实蓝牙低电量提醒、去重与免打扰。不要复制三星收据或用 QA 权益宣称 Play 恢复成功；实际购买仍只允许明确标记不扣费的测试方式。
7. 补测监测开启/关闭时的前台、后台与熄屏连接事件；自然长待机单独记录连续样本与断档，不能把短时存活当成长待机通过。

本轮没有完成耳机/手表电量正确性、断连/重连、蓝牙组件、低电量提醒或长待机验收。完整卸载重装、切换购买账号、Play 签名安装包交易等此前未覆盖项目也仍保持待验证。
