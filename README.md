# VoiceBlock — 语音方块 Mod

> 按住鼠标侧键5，说出方块名称，在创造模式下快速获取方块。
> 全部本地语音识别与语义匹配，无需联网。

---

## 目录

1. [项目简介](#项目简介)
2. [技术栈与依赖](#技术栈与依赖)
3. [环境要求](#环境要求)
4. [构建步骤](#构建步骤)
5. [项目结构](#项目结构)
6. [核心模块说明](#核心模块说明)
7. [模型文件配置](#模型文件配置)
8. [开发与调试](#开发与调试)
9. [方块数据扩展](#方块数据扩展)
10. [常见问题](#常见问题)

---

## 项目简介

VoiceBlock 是一个 Minecraft 1.21 NeoForge 客户端 Mod。玩家按住自定义键位（默认鼠标侧键5）后通过麦克风语音输入方块名称，松开按键后识别结果经过三层模糊匹配，最终在创造模式下替换当前选中快捷栏格子的物品。

### 功能特性

- 按住说话（PTT）交互，默认鼠标侧键5，可在控制设置中修改
- 三层模糊匹配：精确匹配 → 拼音+编辑距离 → bge-small-zh Embedding 语义匹配
- 仅创造模式可用，非创造模式提示拒绝
- 内置 820+ 条 Minecraft 1.21 方块的中文名称、别名与语义描述
- 全部离线运行：Vosk 语音识别 + ONNX Runtime 语义推理

---

## 技术栈与依赖

| 组件 | 版本 | 用途 | 许可证 |
|------|------|------|--------|
| NeoForge | 21.0.143 | Mod 加载器 | LGPL-2.1 |
| ModDevGradle | 1.0.21 | Gradle 构建插件 | LGPL-2.1 |
| Vosk Java | 0.3.45 | 离线中文语音识别 | Apache-2.0 |
| ONNX Runtime | 1.17.0 | bge-small-zh 推理引擎 | MIT |
| pinyin4j | 2.5.1 | 中文转拼音 | GPL-2.0 |
| Gson | (Minecraft 内置) | JSON 解析 | Apache-2.0 |

以上依赖通过 `jarJar` 打包进最终 jar，无需用户额外安装。

---

## 环境要求

- **JDK 21**（推荐 Eclipse Temurin 21 或 Microsoft Build of OpenJDK 21）
- **Gradle 8.8+**（构建时自动下载，无需手动安装）
- 约 **2GB 内存** 用于构建（Minecraft 反编译与重编译）
- 首次构建需要联网下载 NeoForge、Minecraft 及第三方依赖

---

## 构建步骤

### 1. 克隆或获取项目源码

### 2. 确认 JDK 21

```bash
java -version
# 应输出 openjdk version "21.x.x"
```

### 3. 生成 Gradle Wrapper（仅首次）

```bash
gradle wrapper --gradle-version 8.9
```

### 4. 构建

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

首次构建会执行以下流程（耗时 5~15 分钟，取决于网络与硬件）：

1. 下载 NeoForge 21.0.143 与 Minecraft 1.21 资源
2. 反编译 Minecraft 源码（Vineflower）
3. 应用 NeoForge patch 并重编译
4. 编译 Mod 源码
5. 通过 `jarJar` 将三方依赖打包进最终 jar

### 5. 构建产物

```
build/libs/voiceblock-1.0.0.jar   （约 111MB，含全部三方依赖）
```

### 代理配置（如构建时无法连接 Maven 仓库）

编辑 `gradle.properties`，取消注释并修改代理地址：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7897
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7897
```

---

## 项目结构

```
src/main/
├── java/com/voiceblock/
│   ├── VoiceBlockMod.java              # Mod 入口（@Mod）
│   │
│   ├── client/                         # 客户端逻辑
│   │   ├── KeyBindings.java            # 键位注册（鼠标侧键5）
│   │   └── ClientEvents.java           # ClientTick 监听、录音/识别调度
│   │
│   ├── voice/                          # 语音采集与识别
│   │   ├── MicrophoneRecorder.java     # Java Sound API 录音（16kHz/16bit/mono）
│   │   └── VoskRecognizer.java         # Vosk 模型加载与识别
│   │
│   ├── match/                          # 方块匹配引擎
│   │   ├── BlockMatcher.java           # 三层匹配入口与调度
│   │   ├── BlockData.java              # blocks.json 加载
│   │   ├── PinyinUtil.java             # 拼音转换 + Levenshtein 编辑距离
│   │   ├── EmbeddingMatcher.java       # bge-small-zh ONNX 推理 + 余弦相似度
│   │   └── MatchResult.java            # 匹配结果记录
│   │
│   └── util/
│       └── InventoryUtil.java          # 创造模式物品栏替换
│
└── resources/
    ├── META-INF/neoforge.mods.toml     # Mod 元数据
    ├── pack.mcmeta                     # 资源包元数据
    └── assets/voiceblock/
        ├── blocks.json                 # 方块数据（名称/别名/语义描述）
        └── lang/zh_cn.json             # 语言文件
```

---

## 核心模块说明

### 1. 键位与事件流（`client/`）

`KeyBindings` 通过 `RegisterKeyMappingsEvent` 注册鼠标侧键5（`GLFW_MOUSE_BUTTON_5`）。

`ClientEvents` 监听 `ClientTickEvent.Post`，在每帧检测按键状态：

- 按键刚按下 → `MicrophoneRecorder.start()` 开始录音
- 按键刚松开 → `MicrophoneRecorder.stop()` 停止录音，在独立线程中调用 `VoskRecognizer.recognize()` 识别

识别完成后通过 `Minecraft.execute()` 切回主线程执行匹配与物品替换。

### 2. 语音采集（`voice/MicrophoneRecorder.java`）

使用 `javax.sound.sampled.TargetDataLine` 采集音频：

- 采样率：16000 Hz
- 采样位深：16 bit PCM signed
- 声道：单声道
- 字节序：小端

录音线程将数据写入 `ByteArrayOutputStream`，`stop()` 返回完整 PCM 字节数组。

### 3. 语音识别（`voice/VoskRecognizer.java`）

加载 Vosk 中文模型，`Recognizer.acceptWaveForm()` 接受 PCM 数据，`getFinalResult()` 返回 JSON，从中解析 `text` 字段。

模型目录查找顺序：
1. `config/voiceblock/vosk-model/`（运行目录）
2. 若不存在，从 jar 内 `assets/voiceblock/vosk-model/` 解压

### 4. 三层匹配（`match/BlockMatcher.java`）

```
输入文本
  │
  ├─ L1 精确匹配 + 别名字典 ──命中──→ 返回
  │
  ├─ L2 拼音 + 编辑距离 ──相似度≥0.6──→ 返回
  │
  └─ L3 Embedding 余弦相似度 ──相似度≥0.3──→ 返回
```

**L1 精确匹配**：遍历所有方块，比较 `name` 与 `aliases`，完全相等即命中。

**L2 拼音匹配**：用 `pinyin4j` 将输入与所有方块名/别名转无声调拼音，计算 Levenshtein 归一化相似度。

**L3 语义匹配**：`EmbeddingMatcher` 将文本经 BERT WordPiece tokenizer 切分，送入 bge-small-zh ONNX 模型，取 CLS token 输出并 L2 归一化，与预计算的所有方块 embedding 计算余弦相似度。

### 5. 物品栏替换（`util/InventoryUtil.java`）

- 检查 `player.isCreative()`，非创造模式返回 `false`
- 通过 `BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))` 获取物品
- 用 `ItemStack.set(DataComponents.CUSTOM_NAME, ...)` 设置中文显示名
- `player.getInventory().setItem(player.getInventory().selected, stack)` 替换当前选中格子

---

## 模型文件配置

运行 Mod 前必须准备以下模型文件。**模型不会打包进 jar**，需用户自行下载。

### 1. Vosk 中文语音模型

下载地址：<https://alphacephei.com/vosk/models>

选择 **vosk-model-small-cn-0.22**（约 40MB，推荐）或更大的 `vosk-model-cn-0.22`（约 1.3GB，精度更高）。

解压后放入：

```
.minecraft/config/voiceblock/vosk-model/
├── conf/
├── ivector/
├── am/
├── final.mdl
└── ...
```

### 2. bge-small-zh ONNX 模型 + 词表

需要 BAAI 的 `bge-small-zh` 模型导出为 ONNX 格式，并获取其 BERT 词表 `vocab.txt`。

以下三种方法任选其一：

---

#### 方法 A：optimum-cli 一键导出（推荐）

**① 准备 Python 环境**

需要 Python 3.8+。检查版本：
```bash
python --version
```

**② 安装依赖**
```bash
pip install optimum[onnxruntime] transformers torch onnx
```

**③ 导出 ONNX 模型**
```bash
optimum-cli export onnx --model BAAI/bge-small-zh bge-small-zh-onnx/
```

> 若下载缓慢，可设置 HuggingFace 镜像后再执行：
> ```bash
> # Windows
> set HF_ENDPOINT=https://hf-mirror.com
> # Linux / macOS
> export HF_ENDPOINT=https://hf-mirror.com
> ```

**④ 整理文件**

导出完成后，`bge-small-zh-onnx/` 目录内容：
```
bge-small-zh-onnx/
├── model.onnx              ← 模型本体
├── vocab.txt               ← BERT 中文词表（21128 个 token）
├── config.json
├── tokenizer.json
└── ...
```

将 `model.onnx` 重命名为 `bge-small-zh.onnx`，连同 `vocab.txt` 一起放入目标目录。

---

#### 方法 B：从 HuggingFace 直接下载（无需 Python）

**① 访问模型页面**

浏览器打开：<https://huggingface.co/BAAI/bge-small-zh>

**② 下载文件**

在 "Files and versions" 标签页中下载：
- `vocab.txt`（根目录下）
- 若有 `onnx/` 子目录，下载其中的 `model.onnx`

若官方仓库无 ONNX 版本，可搜索第三方导出：
```
https://huggingface.co/search?q=bge-small-zh+onnx
```
找一个包含 `model.onnx` 和 `vocab.txt` 的仓库下载。

**③ 重命名 `model.onnx` → `bge-small-zh.onnx`**

---

#### 方法 C：手动 Python 脚本导出（方法 A 失败时使用）

**① 安装依赖**
```bash
pip install transformers torch onnx onnxruntime
```

**② 创建导出脚本 `export_bge.py`**

```python
from transformers import AutoTokenizer, AutoModel
import torch
import os

model_name = "BAAI/bge-small-zh"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModel.from_pretrained(model_name)
model.eval()

dummy = tokenizer("示例文本", padding=True, truncation=True,
                  max_length=64, return_tensors="pt")

os.makedirs("bge-small-zh-onnx", exist_ok=True)

torch.onnx.export(
    model,
    (dummy["input_ids"], dummy["attention_mask"], dummy["token_type_ids"]),
    "bge-small-zh-onnx/model.onnx",
    input_names=["input_ids", "attention_mask", "token_type_ids"],
    output_names=["last_hidden_state"],
    dynamic_axes={
        "input_ids": {0: "batch", 1: "seq"},
        "attention_mask": {0: "batch", 1: "seq"},
        "token_type_ids": {0: "batch", 1: "seq"},
        "last_hidden_state": {0: "batch", 1: "seq"},
    },
    opset_version=17,
)

tokenizer.save_vocabulary("bge-small-zh-onnx/")
print("导出完成，文件在 bge-small-zh-onnx/ 目录")
```

**③ 运行脚本**
```bash
python export_bge.py
```

---

#### 最终目录结构

无论使用哪种方法，最终文件必须放置为：

```
.minecraft/config/voiceblock/models/
├── bge-small-zh.onnx    （约 40-50MB）
└── vocab.txt            （约 200KB，21128 行）
```

**验证 vocab.txt**：用记事本打开，前几行应为：
```
[PAD]
[UNK]
<[BOS_never_used_51bce0c785ca2f68081bfa7d91973934]>
<[BOS_never_used_51bce0c785ca2f68081bfa7d91973934]>
[MASK]
。
、
的
...
```

---

## 开发与调试

### 运行客户端

```bash
# Windows
gradlew.bat runClient

# Linux / macOS
./gradlew runClient
```

### 日志

Mod 使用 SLF4J，日志输出到 `logs/latest.log`，关键字过滤 `voiceblock`。

### 匹配调试

`BlockMatcher.match()` 每层匹配都会通过 `VoiceBlockMod.LOGGER.info()` 输出命中结果与分数，便于调试匹配质量。

### 添加日志

```java
VoiceBlockMod.LOGGER.info("你的日志内容 {}", variable);
```

---

## 方块数据扩展

方块数据存储在 `src/main/resources/assets/voiceblock/blocks.json`，格式如下：

```json
{
  "id": "minecraft:oak_stairs",
  "name": "橡木楼梯",
  "aliases": ["橡木台阶", "楼梯", "台阶", "木楼梯"],
  "embeddingText": "橡木楼梯 橡木台阶 楼梯 台阶 棕色楼梯 木制楼梯 oak stairs wooden stairs"
}
```

| 字段 | 说明 |
|------|------|
| `id` | Minecraft 物品 ID，必须与 `BuiltInRegistries.ITEM` 中的注册名一致 |
| `name` | 中文显示名，用于精确匹配与物品命名 |
| `aliases` | 别名列表，玩家常说的简称或俗称 |
| `embeddingText` | 语义匹配用文本，建议包含名称、别名、颜色、材质、用途、英文等关键词 |

新增方块后重新 `build` 即可生效。

---

## 常见问题

### Q: 构建失败，提示 `Could not resolve net.neoforged:neoforge:21.0.143`

A: 网络问题。检查是否需要代理，参考上文「代理配置」。或重试构建（NeoForge Maven 偶发不稳定）。

### Q: 构建失败，提示 `To use the NeoForge plugin, please use at least Gradle 8.8`

A: Gradle 版本过低。使用 `gradle wrapper --gradle-version 8.9` 重新生成 wrapper，或修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl`。

### Q: 运行时报 `Failed to initialize voice components`

A: 模型文件未正确放置。检查 `config/voiceblock/vosk-model/` 与 `config/voiceblock/models/` 目录。

### Q: 按键后提示"仅创造模式可用"

A: 当前不在创造模式。切换到创造模式即可。

### Q: 语音识别准确率低

A: 尝试：
1. 下载更大的 Vosk 模型（`vosk-model-cn-0.22`）
2. 在安静环境下使用
3. 确保麦克风采样率为 16kHz

### Q: 语义匹配返回错误方块

A: Embedding 相似度阈值设为 0.3，可在 `EmbeddingMatcher.match()` 中调整 `bestScore < 0.3f` 的阈值。

### Q: `optimum-cli` 命令找不到

A: 确认 `pip install optimum[onnxruntime]` 执行成功。若仍找不到，可改用：
```bash
python -m optimum.exporters.onnx --model BAAI/bge-small-zh bge-small-zh-onnx/
```

### Q: 从 HuggingFace 下载模型很慢

A: 设置国内镜像后重试：
```bash
# Windows
set HF_ENDPOINT=https://hf-mirror.com
# Linux / macOS
export HF_ENDPOINT=https://hf-mirror.com
```

### Q: 没有 Python 环境，怎么获取 ONNX 模型？

A: 使用「方法 B」直接从 HuggingFace 网页下载 `model.onnx` 和 `vocab.txt`，无需 Python。

### Q: 导出的 `model.onnx` 只有几 KB？

A: 导出失败了。检查 `torch.onnx.export` 的报错信息，通常是 PyTorch 版本与 transformers 版本不兼容，尝试升级：
```bash
pip install --upgrade torch transformers
```

### Q: jar 文件太大（111MB）

A: ONNX Runtime 包含全平台原生库。如需减小体积，可在 `build.gradle` 中排除不需要的平台，或使用 `onnxruntime` 的 CPU-only 精简版。

---

## 许可证

Mod 代码部分采用 MIT License。各依赖的许可证见上表。
