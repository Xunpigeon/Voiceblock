# VoiceBlock 更新日志

## v2.0.10 — 2026-10-23

### 修复：从背包取物到手上时出现「幽灵方块」

**现象**：用语音把背包里的物品换到当前选中的快捷栏后，客户端手上显示是空的（方块不渲染），
但按右键又能正常放下去 —— 服务端认为物品在手上，客户端却看不到。

**根因**：`ServerInventoryUtil.swapWithInventory` 在 `broadcastChanges()` **之前**手动调用了
`containerMenu.setRemoteSlot(...)`，把「客户端已知状态」的缓存提前改成了新值。
而 `broadcastChanges()` 是靠 `(真实槽位 != remoteSlots 缓存)` 做差分的 ——
缓存被提前改成新值后，差分永远是 0，这个格子的 `ContainerSetSlot` 包从此不再发送，
直到某次全量同步（比如打开背包）才恢复。这正是"短暂幽灵"。

创造模式的 `replaceSelectedItem` 也有同样的多余调用（顺序不同，影响较小），一并去掉。

**修复**：
- 移除两处 `setRemoteSlot` 调用，只修改真实槽位，然后统一走 `syncInventory()`
  （`containerMenu.broadcastChanges()`；若玩家开着别的容器，再对齐一次 `inventoryMenu`）
- `InventoryPlacer` 放置物品后立即同步（容器类物品源：箱子 / 末影箱 / 汤姆储存 / AE2 / 精妙背包 / 潜行盒），
  不再依赖下一次 tick 的广播

**影响**：四个平台（共用 common 代码），Fabric 1.20.1 / 1.21、Forge 1.20.1、NeoForge 1.21 同时修复。

**验证**：Forge 1.20.1 开发环境，石头放在主背包第 1 格、选中格清空 →
`/voiceblock test minecraft:stone` → 客户端 `/item/mainhand` 立即返回 `minecraft:stone x64`（修复前为 0），
服务端日志 `succeeded via inventory x64`。


## v2.0.9 — 2026-10-23

> 说明：v2.0.0 ~ v2.0.8 的改动此前没有逐版记录，这里把最终形态与本次修复合并说明。

### 修复：专用服务器上「容器取物全部失效」（严重）

**现象**：单人和局域网一切正常；连专用服务器时，只有自己背包里的物品能取，
末影箱 / 潜行盒 / 精妙背包 / AE2 / 汤姆储存 / 周围箱子全部失败，提示
`未找到该物品（已尝试: inventory,curio(err),ae2(err),tomsstorage(err),nearbyChest(err)）`。

**根因**：`common/SourceServerAccess` 的方法体里引用了 `net.minecraft.client.Minecraft`。
专用服务器链接这个类时会去加载客户端类，被 Forge 的 `RuntimeDistCleaner` 判定为 dist 违规：

```
RuntimeException: Attempted to load class net/minecraft/client/server/IntegratedServer
                  for invalid dist DEDICATED_SERVER
```

所有调用它的容器源因此全部抛异常；`inventory` 不调用它，所以没受影响。

**修复**：把客户端查找拆到 `ClientIntegratedServerLookup`，主类改成字符串反射进入，
字节码里不再有任何 `net.minecraft.client.*` 符号引用。四个平台共用这段代码，同时修复。

### 新增：Fabric 服务端权威取物

Fabric 之前是纯客户端实现（`environment: client`，服务端根本不加载模组），
联机时只能读到客户端的"空壳"容器。现在：

- `fabric.mod.json` 改为 `environment: "*"`，新增 `main` + `client` 双入口
- 新增 C2S / S2C 网络通道（1.20.1 用旧版 `ServerPlayNetworking`，1.21 用 `CustomPayload`）
- 客户端只发「物品 ID + 数量」，服务端执行取物后回包；服务端没装时自动降级为本地取物并提示

### 新增：Fabric 学习模式

此前只有 Forge / NeoForge 有学习模式。现在 Fabric 也支持：
`key.voiceblock.learn`（鼠标侧键 4）、`/voiceblock _learn replace|alias`、
「已有发音时二选一（覆盖 / 加为模糊名称）」的聊天按钮，行为与 Forge 一致。

### 新增：`/voiceblock test <物品ID> [数量]`

不经过麦克风，直接走一次完整的取物链路（与语音识别的结果路径完全相同）。
联机排查"服务端装没装 / 通道通不通 / 哪个源报错"时一句话自证。四个平台都有。

### 修复：Vosk 加载失败会导致客户端崩溃

原来 Vosk 初始化写在 `ClientEvents` 的静态初始化块里，只 `catch (Exception)`；
而原生库缺失／平台不支持时抛的是 `NoClassDefFoundError` / `UnsatisfiedLinkError`（Error），
会让整个客户端类初始化失败、游戏崩在启动阶段。

现在改为延迟初始化 + `catch (Throwable)`：最坏情况只是语音不可用，游戏仍可正常进入
（对视障玩家尤其重要）。

