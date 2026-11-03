# 《回血实验室》架构设计

> 本文保留 v0.1 架构底稿（2026-10-30）；当前修复与验证以 `v0.23-validation.md` 为准。
> 对应代码：`仓库根目录`
> 说明：本文是参赛文档《项目开发文档》的架构章节底稿，也是《技术研究报告》的素材来源。

## 1. 设计目标与约束

| 目标 | 具体约束 | 对应赛题要求 |
|---|---|---|
| 设备无关 | 有手环用真信号，没手环用手机信号，两者都不崩 | 真实场景可用性 |
| 可解释 | 每条结论必须能追溯到一条"依据" | 知情权 |
| 端侧优先 | 状态评估与推荐全部本地计算，不上传 | 隐私 |
| 用户可控 | 记录可查看、可删除；推荐只是建议，不代替决定 | 控制权 |
| 快速验证 | 不引入重型框架，7 天内能跑通闭环 | 工程效率 |

明确**不做**的事：不做医学诊断、不做情绪识别、不做需要账号体系的社交。

## 2. 分层架构

```
┌─────────────────────────────────────────────────┐
│  UI 层（Jetpack Compose）                        │
│  ui/App.kt：Home / Recommend / Timer /           │
│             Feedback / Graph / Pool              │
│  手写 sealed class Screen + mutableStateOf 导航   │
└───────────────▲─────────────────────────────────┘
                │ 只读 state + 回调
┌───────────────┴─────────────────────────────────┐
│  领域层（纯 Kotlin，无 Android 依赖）             │
│  logic/StateEngine.kt   信号 → 状态分 + 依据      │
│  logic/Recommender.kt   (行动×档位) → 排序        │
│  model/Models.kt        领域模型                 │
└───────────────▲─────────────────────────────────┘
                │
┌───────────────┴─────────────────────────────────┐
│  数据层                                          │
│  data/Repo.kt          内存态（mutableStateList）│
│  data/Store.kt         SharedPreferences + JSON  │
│  data/ScreenUsage.kt   系统使用统计取数           │
│  data/SessionMath.kt   事件流 → 连续/前台时长（纯）│
│  data/ActivityTracker.kt 计步器 → 久坐时长        │
│  ble/HeartRateClient.kt  BLE HRS 采集            │
└─────────────────────────────────────────────────┘
```

**为什么领域层不带 Android 依赖**：`StateEngine` 和 `Recommender` 只用 `model` 里的纯数据类，因此可以直接用 JVM 单元测试（`app/src/test/`）验证，不需要 Robolectric 或真机。取数层也做了同样处理：`ScreenUsage` 只负责把 `UsageEvents` 翻译成 `List<UsageEvent>`，真正的算法在纯对象 `SessionMath` 里，所以"熄屏归零 / 5 分钟断线 / 跨小时裁剪"这些口径能被 9 个测试固定住。这是本项目在原型阶段就有 34 个测试用例的前提。

## 3. 数据流

```
[BLE HRS notify] ──▶ HeartRateClient.onHeartRate(bpm)
                            │
                            ▼
              MainActivity 维护会话窗口 hrSamples(30)
                            │  restingHr ≈ min(窗口)
                            │
[UsageStatsManager] ──▶ ScreenUsage.snapshot() ──▶ SessionMath.compute()
                            │                    连续使用 / 近 1 小时前台
[TYPE_STEP_COUNTER] ──▶ ActivityTracker ──▶ 多久没起身走动
                            │
                            ▼
      ┌─────────────── SensorSnapshot ───────────────┐
      │ heartRate / restingHr                        │
      │ focusMinutes / screenMinutesLastHour / hour  │
      │ sedentaryMinutes（拿不到就是 null）           │
      └───────────────────┬──────────────────────────┘
                          ▼
                 StateEngine.assess() → StateAssessment(band, score, headline, reasons)
                          ▼
        Recommender.recommend(band, pool, records) → List<Recommendation>(3)
                          ▼                           │
                用户选一个 → 倒计时 → 反馈(1-5 分) ──────┘
                          ▼
              ExperimentRecord 写入 Repo + Store
                          ▼
        Recommender.stats() → 回血图谱（尝试次数/平均分/心率变化）
                          ▼
              下一轮 recommend 的后验更新（学到"对你有效"）
```

闭环的关键在于**最后一步回到最前面**：每次反馈都会改变下一次推荐的概率分布，这是"实验助手"和"打卡 App"的本质区别。

## 4. 关键决策与理由

### 4.1 用规则引擎而不是分类模型
- **决策**：`StateEngine` 用阈值规则（心率抬升 ≥15 扣 22 分等），产出 `score` + `reasons`。
- **理由**：①初赛阶段样本量为 0，无法训练个性化模型；②规则天然可解释，`reasons` 直接就是"为什么给你这个结论"的 UI 文案；③阈值可写成配置，后续换成学习到的参数而不改架构。
- **代价**：阈值是经验值，对个体的适配靠"用户自己校准"（反馈环节）而非模型自适应。

### 4.2 推荐用 Thompson 采样而不是"取平均分排序"
- **决策**：每个"行动 × 精力档位"独立维护 `Beta(α, β)`，成功定义为评分 ≥4，每次推荐从后验采样排序。
- **理由**：①天然带探索——样本少的行动后验宽，仍有机会被推荐，避免冷启动后永远只推最初那三样；②样本多且效果差时会被自然压下；③计算量极小（3-9 个行动，端侧瞬时完成）；④每个行动都能说出"你试过几次、平均几分"，可解释。
- **实现细节**：`sampleBeta` 用 Marsaglia–Tsang 方法生成 Gamma 分布再归一化；`n == 0` 时额外加 0.18 的探索加成（否则 Beta(1,1) 的均值 0.5 会被高成功率的行动完全压制）。
- **备选方案**：ε-greedy（更简单但探索不可控）、UCB（需要置信区间参数，对非平稳偏好不友好）。

