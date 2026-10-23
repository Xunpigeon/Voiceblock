# VoiceBlock — 语音取物 Mod

说出物品的名字，直接把它拿到手上。

基于 Vosk 的**完全离线**中文语音识别，面向中文玩家 —— 尤其是**视障玩家**（没法靠眼睛在背包里翻找物品）。

---

## 功能

- 🎙️ **离线语音识别**：内置 Vosk 中文模型，断网可用；也可外置大模型提升识别率
- 🧱 **2200+ 物品**：原版 + Create / 机械动力 / 精妙背包等常见整合包物品，启动时还会从注册表自动发现
- 🗣️ **7 个取物来源**，按优先级自动尝试：

  | 顺序 | 来源 | 默认 | 说明 |
  |---|---|---|---|
  | 1 | 背包 | ✅ | 自身 36 格 + 副手 |
  | 2 | 精妙背包 | ✅ | 读取背包内物品，不是背包本身 |
  | 3 | 末影箱 | ⬜ | 玩家私有，默认关 |
  | 4 | 潜行盒 | ✅ | 1.20.5+ 可用 |
  | 5 | AE2 | ✅ | 需无线终端已绑定网络 |
  | 6 | 汤姆的简易储存 | ✅ | 无线终端或周围 8 格内的存储终端 |
  | 7 | 周围箱子 | ✅ | 半径 16 格内的容器 |

- 🌐 **联机可用**：客户端把「物品 ID + 数量」发给服务端，由**服务端**真正执行取物，容器内容不会出现"空壳"问题
- 🎓 **学习模式**：手持物品按住【鼠标侧键 4】说出名字，即可把发音记进 `manual_overrides.json`
- 🔤 **拼音模糊匹配**：N-best 多候选 + 拼音评分，"原石"、"yuanshi"、"安山机壳" 这类都能命中
- 🔊 **音效反馈**：按下/松开都有提示音，不用看屏幕也知道状态

---

## 支持的平台

| 加载器 | Minecraft | Java | 说明 |
|---|---|---|---|
| Fabric | 1.20.1 ~ 1.20.4 | 17 | |
| Fabric | 1.21.x | 21 | |
| Forge | 1.20.1 | 17 | |
| NeoForge | 1.21.x | 21 | |

> 联机时**服务端也要装**同一个 jar。只装客户端时只能取自己背包里的物品（会给出提示）。

---

## 安装与使用

1. 把对应 jar 放进 `mods/`
2. 首次启动会把模型解压到 `config/voiceblock/vosk-model/`
3. 进游戏，**按住鼠标侧键 5** 说话（按下"叮"，松开经验球音效）
4. HUD 显示识别结果，聊天栏显示成功/失败原因

| 按键 | 默认 | 作用 |
|---|---|---|
| `key.voiceblock.use` | 鼠标侧键 5 | 按住说话，松开取物 |
| `key.voiceblock.learn` | 鼠标侧键 4 | 学习模式：手持物品按住说话，记录发音 |

两个键位都可以在「控制 → 按键绑定 → VoiceBlock」里改。

### 创造 / 生存

| 模式 | 行为 |
|---|---|
| 创造 | 拿到 1 个 |
| 生存 | 取一整组（最多 64），放入当前选中的快捷栏 |

---

## 命令

| 命令 | 作用 |
|---|---|
| `/voiceblock` | 帮助 |
| `/voiceblock debug` | 查看最近一次识别的 N-best 候选 |
| `/voiceblock bind <序号> [物品ID]` | 把候选发音绑定到物品（不填 ID 用手持物品） |
| `/voiceblock list` | 列出手动绑定 |
| `/voiceblock remove <拼音>` | 删除一条绑定 |
| `/voiceblock dedup` | 扫描冲突并生成报告 |
| `/voiceblock reload` | 热重载 `manual_overrides.json` |
| `/voiceblock source list` | 查看所有物品源：开关 / 可用 / 优先级 / 半径 |
| `/voiceblock source enable\|disable <名字>` | 启停某个源 |
| `/voiceblock source priority <名字> <数字>` | 改优先级（越小越优先） |
| `/voiceblock source radius <名字> <数字>` | 改周围箱子/汤姆储存的扫描半径 |
| `/voiceblock source reset` | 恢复默认配置 |
| `/voiceblock test <物品ID> [数量]` | **诊断用**：不经过麦克风，直接走一次取物链路 |

> `/voiceblock test` 在联机排查时特别有用：一句话就能确认服务端有没有装、通道通不通、哪个物品源在报错。

---

## 配置文件

全部在 `config/voiceblock/`（服务端有自己的独立目录，与客户端不同步）：

| 文件 | 内容 |
|---|---|
| `sources.json` | 物品源开关 / 优先级 / 半径 |
| `manual_overrides.json` | 手动绑定与学习模式的发音记录 |
| `hints.json` | 一次性提示是否已展示 |
| `vosk-model/` | 外置模型目录，存在则优先于 jar 内置模型 |

---

## 从源码构建

```bash
./gradlew :fabric-1.20.1:build
./gradlew :fabric-1.21:build
./gradlew :forge-1.20.1:build
./gradlew :neoforge-1.21:build
```

> ⚠️ Forge 必须用 `build` 任务（会跑 reobf），**不要**用 `gradlew jar`，否则产物启动即 `NoSuchMethodError`。
>
> ⚠️ 产物目录里会出现 `*-all.jar`，那是 jar-in-jar 版本，**分发时用不带 `-all` 的那个**。

环境要求：Windows + JDK 17（Forge 1.20.1）+ JDK 21（其余三个）。

---

## 许可

MIT
