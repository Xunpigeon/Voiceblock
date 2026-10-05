# VoiceBlock 更新日志

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