### 其它

- `run/` 拆分为 `run-client/` 与 `run-server/`，避免开发时两端抢同一个世界导致登录超时
- `fabric.mod.json` 版本号与 Gradle 版本号对齐（此前残留 2.0.7）
- 新增 `docs`/交接文档中描述的完整物品源体系、学习模式、服务端通道说明


## v1.3.0 — 2026-10-17

### 核心改动：识别率提升 + 手动绑定 + 物品自动发现

#### 识别率提升：N-best 多候选重排序
- Vosk 现在返回前 **5 个候选**（不再是只看第 1 名）
- `BlockMatcher` 改造三层匹配策略：
  1. **manual override 硬绑定**（拼音匹配，命中即返回，不评分）
  2. **精确名称/别名**（按候选顺序遍历）
  3. **N-best 拼音评分**（每个候选对每个 entry 打分，按 entry 取最高，最后取全局最高）
- 实测效果：以前"安山机壳"识别成"安山基球"完全匹配不到，现在 5 个候选里大概率有一个是"安山机壳"，自动选对

#### 手动绑定：玩家定制发音
- 配置文件：`config/voiceblock/manual_overrides.json`
- 格式：`{"triggerPinyin": "anshanjike", "targetId": "create:andesite_casing", "note": "我的口音"}`
- 触发键说"安山机壳"识别成什么鬼都不怕，只要那个候选的拼音命中规则，就硬指到你想取的方块
- 配置文件可直接拷贝分享给其他玩家

#### 物品自动发现：覆盖所有 mod 物品
- 1.2.0 时只扫 BLOCK 注册表，纯物品（无对应方块）漏掉
- 1.3.0 改用 **`item.getDefaultInstance().getHoverName()`** 跨版本拿名称，扫 `BuiltInRegistries.ITEM` 全量
- **Block 优先**：已在 BLOCK 表里的 id 跳过
- **中文优先**：拿不到中文本地化的物品跳过（避免英文名称污染词典）
- 效果：装新 mod 零配置即可用语音调取大部分物品

#### /voiceblock 调试命令家族
- `/voiceblock` 显示帮助
- `/voiceblock debug` 显示最近一次识别的 N-best 候选文本 + 拼音
- `/voiceblock bind <序号> <方块ID>` 把指定候选的拼音绑到方块（写 overrides.json）
- `/voiceblock list` 列出所有手动绑定
- `/voiceblock remove <拼音>` 删除一条绑定
- `/voiceblock dedup` 扫 4 档冲突 + 输出 `config/voiceblock/duplicates_report.md`
- `/voiceblock reload` 热重载 overrides.json

#### /voiceblock dedup 报告分档
1. **name/alias 完全撞名**（必查）
2. **同一 Item**（id 反查命中，不同 id 指向同一物品）
3. **拼音完全一致**（同音字）
4. **拼音高相似**（≥ 0.85）
仅报告不修改，决策由人做。

#### 代码组织
- 新增 `match/ManualOverrides.java`（加载/查询/写入）
- 新增 `match/DedupScanner.java`（扫描 + 报告）
- 新增 `debug/DebugCommands.java`（命令逻辑，跨平台共用）
- 新增 `voice/RecognitionResult.java`（N-best 候选封装）
- `vosk/VoskRecognizer` 加 `setMaxAlternatives(5)` + JSON 解析 alternatives
- `match/BlockMatcher` 接收 `RecognitionResult`，三层策略
- `match/BlockData` 增加 ITEM 注册表扫描
- 4 个平台 ClientEvents 接入 brigadier 客户端命令

#### 多版本构建产物
| 产物 | 大小 | 适用加载器 / MC 版本 | Java |
|------|------|---------------------|------|
| voiceblock-fabric-1.20.1-1.3.0.jar | 66.8 MB | Fabric 1.20.1 ~ 1.20.4 | 17 |
| voiceblock-fabric-1.21-1.3.0.jar | 66.8 MB | Fabric 1.21.x | 21 |
| voiceblock-neoforge-1.21-1.3.0.jar | 66.8 MB | NeoForge 1.21 | 21 |
| voiceblock-forge-1.20.1-1.3.0-all.jar | 68.3 MB | Forge 1.20.1 ~ 1.20.6 | 17 |

#### 已知差异
- fabric-api 0.92.2（1.20.1）和 0.100.6（1.21）的 v2 source class 名称相同（`FabricClientCommandSource`），但 0.100.6 已移除 v1 的 `ClientCommands` 工具类。两个 fabric 子项目各自维护 ClientEvents.java
- NeoForge / Forge 共享 `neoforge/java/` 和 `forge/java/`，命令注册用 brigadier 通用 API

## v1.2.0 — 2026-10-05

### 核心改动：生存模式支持 + 按键音效提示

