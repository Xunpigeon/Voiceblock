# VoiceBlock — 语音切换方块 Mod

用语音控制 Minecraft，说出方块名称即可切换到对应方块。基于 Vosk 离线中文语音识别，无需联网。

## 功能特性

- 🎙️ **离线语音识别**：内置 Vosk 中文模型，断网也能用
- 🧱 **1604 个方块**：支持原版 + Create + Aeronautics
- 🔤 **拼音模糊匹配**：说"原石"、"yuanshi"都能匹配
- 🎯 **多加载器支持**：Fabric / Forge / NeoForge
- 🔧 **外置模型覆盖**：可自行替换为高精度大模型

## 使用方法

1. 将对应版本的 jar 放入 `mods/` 文件夹
2. 首次启动游戏，模型会自动解压到 `config/voiceblock/vosk-model/`
3. 进入游戏，按住 **V 键** 说话，松开后自动识别并切换方块
4. HUD 会显示识别结果和匹配到的方块

> 默认按键为 V，可在控制设置中修改。

## 下载

前往 [Releases](https://github.com/Xunpigeon/Voiceblock/releases) 页面下载最新版本。

| 产物 | 适用加载器 | MC 版本 | Java |
|------|-----------|---------|------|
| voiceblock-fabric-1.20.1-1.1.0.jar | Fabric | 1.20.1 ~ 1.20.4 | 17 |
| voiceblock-fabric-1.21-1.1.0.jar | Fabric | 1.21.x | 21 |
| voiceblock-neoforge-1.21-1.1.0.jar | NeoForge | 1.21 | 21 |
| voiceblock-forge-1.20.1-1.1.0-all.jar | Forge | 1.20.1 ~ 1.20.6 | 17 |

> Forge 用户请使用 `-all.jar`（已内嵌 vosk + pinyin4j 依赖）。

## 升级到高精度模型（可选）

内置小模型（vosk-model-small-cn-0.22，65MB，字错误率 23.54%）适合日常使用。
如需更高精度，可下载大模型 vosk-model-cn-0.22（1.3GB，字错误率 13.98%）：

1. 从 [Vosk 官网](https://alphacephei.com/vosk/models) 下载 `vosk-model-cn-0.22`
2. 解压并覆盖到 `config/voiceblock/vosk-model/`
3. 重启游戏即可，无需重新安装 mod

## 构建

```bash
./gradlew build
```

产物位于各子项目的 `build/libs/` 目录。

## 项目结构

```
VoiceBlock/
├── common/              # 跨平台共享代码与资源
│   ├── java/            # VoskRecognizer, BlockMatcher 等核心逻辑
│   └── resources/       # blocks.json, vosk-model, lang
├── fabric/              # Fabric 平台特定代码
├── forge/               # Forge 平台特定代码
├── neoforge/            # NeoForge 平台特定代码
├── fabric-1.20.1-1.20.4/
├── fabric-1.21.x/
├── forge-1.20.1-1.20.6/
└── neoforge-1.21.x/
```

## 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)。

## 许可证

本项目仅供学习交流使用。