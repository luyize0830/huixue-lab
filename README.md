# 回血实验室

面向大学生的个人微行动实验助手：手机和可选手环信号 → 可解释的状态 → 五分钟行动建议 → 计时与自评 → 个人记录与下一次推荐。

仓库拟用于第二十届全国大学生软件创新大赛，赛题四“AI＋健康”。报名、队伍和最终提交要求以主办方正式通知为准。

## 产品重规划（2026-11-27）

下一阶段聚焦「学习间隙的个人休息助手」：无手环、无模型 Key 也能真实使用，快速选一件 1–5 分钟的休息，再用个人反馈帮助下一次选择。规划先补数据来源与迁移，再重组为「现在 / 记录 / 我的」三页流程。

[产品规划总览](docs/product/README.md) · [需求与验收](docs/product/02-prd.md) · [实施任务](docs/product/04-delivery-plan.md) · [交互原型](docs/product/prototype/index.html)

R1 三页主流程和可信数据迁移仍待实现。用户随后确认的 [R2 陪伴设定与文档导入](docs/product/07-persona-import.md) 已独立交付为 v0.28。设计原型全部使用演示数据，不替代正式 App 或原有网页演示。

## v0.28：用户可导入陪伴设定

| 版本 | versionCode | JVM | 网页 | 原生设备 | 正式 APK |
|---|---|---|---|---|---|
| 0.28 | 29 | 285 例 / 32 类通过 | 24 例通过 | 隔离 Android 17 模拟器 14 例通过 | 4,641,482 字节 |

- F-50：TXT、MD、Word（.docx）本机导入，先预览、再放入草稿，保存后才生效。
- 最大 512 KB、正文 6000 字符；空文档、乱码、超限和不支持格式明确拒绝，不静默截断。
- 支持撤销导入草稿、撤销上次保存、恢复默认确认及未保存离开提示；转屏保留预览与草稿。
- 增加轻暧昧档，与朋友小夜、温柔陪伴一起供用户选择；历史预设保留。
- 设定与聊天记忆独立，当前设定会随模型聊天请求发送；真实 DeepSeek 语感本轮未验证。

[发布页](https://github.com/luyize0830/huixue-lab/releases/tag/v0.28) · [验证记录](docs/v0.28-validation.md) · [导入说明与模板](docs/personas/README.md)

## v0.27：真实状态接线与参赛验证（历史）

| 版本 | versionCode | JVM | 网页 | 原生设备 | 正式APK |
|---|---|---|---|---|---|
| 0.27 | 28 | 267例 / 31类通过 | 24例通过 | 7例在手机和模拟器各通过 | 4,625,098 字节 |

- 修复首页快照漏传心率、主动陪伴使用旧输入、凌晨推荐条件缺失；首页建议限制五分钟。
- 睡眠按昨夜窗口合并片段，修复Health Connect提供方，空数据保持未知。
- 新增F-43：首页推荐展示、选择、跳过与反馈关联；演示不计入真实接受率，支持CSV及备份。
- 网页改用同档Thompson采样，覆盖天数按实际日期计算。
- 完成设备持久化与首页接线回归，正式签名和旧版兼容包分别提供；CI检查文档数字。

[发布页](https://github.com/luyize0830/huixue-lab/releases/tag/v0.27) · [完整验证](docs/v0.27-validation.md) · [试用与演示方案](docs/13-参赛试用与演示方案.md)

## 安装与体验

首次安装或已有正式签名版使用 `huixue-lab-v0.28-release.apk`。此前由本机 debug 签名安装的用户使用 `huixue-lab-v0.28-debug-compat.apk`（18,773,593 字节）。本版沿用原证书，未在真实手机上重新进行覆盖升级测试。

两者签名不同，不能互相覆盖。不要为试包卸载已有数据；签名与备份说明见 [发布说明](docs/RELEASE.md)。

导入入口：**我的 → 她的性格 → 导入设定文档**。模板在 [docs/personas](docs/personas/README.md)。

`web-demo/index.html` 仍是零依赖演示，不采集真实心率、不调用云模型。本版的人设文档导入在 Android 提供，网页版没有模型人设导入界面。

## 已有功能

标准BLE HRS心率、手机使用统计、步数变化近似久坐、Health Connect睡眠；状态依据、反馈驱动的个性化推荐；行动新增/编辑/收藏/归档；可暂停恢复计时；报告、CSV和完整备份；可选大模型聊天、人设、语音及音景。

## 构建和检查

需要JDK25与Android SDK Platform37。将JAVA_HOME、ANDROID_HOME设置为本机安装位置，然后运行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain
node web-demo/selftest.js
python tools/check_delivery.py --require-unit-reports
```

原生测试、发布环境变量及两种签名见 [发布说明](docs/RELEASE.md)。改动前阅读 [AGENTS.md](AGENTS.md)。

## 数据与边界

状态阈值是经验规则，不是医学测量；心率参考基线是最近30次读数最低值。睡眠统计会话记录时长，未知不算睡足。推荐后验只由主观评分更新，心率差作为观察参考。

基础功能端侧运行。可选模型会接收用户输入、最近对话、角色/记忆和行动上下文；Key由用户配置，存应用私有偏好，业务备份不导出。清空记录同时清空推荐统计。

真实Health Connect睡眠全链路、长时后台通知、真实模型/TTS专项和真实用户效果仍待验证。当前没有完成10人访谈与两周试用，不能声称已证明健康改善。

## 文档与素材

[架构](docs/ARCHITECTURE.md)；[计划书](docs/03-项目计划书.md)；[开发文档](docs/04-项目开发文档.md)；[测试文档](docs/05-项目测试文档.md)；[技术研究](docs/06-技术研究报告.md)；[创新报告](docs/07-设计及创新性分析报告.md)；[AI过程记录](docs/08-AI辅助编程交互记录.md)。

人物为社区形象派生素材，署名与CC BY-NC-SA 4.0说明见 [素材来源](assets/visuals-v024/README.md)。历史功能和发布记录可在Git历史及各版本验证文档查看。
