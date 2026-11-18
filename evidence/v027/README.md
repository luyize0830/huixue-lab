# v0.27 证据索引

- `validation.json`：应用实现基线、文件大小与哈希、测试结果和待验证项。
- `jvm-summary.json`：31 个测试类、267 例的实际本地 XML 汇总。
- `phone-instrumentation.txt` / `emulator-instrumentation.txt`：7 例原生测试分别在 Android 16 真机和 Android 17 模拟器通过的输出。
- `android-release-*.png`：正式签名包在隔离模拟器运行的页面；未接手环、使用演示行为信号，不属于真实用户数据。
- `web-*.png` / `web-browser-validation.json`：真实浏览器完成展示、选择、暂停刷新恢复、反馈和 CSV 下载；所有轮次为 DEMO。

本目录不含可用的真实 BLE 手机截图。先前79 bpm文本仅作为观察记录，完整实时取数与展开评分依据仍待复验。首页心率65→90及置空的分数变化由注入值的Compose测试验证。

90 秒带字幕的网页演示视频另附于 GitHub Release；演示提前结束行动，不作为实际完成时长或用户效果证据。
