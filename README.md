# 回血实验室 · Android 工程

《回血实验室》是"第二十届全国大学生软件创新大赛"参赛作品（赛题四：AI＋健康）的原型工程。

一句话定位：**手环读状态 → 状态下滑时推荐 1 个 5 分钟能做完的小行动 → 做完打 3 秒分 → 长期积累"哪种回血方式对我真的有效"的个人图谱。**

## 当前状态

| 模块 | 状态 | 说明 |
|---|---|---|
| BLE 手环心率（标准 HRS） | ✅ 已跑通 | 小米手环 9 Pro 实测可读，见 `docs/00-技术验证-手环心率.md` |
| 手机使用统计（连续使用/前台时长） | ✅ 已实现 | 系统 `UsageStatsManager`，需"使用情况访问"授权 |
| 久坐检测（步数变化间隔） | ✅ 已实现 | 手机计步器 `TYPE_STEP_COUNTER`，需"身体活动"权限 |
| 状态评估（可解释规则） | ✅ 已实现 | 心率抬升 / 连续专注 / 时段 / 屏幕时间 / 久坐 五项信号，每条扣分都有依据文案 |
| 推荐（Thompson 采样） | ✅ 已实现 | 每个"行动 × 档位"独立维护 Beta 后验，冷启动带探索加成 |
| 行动倒计时 + 手机振动 | ✅ 已实现 | 手环振动需厂商私有协议，暂不可用 |
| 主观反馈 + 图谱统计 | ✅ 已实现 | 1-5 分 + 前后心率差，本地存储 |
| Health Connect（手环历史步数/睡眠） | ⏳ 未接入 | 依赖小米是否写入 HC，先做可靠的本机信号 |
| 手环历史数据（睡眠/全天心率） | ⏳ 未接入 | 需 Gadgetbridge 或厂商云，见技术验证文档 |

### 信号与权限矩阵（答辩会被问"数据哪来的"）

| 信号 | 来源 | 权限 | 拿不到时 |
|---|---|---|---|
| 心率 | 手环 BLE `0x180D/0x2A37` | 附近设备（蓝牙扫描/连接） | 只用行为信号，首页如实写"没连上手环" |
| 连续使用 / 近 1 小时屏幕 | `UsageStatsManager` | 使用情况访问（在系统设置里开） | 退回演示数据滑杆，UI 打"演示数据"角标 |
| 多久没起身走动 | `TYPE_STEP_COUNTER` 步数变化间隔 | 身体活动（Android 10+ 运行时权限） | 该维度跳过，不出依据文案 |
| 时段 | 系统时钟 | 无 | — |

所有信号都**只在本机计算**，不联网、不上传。

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
├── data/ScreenUsage.kt    # 手机使用统计取数（UsageStatsManager）
├── data/SessionMath.kt    # 由事件流算"连续使用/前台时长"的纯逻辑（有单测）
├── data/ActivityTracker.kt# 计步器 → 久坐时长（有单测）
├── ble/HeartRateClient.kt # BLE HRS 扫描/连接/订阅/解析
└── ui/App.kt              # Compose UI（六个页面，手写导航）
```

## 测试

共 34 个 JVM 单元测试，`.\gradlew.bat testDebugUnitTest` 全绿：

- `logic/StateEngineTest.kt`：12 个，各信号扣分档位、缺失降级、档位阈值、五信号全报警 = 25 分
- `logic/RecommenderTest.kt`：8 个，空池、limit、同档位过滤、Thompson 收敛方向、探索加成、统计口径
- `data/SessionMathTest.kt`：9 个，熄屏归零、5 分钟断线、跨小时窗口裁剪、上限截断、时间倒流
- `data/ActivityTrackerTest.kt`：5 个，未知返回 null、8 小时封顶、负值保护

BLE 解析与权限路径无法用 JVM 单测覆盖，靠真机验证（见 `docs/`）。

## 已知限制（写进参赛文档时必须如实说明）

1. **演示数据仍保留**：未授权"使用情况访问"时用滑杆代替屏幕信号，UI 明确打"演示数据"角标；授权后自动切换。
2. **静息基线**：当前用"本次会话最低心率"近似，不是设备提供的真实静息心率。
3. **久坐只认"步数变化"**：站着不动会被算作没走动；这是手机的物理限制，不是 bug。
4. **手环振动不可用**：标准 GATT 没有振动服务，需厂商私有协议或通知镜像。
5. **仅支持标准心率服务**：不读睡眠/压力/血氧等厂商专有数据。
6. 包名 `com.example.blehr` 与项目目录 `BleHrDemo` 是技术验证阶段的遗留，正式版会改。

## 相关文档

- `C:\Users\34243\Desktop\软件大赛\docs\00-技术验证-手环心率.md`
- `C:\Users\34243\Desktop\软件大赛\docs\01-选题说明.md`
- `C:\Users\34243\Desktop\软件大赛\docs\ARCHITECTURE.md`（架构与关键决策）