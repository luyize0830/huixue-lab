# v0.28 构建、签名与安装

versionCode 29，包名 `com.example.blehr`。当前发布见 [v0.28 Release](https://github.com/luyize0830/huixue-lab/releases/tag/v0.28)，结果见 [验证记录](v0.28-validation.md)。

## 选择安装包

| 安装情况 | 文件 |
| --- | --- |
| 首次安装，或已有正式发布签名版本 | `huixue-lab-v0.28-release.apk` |
| 此前由本机 debug 签名生成的版本 | `huixue-lab-v0.28-debug-compat.apk` |

两种证书与 v0.27 一致；本版未在真实用户手机重新验证覆盖升级。不同签名不能互相覆盖。签名冲突时先停止安装并备份，不为试包自动卸载或清空用户数据。

正式证书 SHA-256：`fd4fd5aec0eeeca4257f3069701d97de559af4c6edac0a42f8d421dc2dbe094c`。

兼容证书 SHA-256：`f8f84dc138d29104dd182a3db3d5ccaa6e977964b60fcf2579aeed5f124fda55`。

## 验证

JDK 25、Android SDK Platform 37，并设置 JAVA_HOME、ANDROID_HOME。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain
node web-demo/selftest.js
python tools/check_delivery.py --require-unit-reports
```

隔离设备中安装 Debug 与 AndroidTest APK 后运行：

```powershell
adb -s <设备序列号> shell am instrument -w com.example.blehr.test/androidx.test.runner.AndroidJUnitRunner
```

当前完整结果为 `OK (14 tests)`。其中平台 DOCX 内容提供器测试要求 API 29+；本轮使用 API 37，不能把结果外推到所有旧系统。测试使用隔离偏好及临时文件。正式包须另验安装、冷启动、文档选择、预览、转屏、保存与撤销；Debug 测试不代替 R8 发布结果。

## 正式签名

Gradle 读取 `HUIXUE_STORE_FILE`、`HUIXUE_STORE_PASSWORD`、`HUIXUE_KEY_ALIAS`、`HUIXUE_KEY_PASSWORD`，配置后执行 `gradlew assembleRelease --console=plain`。未提供时产物未签名，不能作为可安装发布包。

沿用用户主目录 `.android/huixue-lab-release/` 的已有持久密钥；不能为下一版本重新生成证书，也不将密钥或 signing.json 放进源码、桌面交接包或 Release。维护者应加密离线备份。

## 发布顺序

1. 完成必要测试与正式包检查，记录实现提交、实际 APK 字节数和 SHA-256。
2. 同步 README、docs/03–08、验证记录、证据清单和使用说明，执行一致性检查。
3. 提交并推送 main，确认 CI；发布版本 Release。
4. 附正式 APK、兼容 APK、证据 ZIP、文档 ZIP、功能演示短片及 SHA256SUMS.txt。
5. 同步桌面交接包，保留历史版本，不把旧证据当作本版结果。

本版使用入口为「我的 → 她的性格 → 导入设定文档」。TXT/MD/DOCX 的范围与示例见 [设定文档说明](personas/README.md)。真实 DeepSeek 风格一致性、本版真机升级和 R1 其他规划功能保持未验证/未实现。
