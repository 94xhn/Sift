<div align="center">

# Sift

**刷到的好东西，别让它划走就丢了。**
A local-first personal knowledge **agent** for the age of endless scrolling.

</div>

---

## 这是什么 / What it is

刷短视频时刷到一个有用的知识点，划走就再也找不到了。Sift 让你点一下悬浮球，截屏交给一个 **Agent** 处理：它判断这是不是值得留的知识、自动总结成笔记、打标签归类、跟你过去存的东西关联起来。攒着攒着，就长成一张属于你自己的知识图谱。

它**不**试图让你"刷得心安理得"。它用真实数据跟你对话：本周刷了多久、沉淀了几条、收藏利用率多少。诚实，是它跟那些自欺打卡 App 的分界线。

> Tap a floating ball → screenshot → an on-device agent decides if it's worth keeping, summarizes it, tags it, and links it to your past notes. All data stays on your device. You bring your own LLM API key.

## 为什么说它是 Agent / Why "agent"

不是套时髦词。它的 agent 性体现在三点：

1. **会自主决策** — 截图进来，agent 自己判断"知识 vs 纯娱乐，要不要存"，会主动拒绝。
2. **有工具** — 查重、打标签、关联旧笔记、写库，都是本地 tool，模型负责调度（标准 tool-use 循环）。
3. **有记忆** — 维护本地知识库，新内容自动跟旧内容关联，越用越懂你。

详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 核心特性 / Features

- 🎈 **悬浮球一键捕获** — 在任意 App 上方截屏，不打断刷视频
- 🤖 **Agent 自动处理** — 决定去留 / 总结 / 打标签 / 关联，而非无脑收藏
- 🔑 **自带 API Key (BYO-Key)** — 支持 OpenAI 兼容协议（OpenAI / DeepSeek / Moonshot / Ollama / 中转…）与 Anthropic Claude
- 📊 **诚实仪表盘** — 刷视频时长 vs 知识沉淀 vs 收藏利用率，用数据说话
- 🔒 **本地优先** — 所有数据存本机，截图默认用完即删，Key 加密存储，零云端上报
- 🕸️ **个人知识图谱** — 笔记之间自动建立关联

## 状态 / Status

🚧 **早期开发中 (pre-v0.1)**。当前阶段：架构设计 + 项目骨架。详见 [ROADMAP.md](ROADMAP.md)。

## 构建 / Build

需要 **Android Studio**（自带 JDK 17 + Android SDK + Gradle）：

```bash
git clone https://github.com/94xhn/Sift.git
# 用 Android Studio 打开根目录，等待 Gradle sync，连真机/模拟器 Run
```

> Android-only。iOS 因沙盒禁止跨 App 截屏，技术上不可行。

## 项目结构 / Structure

```
:app            Compose UI、导航、Hilt 装配（只接线）
:core:capture   截屏服务、悬浮球、UsageStats（隔离 Android 脏活）
:core:data      Room、Repository 实现、LLMProvider 各厂商实现、加密存储
:core:domain    纯 Kotlin：实体 + Agent 循环 + Tool/LLMProvider 接口（单测打这层）
docs/           架构与设计文档
```

## 隐私 / Privacy

- 截图默认 **用完即删**，仅在你显式开启时保留原图。
- API Key 用 `EncryptedSharedPreferences` 加密存储，从不写日志，关闭系统备份。
- 截屏内容只发往**你自己配置的** endpoint，App 内无任何 Sift 自有上报。

## 许可 / License

MIT — 见 [LICENSE](LICENSE)。

---

<div align="center"><sub>诚实的工具，配诚实的名字。Honest tool, honest name.</sub></div>
