# v0.27 构建、签名与安装

本版 versionCode 为 28，包名仍是 `com.example.blehr`。下载与文件校验以 [v0.27 Release](https://github.com/luyize0830/huixue-lab/releases/tag/v0.27) 和 [验证记录](v0.27-validation.md) 为准。

## 选择安装包

| 安装情况 | 文件 |
|---|---|
| 首次安装，或以后使用本项目正式发布签名 | `huixue-lab-v0.27-release.apk` |
| 已安装此前由本机 debug 签名生成的版本 | `huixue-lab-v0.27-debug-compat.apk` |

本轮确认历史 v0.25 与兼容包证书相同，并保留已有记录覆盖升级。其他电脑生成的 debug 签名可能不同，不能保证覆盖。正式包与兼容包不能互相覆盖；安装器提示签名冲突时停止安装，先从旧版导出业务备份。切换签名需要用户自行确认卸载再安装，并导入备份、重新设置权限和模型凭据。切勿为试包自动清空用户数据。

正式证书 SHA-256：`fd4fd5aec0eeeca4257f3069701d97de559af4c6edac0a42f8d421dc2dbe094c`。

兼容证书 SHA-256：`f8f84dc138d29104dd182a3db3d5ccaa6e977964b60fcf2579aeed5f124fda55`。

## 本地验证

安装 JDK 25、Android SDK Platform 37，并设置 JAVA_HOME、ANDROID_HOME。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain
node web-demo/selftest.js
python tools/check_delivery.py --require-unit-reports
```

设备安装应用和测试 APK 后运行：

```powershell
adb -s <设备序列号> install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s <设备序列号> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <设备序列号> shell am instrument -w com.example.blehr.test/androidx.test.runner.AndroidJUnitRunner
```

测试使用独立前缀的偏好与文件。完整结果应包含 `OK (7 tests)`，仅成功编译测试 APK 不代表执行通过。正式包另外在隔离模拟器验证安装、冷启动、推荐、暂停恢复和反馈；debug 测试无法覆盖 R8 发布优化问题。

## 正式签名

Gradle 从以下四个环境变量读取签名配置：

| 变量 | 内容 |
|---|---|
| HUIXUE_STORE_FILE | 发布密钥库文件位置 |
| HUIXUE_STORE_PASSWORD | 密钥库密码 |
| HUIXUE_KEY_ALIAS | 发布密钥别名 |
| HUIXUE_KEY_PASSWORD | 密钥密码 |

设置后执行 `gradlew assembleRelease --console=plain`，用 SDK 的 `apksigner verify --print-certs` 验证输出。未提供签名变量时，release 构建只能得到未签名产物，不能作为可安装交付包。当前构建使用全应用 R8 压缩和 Android 标准优化规则；Compose 额外堆栈映射关闭，常规 R8 mapping 仍生成。

本次持久密钥保存在当前用户主目录的 `.android/huixue-lab-release/`，未进入仓库、桌面源码包或公开附件。维护者应将密钥库和凭据加密离线备份；不要为下一版重新生成密钥，也不要把 signing.json 上传到 GitHub。密钥丢失将影响同签名覆盖升级。

## 发布顺序

1. 完成测试，记录实现提交、APK 字节数、SHA-256、实际设备验证和未验证项。
2. 同步 README、03–08、版本验证记录及 evidence 清单，执行一致性检查。
3. 提交并推送 main，确认 CI 通过；创建版本 Release。
4. 附正式 APK、兼容 APK、截图证据 ZIP、文档 ZIP、演示视频和 SHA256SUMS.txt。
5. 同步桌面交接包。旧发布和历史资料保留，不把旧版截图当成本版结果。

签名私钥、模型密钥、未裁剪的私人页面、设备序列号和本机绝对路径不进入公开证据。
