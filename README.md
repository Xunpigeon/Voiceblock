# 语音方块 VoiceBlock

> **说出方块名字，瞬间拿到方块。**

语音方块是一个 Minecraft NeoForge 模组，使用**完全离线**的中文语音识别，听懂你说的方块名，直接送到手上。无需联网、无需指令、无需翻背包。

## ✨ 功能特性

- **语音获取方块** — 按住自定义按键，说出方块名，松开按键瞬间获取
- **完全离线识别** — 基于 [Vosk](https://alphacephei.com/vosk/) 离线中文模型，无需联网
- **智能三层匹配** — 精确匹配 + 手工别名 + 拼音相似度，听懂口语化说法
- **自动支持 Mod 方块** — 启动时自动扫描游戏注册表，其他 Mod 添加的方块也能识别
- **800+ 原版方块别名** — 说"半砖"拿台阶、说"围栏"拿栅栏、说"踏板"拿压力板……
- **实时 HUD 反馈** — 屏幕显示识别文本和匹配结果，方便调试
- **跨平台原生库** — 内置 Windows / Linux / macOS 的语音识别原生库

## 🎮 使用方法

1. 将 oiceblock-1.0.0.jar 放入 .minecraft/mods/
2. 启动游戏（首次启动会自动解压语音模型到 config/voiceblock/）
3. 在 **控制 → 杂项** 中找到 **语音方块** 绑定按键（默认未绑定，建议设为鼠标侧键）
4. 创造模式下，按住绑定的按键，**清楚地说出方块名称**
5. 松开按键 — 匹配到的方块直接出现在当前选中的快捷栏位，64 个

## 📋 运行要求

| 项目        | 版本               |
| --------- | ---------------- |
| Minecraft | **1.21**         |
| NeoForge  | **21.0.167** 或更高 |
| Java      | **21**           |
| 麦克风       | 任意可用的输入设备        |

## 🔧 开发者

### 构建

`powershell

# Windows

.\gradlew.bat build


构建产物：uild/libs/voiceblock-1.0.0.jar（可直接发布的完整模组）

### 项目结构

`
src/main/java/com/voiceblock/
├── VoiceBlockMod.java          # 模组入口
├── client/
│   ├── ClientEvents.java       # 按键监听 + 识别流程
│   ├── KeyBindings.java        # 按键绑定注册
│   └── VoiceHudOverlay.java    # 实时 HUD 渲染
├── match/
│   ├── BlockData.java          # 方块数据 + 运行时自动发现 Mod 方块
│   ├── BlockMatcher.java       # 两层匹配（精确 → 拼音相似度）
│   ├── MatchResult.java        # 匹配结果数据类
│   └── PinyinUtil.java         # 拼音相似度算法
├── util/
│   └── InventoryUtil.java      # 创造模式物品服务端同步
└── voice/
    ├── MicrophoneRecorder.java # PCM 音频采集（基于 Java Sound API）
    └── VoskRecognizer.java     # Vosk 离线中文语音识别
`

### 添加方块别名

编辑 src/main/resources/assets/voiceblock/blocks.json，为常用方块添加口语化别名：

`json
{"id": "minecraft:oak_fence", "name": "橡木栅栏", "aliases": ["围栏", "栏杆"]}
`

其他 Mod 的方块会在运行时自动发现，但你也可以在这个文件里手动为它们添加别名。

## 📦 依赖

| 库                                                | 版本     | 用途            |
| ------------------------------------------------ | ------ | ------------- |
| [Vosk](https://alphacephei.com/vosk/)            | 0.3.45 | 离线中文语音识别      |
| [pinyin4j](https://github.com/belerweb/pinyin4j) | 2.5.1  | 中文拼音转换，用于模糊匹配 |

两个库都通过 JarJar 内嵌在 jar 中，用户无需额外下载。

## 📝 开源协议

本项目基于 **MIT License** 开源，欢迎自由使用、修改和分发。