### 4.3 设备无关：手环是"增强"不是"前提"
- **决策**：`SensorSnapshot` 所有字段可空，缺失时 `StateEngine` 降级并如实说明（"没连上手环，这次只用行为信号判断"）。
- **理由**：评委或用户现场没有手环时产品必须还能跑；BLE 授权失败、设备被系统占用（小米运动健康占用连接）都是真实发生过的故障。
- **对应的 UI**：首页手环卡片写"手环（可选，有更好）"，未连接时倒计时页明确提示"这次不记录心率"。

### 4.4 手写导航而不是 Navigation Compose
- **决策**：`sealed class Screen` + `mutableStateOf`，六个页面。
- **理由**：原型期页面少、参数简单（`Timer(action)`、`Feedback(action, hrBefore)`），引入 navigation-compose 只会增加依赖与配置成本；迁移成本也低（每页是独立 composable）。

### 4.5 存储：SharedPreferences + JSON
- **决策**：`Store.kt` 用 `SharedPreferences` 存一个 JSON 数组（`records_v1`），带版本化 key。
- **理由**：记录量级是"每天几次、一年几千条"，远小于 Room 的适用场景；原型期零依赖、零迁移成本。
- **升级路径**：记录超过约 5000 条或需要按时间窗查询时换 Room，`Repo` 的接口不变。

## 5. 错误处理与降级

| 故障 | 检测 | 处理 |
|---|---|---|
| 蓝牙未开 / 无权限 | `hasPermissions()` + 适配器状态 | 状态栏提示，不阻塞主流程 |
| 设备连接失败 | `onConnectionStateChange` 非 SUCCESS | 提示 + 允许重扫 |
| 订阅失败（无 HRS 服务） | `onServicesDiscovered` 找不到 0x180D | 明确告知"这个设备不支持标准心率服务" |
| 页面销毁 | `onDestroy` | `client.close()` 释放 GATT，避免泄漏 |
| 数据缺失 | 字段可空 | 降级 + 文案说明，不抛异常 |
| 没有"使用情况访问"权限 | `ScreenUsage.hasAccess()` = false | 首页标"演示数据"，给"去授权"入口，滑杆兜底 |
| 没有"身体活动"权限 | `ActivityTracker.start()` 直接返回不可用 | 该维度跳过，不出依据文案，仍给授权入口 |
| 设备没有计步器 | `getDefaultSensor(TYPE_STEP_COUNTER)` = null | 同上，如实标记不可用 |
| 久坐时间未知 | `stillMinutes()` 返回 null | 不参与打分，不用 0 冒充"刚动过" |

## 6. 性能考量

- BLE 心率 notify 频率约 1 Hz，解析后仅更新一个 `Int` 状态，无额外分配；
- 推荐计算复杂度 O(行动数 × 记录数)，本项目规模下 <1 ms；
- Compose 侧用 `mutableStateOf` 细粒度更新，心率跳动不会导致整页重组（只有读该值的 composable 重组）；
- APK 体积 16.4 MB（含 Compose 运行时），后续可用 R8 + 资源压缩降到 8 MB 左右。

## 7. 隐私与合规

- 心率与实验记录**只存在本机** SharedPreferences，无网络上传；
- "回血图谱"页提供"清空我的记录"，一次点击删除全部实验数据；
- 不申请通讯录、短信、相册等无关权限；BLE 权限声明 `neverForLocation`，不索取位置信息；
- 状态结论的措辞刻意避开医学表述（不说"压力过大""焦虑"，只说"要不要现在回一点电"）。

## 8. 后续演进

1. **Health Connect**：接 `androidx.health.connect:connect-client`，补充手环历史步数/睡眠，用于建立真实静息基线（当前屏幕与久坐已用系统原生接口落地，不再依赖 HC）；
2. **手环历史数据**：通过 Gadgetbridge 或厂商云补全天心率与睡眠，用于建立真实静息基线；
3. **手环端提醒**：走通知镜像（第三方可写入通知，手环会振动），替代手机振动；
4. **OPPO 能力接入**：赛题四鼓励使用 OPPO 健康服务与互联开放能力，本架构通过 `SensorSnapshot` 抽象，新增数据源不影响领域层；
5. **记录可视化**：图谱页加"过去 14 天状态曲线"与"行动有效性排名"图表。

## 9. 测试策略

| 层 | 方式 | 覆盖 |
|---|---|---|
| 领域层 | JVM 单元测试（20 个用例） | 扣分档位、缺失降级、档位阈值、推荐收敛方向、探索加成、统计口径 |
| 取数逻辑 | JVM 单元测试（14 个用例） | 熄屏归零、5 分钟断线、跨小时窗口裁剪、上限截断、时间倒流、久坐未知返回 null |
| 数据层 | 手动验证（增删后重启仍存在） | 持久化 |
| BLE | 真机验证 | 扫描/连接/订阅/心率跳动（已通过） |
| 真机信号 | 截图 + UI 层级 dump 存档 | 使用统计/久坐授权后数值变化 |
| UI | 真机走查 + 截图存档 | 六页主流程 |

> 待补：`Store` 的损坏 JSON 恢复用例；久坐信号在"站着不动"场景下的误判边界。