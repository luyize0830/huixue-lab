# 给协作者 / AI 的约定

这个仓库是「回血实验室」的参赛项目。下面的规矩是用户明确要求的，改代码前先读一遍。

## 1. 每次修改都要同步到 GitHub

用户在 2026-11-03 明确要求：**以后每次修改后都同步更新到 GitHub**。所以任何一次改动，收尾时按这个顺序走完，不要停在本地：

1. `.\gradlew.bat testDebugUnitTest` —— 单测必须全绿（当前 212 个用例），不绿就不提交。
2. `git add -A && git commit -m "<type>(vX.YZ): <做了什么、为什么>"` —— 提交信息写清"改了什么"和"为什么"。
3. `git push origin main` —— 推到主分支。PowerShell 里 git 把进度写到 stderr，会显示 `exit code: 1`，看最后一行 `xxx..yyy  main -> main` 才算成功。
4. 如果这次改了**用户能看见的东西**（新功能、界面、文案、版本号），还要补齐：
   - `README.md` 里对应版本的一节；
   - `docs/03`–`docs/08` 六份参赛文档（口径必须与 `versionCode` / 提交哈希 / APK 字节数 / 单测数一致）；
   - GitHub Release（新版本 + APK + 证据截图 zip）；
   - 桌面交接包 `C:\Users\34243\Desktop\huixue-lab`（`00-先看这个.md` 的版本表、`20-文档`、`30-安装包`、`50-证据截图`、`80-仓库快照`）。
5. 收尾时回报一次"已同步"，列出提交哈希、Release 地址、文档版本。

## 2. 不要动的东西

- 包名 `com.example.blehr` 暂时不改（改名会让手机上的旧版本变成另一个 App）。
- API Key、令牌、手机号、本机绝对路径不进代码、不进文档、不进截图（`evidence/` 里的 `ui-*.xml` 常会把输入框内容 dump 出来，提交前扫一遍 `sk-`）。
- 版本号三处一起改：`app/build.gradle.kts` 的 `versionCode` / `versionName`、`README.md` 版本表、`docs/03` 头部版本行。

## 3. 代码约定

- 新逻辑优先放进 `logic/`，写成不依赖 Android 的纯函数，并配单测（`app/src/test/java/com/example/blehr/`）。当前覆盖率高的模块：状态评估、推荐、会话、记忆、节奏、主动性、压力、音乐建议、错误映射。
- 需要平台能力（BLE、TTS、AudioTrack、Health Connect、SharedPreferences）的放 `data/` 或 `ui/`，UI 只负责展示。
- Kotlin 字符串里要引号就用「」，不要用 ASCII 双引号包中文——会截断字符串并报 `Unresolved reference`。
- 改"说话像不像人"相关的东西，要同时看 `logic/Persona.kt`（人设与提示词）和 `logic/Humanizer.kt`（拆条与去客服腔），改完两边的测试都要跑。

## 4. 编号不要重排

功能编号 `F-01`…`F-38`、缺陷编号 `BUG-01`…`BUG-26` 都是只增不重排。`docs/03` 的进度表、`docs/05` 的缺陷表、`docs/06/07` 的引用都锚在这些号上，重排会让历史文档全部失配。

## 5. 文档字数

`docs/03`–`docs/08` 每份有 3500 字（Word 口径）上限，`docs/04` 与 `docs/07` 经常顶格。加内容前先删同等量的旧句，不要超。

## 6. 网页演示版是等价重写

`web-demo/index.html` 是单文件、零依赖的等价实现（给装不了 APK 的老师看）。改了安卓端的规则（状态评估阈值、推荐约束、深夜模式、压力/音乐逻辑）时，看一眼网页版是否需要同步；两边规则应当一致。