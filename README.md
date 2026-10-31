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
| 回血池（26 个行动 + cue） | ✅ 已实现 | 每个行动都写了「第一步怎么做」，降低启动成本 |
| 对话式入口（本地意图解析） | ✅ 已实现 | 一句话 → (情绪, 场景, 时间预算) → 约束推荐；非大模型，接口已留 |
| 主动提醒（JITAI） | ✅ 已实现 | AlarmManager 30 分钟一唤，`NudgePlanner` 决定是否打扰（间隔 ≥50 分钟） |
| 洞察（时段/榜单/心率下降率） | ✅ 已实现 | `InsightsEngine`，纯逻辑可测 |
| 导出 CSV | ✅ 已实现 | 系统分享面板，带 BOM，标题含逗号会转义 |
| 深夜模式 | ✅ 已实现 | 23:00–05:00 只推 REST/CALM/FUEL，候选不足退回全量 |
| Health Connect（手环历史步数/睡眠） | ✅ 已接入 | 只读；小米没写进来时如实显示「没有数据」，不编数 |
| 陪练码（跨设备同一场） | ✅ 已实现 | 8 位 Base36 短码，不联网、无服务端，两台设备各自本地算剩余时间，2 小时有效 |
| 语音输入 | ✅ 已实现 | 走系统 RecognizerIntent，App 自己不申请录音权限 |
| 手环历史数据（睡眠/全天心率） | ⏳ 未接入 | 需 Gadgetbridge 或厂商云 |
| 界面（v0.5 重做） | ✅ 已实现 | 暖白纸感 + 自绘图标 + 汉字分类贴纸，见 `ui/Look.kt` |

### 信号与权限矩阵（答辩会被问"数据哪来的"）

| 信号 | 来源 | 权限 | 拿不到时 |
|---|---|---|---|
| 心率 | 手环 BLE `0x180D/0x2A37` | 附近设备（蓝牙扫描/连接） | 只用行为信号，首页如实写"没连上手环" |
| 连续使用 / 近 1 小时屏幕 | `UsageStatsManager` | 使用情况访问（在系统设置里开） | 退回演示数据滑杆，UI 打"演示数据"角标 |
| 多久没起身走动 | `TYPE_STEP_COUNTER` 步数变化间隔 | 身体活动（Android 10+ 运行时权限） | 该维度跳过，不出依据文案 |
| 时段 | 系统时钟 | 无 | — |

所有信号都**只在本机计算**，不联网、不上传。

## 界面与视觉（v0.5 重做）

配色集中在 `ui/Look.kt`：纸白底 `#F7F4EF`、墨色字 `#201E1B`、描边 `#E3DBD0`，状态三档用陶土 `#BF5B3F` / 麦黄 `#B0842E` / 橄榄 `#5A7A4C`，主按钮深墨绿 `#3D5A4A`。

图标全部用 Compose `Canvas` 自绘（`Glyph(kind)`：清单 / 柱状 / 双人 / 气泡 / 折线 / 铃 / 钟 / 麦克风），不引任何图标库，省包体。行动分类用「一个汉字」的贴纸（动 / 静 / 聊 / 玩 / 歇 / 吃）：17dp 的线稿图标在 26 行列表里会糊成一团（脚印变两点、静心圆像加载圈），汉字贴纸在任何尺寸都一眼可辨。

刻意避开"深蓝黑底 + 霓虹薄荷绿 + 大 emoji"这套常见的生成式观感：一是这个应用白天在户外用得多，深色底在强光下更难读；二是那套观感太像模板，不像一个被人认真挑过颜色的个人工具。反馈页原来用 emoji 表情打分，v0.5 也换成了「没用 / 一般 / 还行 / 有用 / 很管用」五段文字选择。

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
├── logic/Recommender.kt   # 推荐：Thompson 采样 + 约束（时间/深夜/排除）+ 图谱统计
├── logic/IntentParser.kt  # 对话入口：本地意图解析（情绪/场景/时间预算）+ 回复生成
├── logic/InsightsEngine.kt# 时段画像、有效榜单、心率下降率、CSV 导出
├── logic/NudgePlanner.kt  # 决定"要不要主动打扰"的纯逻辑（有单测）
├── notify/Reminders.kt    # 通知渠道、AlarmManager 调度、广播接收器
├── data/Store.kt          # SharedPreferences + org.json 持久化
├── data/Repo.kt           # 默认行动池 + 全局状态
├── data/ScreenUsage.kt    # 手机使用统计取数（UsageStatsManager）
├── data/SessionMath.kt    # 由事件流算"连续使用/前台时长"的纯逻辑（有单测）
├── data/ActivityTracker.kt# 计步器 → 久坐时长（有单测）
├── ble/HeartRateClient.kt # BLE HRS 扫描/连接/订阅/解析
├── ui/App.kt              # Compose UI（七个页面，手写导航）
├── ui/ChatScreen.kt       # 对话页
└── ui/InsightsSection.kt  # 洞察区 + 导出 + 提醒开关
```

## 测试

共 76 个 JVM 单元测试，`.\gradlew.bat testDebugUnitTest` 全绿：

- `logic/StateEngineTest.kt`：12 个，各信号扣分档位、缺失降级、档位阈值、五信号全报警 = 25 分
- `logic/RecommenderTest.kt`：8 个，空池、limit、同档位过滤、Thompson 收敛方向、探索加成、统计口径
- `data/SessionMathTest.kt`：9 个，熄屏归零、5 分钟断线、跨小时窗口裁剪、上限截断、时间倒流
- `data/ActivityTrackerTest.kt`：5 个，未知返回 null、8 小时封顶、负值保护
- `logic/IntentParserTest.kt`：12 个，情绪/场景/中文数字时间预算、图书馆排除社交类、预算过小老实说没有
- `logic/RecommenderConstraintsTest.kt`：6 个，时间上限、排除列表、深夜优先与退回、约束致空不崩、收敛方向
- `logic/NudgePlannerTest.kt`：7 个，不该打扰的三种情况、久坐/低分模板、50 分钟冷却、关闭开关
- `logic/InsightsEngineTest.kt`：9 个，时段划分、有效率/心率下降率、榜单排序、CSV 表头与逗号转义

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
- `C:\Users\34243\Desktop\软件大赛\docs\02-功能需求清单.md`（PRD v0.3）