#### 新增：生存模式背包交换
- 生存模式下不再凭空生成物品，而是在背包中查找目标物品并与当前选中的快捷栏格子**交换**
- 物品已在手上 → 不操作；背包中有该物品 → 交换到当前选中格；背包中没有 → 提示"背包中没有: XXX"
- 使用 `ClickType.SWAP` 通过容器点击包通知服务器，**无幽灵方块**，服务端与客户端完全同步
- 创造模式保持原逻辑：直接在快捷栏生成 64 个目标物品

#### 新增：按键音效提示
- **按下触发键**：播放 `UI_BUTTON_CLICK`（清脆高音点击声，提示开始聆听）
- **松开触发键**：播放 `EXPERIENCE_ORB_PICKUP`（经验球拾取音，提示识别结束）
- 新增 `SoundUtil` 跨版本音效工具类，通过反射兼容 1.20.x（SoundEvent 直接返回）与 1.21+（Holder 包装）的 API 差异

#### 方块库扩充
- blocks.json 从 1604 个方块增至 **2195 个**（新增 591 个，主要补全原版物品）
- 当前覆盖：minecraft(1411) + create(643) + simulated(95) + aeronautics(43) + offroad(3)

#### 修复
- 生存模式下交换物品出现幽灵方块的问题

#### 多版本构建产物
| 产物 | 大小 | 适用加载器 / MC 版本 | Java |
|------|------|---------------------|------|
| voiceblock-fabric-1.20.1-1.2.0.jar | 66.7 MB | Fabric 1.20.1 ~ 1.20.4 | 17 |
| voiceblock-fabric-1.21-1.2.0.jar | 66.7 MB | Fabric 1.21.x | 21 |
| voiceblock-neoforge-1.21-1.2.0.jar | 66.7 MB | NeoForge 1.21 | 21 |
| voiceblock-forge-1.20.1-1.2.0-all.jar | 68.3 MB | Forge 1.20.1 ~ 1.20.6 | 17 |

Forge 用户请使用 -all.jar（已内嵌 vosk + pinyin4j 依赖）。

## v1.1.0 — 2026-10-05

### 核心改动：内置语音模型 + 外置模型覆盖

#### 语音模型一体化封装
- 将 Vosk 中文小模型（vosk-model-small-cn-0.22，65MB）打包进所有 4 个发行 jar
- 用户只需把 jar 放入 mods 文件夹，首次启动游戏自动解压模型到 config/voiceblock/vosk-model/
- 无需手动下载模型，开箱即用

#### 支持外置大模型覆盖
- 模型加载优先级：config/voiceblock/vosk-model/（外置） > jar 内置模型
- 用户可自行下载高精度大模型 vosk-model-cn-0.22（1.3GB，字错误率 13.98%）
  解压覆盖到 config/voiceblock/vosk-model/ 即可提升识别精度，无需重新装 jar
- 小模型字错误率 23.54%，适合低配机器；大模型适合追求精度的用户

#### VoskRecognizer 重构
- 新增 isValidModel() 校验：检测 conf/model.conf 和 am/final.mdl 是否存在
- 新增 extractModelFromJar()：从 jar 资源按 vosk-model-files.txt 清单解压模型
- getInstance() 不再抛异常，初始化失败返回 null，避免游戏崩溃
- 模型缺失时在游戏内显示红字提示并输出下载地址到日志
- 新增 isReady() 静态方法供外部检测识别器状态

#### 多版本构建产物（不覆盖 v1.0.0）
| 产物 | 大小 | 适用加载器 / MC 版本 |
|------|------|---------------------|
| voiceblock-fabric-1.20.1-1.1.0.jar | 66.7 MB | Fabric 1.20.1 ~ 1.20.4 |
| voiceblock-fabric-1.21-1.1.0.jar | 66.7 MB | Fabric 1.21.x |
| voiceblock-neoforge-1.21-1.1.0.jar | 66.7 MB | NeoForge 1.21 |
| voiceblock-forge-1.20.1-1.1.0-all.jar | 68.3 MB | Forge 1.20.1 ~ 1.20.6 |

Forge 用户请使用 -all.jar（已内嵌 vosk + pinyin4j 依赖）。

#### 构建配置
- 所有子项目版本号从 1.0.0 升至 1.1.0（build.gradle + 元数据文件）
- settings.gradle 补充 projectDir 映射，修复子项目目录名与 include 名不一致的问题

### 关于 Java 版本
- MC 1.20.1 / 1.20.4（Fabric / Forge）：运行需要 Java 17
- MC 1.21.x（Fabric / NeoForge）：运行需要 Java 21

## v1.0.0 — 初始版本
- 语音识别切换方块功能（Vosk 离线中文识别 + pinyin4j 拼音匹配）
- 支持 1604 个方块（原版 + Create + Aeronautics）
- 多版本：Fabric 1.20.1 / 1.21、NeoForge 1.21、Forge 1.20.1
- 按键触发语音识别，HUD 显示识别结果