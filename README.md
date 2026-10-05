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


## 开发者指南：如何添加自定义方块

VoiceBlock 的方块匹配数据全部存放在 `common/resources/assets/voiceblock/blocks.json` 中。如需让你的 Mod 方块被语音识别支持，只需在这个 JSON 文件里添加条目，然后重新编译即可。

### 1. blocks.json 格式

每个方块是一个 JSON 对象，包含三个字段：

```json
{
  "id": "minecraft:oak_planks",
  "name": "橡木木板",
  "aliases": ["橡木板", "木板"]
}
```

| 字段 | 说明 | 示例 |
|------|------|------|
| `id` | Minecraft 物品 ID，格式为 `命名空间:方块名`。原版用 `minecraft:`，模组用模组的命名空间 | `create:andesite_casing` |
| `name` | 中文显示名称，既是匹配关键词，也会在 HUD 上显示 | `安山岩机壳` |
| `aliases` | 别名数组，说这些词也能匹配到该方块。可留空 `[]` | `["安山机壳", "机壳"]` |

### 2. 如何获取方块 ID

- **方法一（游戏内）**：按 `F3 + H` 开启物品 ID 显示，鼠标悬停在物品上即可看到完整 ID（如 `minecraft:oak_planks`）。
- **方法二（源码）**：在对应 Mod 的源码中查找方块注册名，通常在 `ModBlocks` 或类似类中。
- **方法三（JEI/REI）**：安装 JEI 或 REI，在物品上按 `R` 或查看配方可看到 ID。

### 3. 添加方块示例

#### 添加原版方块

```json
{
  "id": "minecraft:cherry_planks",
  "name": "樱花木板",
  "aliases": ["樱花板", "樱木板"]
}
```

#### 添加 Mod 方块（以 Create 为例）

```json
{
  "id": "create:brass_casing",
  "name": "黄铜机壳",
  "aliases": ["黄铜壳", "铜机壳"]
}
```

#### 添加你自己的 Mod 方块

把 `id` 的命名空间换成你的 Mod ID 即可：

```json
{
  "id": "mymod:magic_block",
  "name": "魔法方块",
  "aliases": ["魔法块", "魔方块"]
}
```

### 4. 匹配原理

识别时，`BlockMatcher` 会对语音识别出的文本与 `name` + `aliases` 进行匹配：

- 支持**中文直接匹配**（说"橡木木板"匹配 `橡木木板`）
- 支持**拼音匹配**（说"xiangmumuban"也能匹配 `橡木木板`，基于 pinyin4j）
- 支持**模糊匹配**（部分命中即可，容错率高）

> 💡 建议：为常用方块多添加几个口语化别名，能大幅提升识别成功率。比如"橡木台阶"可以加别名"橡木半砖"、"半砖"。

### 5. 重新编译

修改 `blocks.json` 后，在项目根目录运行：

```bash
# 编译全部 4 个版本
./gradlew build

# 或只编译指定版本
./gradlew :fabric-1.21:build
./gradlew :neoforge-1.21:build
./gradlew :forge-1.20.1:build
./gradlew :fabric-1.20.1:build
```

产物位于各子项目的 `build/libs/` 目录。`blocks.json` 会被打包进 jar，修改后必须重新编译才能生效。

### 6. 注意事项

- `blocks.json` 必须是合法的 JSON 格式，逗号、括号不能漏。
- `id` 必须与游戏中实际注册的物品 ID 完全一致，否则切换方块会失败。
- `name` 和 `aliases` 建议用简体中文，避免生僻字影响语音识别。
- 当前已内置原版（820）、Create（643）、Aeronautics（43）等共 1604 个方块，欢迎 PR 补充更多 Mod 支持。
