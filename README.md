# 回血实验室 · Android 工程

《回血实验室》是"第二十届全国大学生软件创新大赛"参赛作品（赛题四：AI＋健康）的原型工程。

一句话定位：**手环读状态 → 状态下滑时推荐 1 个 5 分钟能做完的小行动 → 做完打 3 秒分 → 长期积累"哪种回血方式对我真的有效"的个人图谱。**

## 当前状态

| 模块 | 状态 | 说明 |
|---|---|---|
| BLE 手环心率（标准 HRS） | ✅ 已跑通 | 小米手环 9 Pro 实测可读，见 `docs/00-技术验证-手环心率.md` |
| 状态评估（可解释规则） | ✅ 已实现 | 心率抬升 / 连续专注 / 时段 / 屏幕时间 四项信号，每条扣分都有依据文案 |
| 推荐（Thompson 采样） | ✅ 已实现 | 每个"行动 × 档位"独立维护 Beta 后验，冷启动带探索加成 |
| 行动倒计时 + 手机振动 | ✅ 已实现 | 手环振动需厂商私有协议，暂不可用 |
| 主观反馈 + 图谱统计 | ✅ 已实现 | 1-5 分 + 前后心率差，本地存储 |
| Health Connect（真实步数/屏幕时长） | ⏳ 未接入 | 目前用"演示数据"滑杆替代 |
| 手环历史数据（睡眠/全天心率） | ⏳ 未接入 | 需 Gadgetbridge 或厂商云，见技术验证文档 |

## 构建

需要 JDK 17 + Android SDK（compileSdk 37，minSdk 26）。

```bash
# 命令行构建（Windows，Android Studio 自带 JBR）
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug --console=plain

# 单元测试
.\gradlew.bat testDebugUnitTest --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

**安装到小米/HyperOS 设备**：Android Studio 的 Run 会报 `INSTALL_FAILED_USER_RESTRICTED`，请直接用 adb：

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

（`-t` 是因为 debug 包带 testOnly 标记；`-r` 覆盖安装。）

## 目录结构

```
app/src/main/java/com/example/blehr/
├── MainActivity.kt        # 入口：权限申请、BLE 客户端生命周期、静息基线维护
├── model/Models.kt        # 领域模型（纯 Kotlin，无 Android 依赖）
├── logic/StateEngine.kt   # 状态评估：可解释规则引擎
├── logic/Recommender.kt   # 推荐：Thompson 采样 + 图谱统计
├── data/Store.kt          # SharedPreferences + org.json 持久化
├── data/Repo.kt           # 默认行动池 + 全局状态
├── ble/HeartRateClient.kt # BLE HRS 扫描/连接/订阅/解析
└── ui/App.kt              # Compose UI（六个页面，手写导航）
```

## 测试

- `app/src/test/java/com/example/blehr/logic/StateEngineTest.kt`：9 个用例，覆盖各信号的扣分档位、降级路径、档位阈值
- `app/src/test/java/com/example/blehr/logic/RecommenderTest.kt`：8 个用例，覆盖空池、limit、同档位过滤、Thompson 收敛方向、探索加成、统计口径

BLE 解析与权限路径无法用 JVM 单测覆盖，靠真机验证（见 `docs/`）。

## 已知限制（写进参赛文档时必须如实说明）

1. **演示数据**：步数/屏幕时长目前来自滑杆，不是真实系统数据；接入 Health Connect 后替换。
2. **静息基线**：当前用"本次会话最低心率"近似，不是设备提供的真实静息心率。
3. **手环振动不可用**：标准 GATT 没有振动服务，需厂商私有协议或通知镜像。
4. **仅支持标准心率服务**：不读睡眠/压力/血氧等厂商专有数据。
5. 包名 `com.example.blehr` 与项目目录 `BleHrDemo` 是技术验证阶段的遗留，正式版会改。

## 相关文档

- `C:\Users\34243\Desktop\软件大赛\docs\00-技术验证-手环心率.md`
- `C:\Users\34243\Desktop\软件大赛\docs\01-选题说明.md`
- `C:\Users\34243\Desktop\软件大赛\docs\ARCHITECTURE.md`（架构与关键决策）