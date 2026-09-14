# HiddenApiBypass 迁移记录 — 2026-09-11

用户明确要求以 LSPosed AndroidHiddenApiBypass 替换 FreeReflection，覆盖此前保留 FreeReflection 的要求。当前代码和本地制品已完成替换；OPPO 真机验证按用户要求留待后续，不能据本地构建结果认定耳机和手表问题已解决。

## 实现

- 依赖固定为 `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`，使用 `HiddenApiBypass`，未采用 LSPass。版本依据为 [上游 v6.1](https://github.com/LSPosed/AndroidHiddenApiBypass/tree/v6.1) 与 [Maven Central 元数据](https://repo.maven.apache.org/maven2/org/lsposed/hiddenapibypass/hiddenapibypass/maven-metadata.xml)。
- `Application.attachBaseContext` 调用 `ReflectUtil.init()`。Android 9 / API 28 以上在独立嵌套类中访问库，低版本继续普通反射；保留 minSdk 21、targetSdk 36。
- 使用 `setHiddenApiExemptions("")` 保持原先应用范围的豁免行为。初始化同步且每进程只执行一次，捕获运行时和链接异常；库不可用时不反复触发失败的类初始化。
- 方法读取先走普通反射；仅找不到方法且库可用时，通过 HiddenApiBypass 按完整参数类型查找。目标方法只执行一次，异常返回未知，不因调用失败重复执行。
- 真机诊断输出改为 `reflectionBackend=HiddenApiBypass` 与 `hiddenApiAccessEnabled`。未改变购买验证、权益逻辑或蓝牙读取业务策略。

API 调用核对的是 [v6.1 实际源代码](https://github.com/LSPosed/AndroidHiddenApiBypass/blob/v6.1/library/src/main/java/org/lsposed/hiddenapibypass/HiddenApiBypass.java)。未添加永久仓库、修改依赖信息上报或额外混淆规则；库自带 consumer rules。

## 最终本地验证

| 检查 | 结果 |
| --- | --- |
| Debug APK、AndroidTest APK、Release AAB | 全部构建成功；测试 APK 仅编译，未运行真机诊断 |
| JVM 单测 | 36 项通过，0 failures / 0 errors / 0 skipped；最后一次代码修改后实际执行 |
| 反射回归 | 无初始化仍能普通读取、完整参数类型及 null 重载匹配、失败目标不重复执行 |
| Release Lint | 0 errors / 0 fatal / 223 warnings |
| APK / AAB DEX | 存在 HiddenApiBypass 类定义，不存在 `me.weishu` 类定义；APK helper 确实调用新库两个 API |
| APK 身份 | 正常包名，versionCode 33 / versionName 3.3，minSdk 21 / targetSdk 36 |
| APK 签名 | 验证通过，debug 证书与已备份的原 OPPO APK 相同 |
| AAB 签名 | 未签名，仅本地构建验证用 |
| 独立代码复核 | 无阻断问题；包含初始化幂等与失败类访问保护的最终复核 |

首次获取新依赖遇到本机 Java TLS 信任链问题。通过正常 TLS 校验的 curl 下载 Maven Central 官方文件，核对 AAR SHA-256 与官方 module 元数据一致后，以 `/private/tmp/battery-widget-hiddenapibypass-6.1/verified-dependency.init.gradle` 添加仅本轮构建使用的临时仓库。未禁用 TLS 或改动系统信任配置。AAR SHA-256：`e3161dd21c97a4540b1698a33f7062aeaa1450008e1e2176070e5380f7a6324c`。

## 当前制品

目录：`play/artifacts/3.3-33/hidden-api-bypass-2026-09-11/`（已被 Git 忽略）。

- [主 APK](../play/artifacts/3.3-33/hidden-api-bypass-2026-09-11/battery-widget-3.3-33-hiddenapibypass-debug.apk)：SHA-256 `ca57d71b91f3eedf39f687e1504682b55f62c7292559d110e06eed39c68bbffa`。
- 测试 APK：`battery-widget-3.3-33-hiddenapibypass-androidTest.apk`。
- 未签名 AAB：`battery-widget-3.3-33-hiddenapibypass-unsigned.aab`；SHA-256 `03903e9fa3cea94ba87c864af9dfe021be694326bcde64c0c0880ba661485c6b`。
- 最终构建日志：`build-validation-final.log`；完整哈希与结果：`validation.json`；依赖来源：`dependency.json`。
- 制品证据：`apk-badging.txt`、`apk-signature.txt`、`apk-reflection-backend.smali`。

本轮未操作手机、安装 APK、上传或修改 Google Play。此前内部轨道的版本 33 不包含本次迁移；未来上传需使用新的可用 versionCode。

## 待真机验收

继续遵循 [OPPO 测试顺序与基线](oppo-bluetooth-validation-2026-09-11.md#下次连接后的执行顺序)：安装本记录中的新包，核对初始化、耳机/手表连接状态和真实电量，再测断连重连、组件及后台更新。保留原组件 ID 7/8，并恢复此前尚未收尾的 USB 常亮设置原值 `0`。

普通 JVM 测试不验证 Android ART 的隐藏 API 可用性；诊断执行成功也不等于实际电量读取通过。OPPO / Android 16 的结论须以新包真机结果为准。

## 后续真机验证

用户随后重新连接 OPPO 并授权继续测试。新包已安装，隐藏 API 初始化成功，Enco Free4 恢复连接和 100% 电量读取，与系统左右耳 100% 一致；华为手表连接可识别、电量仍未知，用户确认这是已知设备限制。普通包 Pro 蓝牙未解锁场景 3 项通过。后续结果和验证边界见 [OPPO 蓝牙与 Pro 验证](oppo-pro-bluetooth-validation-2026-09-11.md)。
