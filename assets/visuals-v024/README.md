# v0.24 图片素材与来源

本轮使用内置 image_gen 生成 10 张独立透明图片：小夜困倦/开心/担心三张、运动/静心/社交/娱乐/休息/补给六张分类图标，以及「回」启动图标前景。

`asset-spec.json` 保存完整提示词、生成文件标识与导出规格；`manifest.json` 保存原图与运行时文件的 SHA-256、尺寸及字节数。原图仍保留在生成输出目录；工程使用保持 alpha 的缩小与无损 WebP 编码，没有抠图、重绘或改色。

## 人物参考与署名

- 原型：上善无形的「溟月」；蓝发鲸鱼娘女仆方向参考 ZipZipPipe 的社区二次设计。
- 实际视觉参考：[作者版本展示截帧的档案副本](https://github.com/HABAI182/ai-girls-atlas/blob/main/assets/zipzippipe/deepseek/video-frame-zipzippipe-BV1tE9XBbErS-05s.png)。该参考只用于生成输入，未将其原图作为 App 素材分发。
- [角色档案与逐版本来源](https://github.com/HABAI182/ai-girls-atlas/blob/main/characters/deepseek/README.md)。
- [上善无形的 CC BY-NC-SA 4.0 声明](https://www.bilibili.com/opus/1231977657712771073)。人物三张作为社区形象的 AI 派生素材保留上述署名，按 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) 标注非商用、相同方式共享；不表示原作者或 DeepSeek 官方参与或认可本软件。

本次再创作：Codex 内置 image_gen；产品内仍称「小夜」。表情仅跟随既有 `MascotMood` 状态切换，没有改变角色对话设定。六类行动图标与「回」图标为本轮独立提示词生成，未使用人物参考图。

## 接入位置

- 安卓：`app/src/main/res/drawable-nodpi/art_v024_*.webp`；`Girl.kt` 加载三态人物，`Look.kt` 加载六分类图标。小于 28dp 的分类仍保留汉字标签以便阅读。
- 首页、聊天、首次填写称呼页、音乐页共用人物组件；行动列表、推荐与倒计时共用分类组件。
- 新启动图标使用版本化资源，保留旧资源和 Android 主题图标的单色矢量；包名不变。
- 网页：`web-demo/assets/art-v024/`；`web-demo/artwork.html` 可查看整套素材。

重导出：安装 Pillow 后运行 `python tools/import_visuals.py --source-dir <生成原图所在目录>`。处理只负责尺寸、编码和启动器所需的底板/遮罩，不重新生成图片。
