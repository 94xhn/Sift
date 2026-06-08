<div align="center">

# Sift

**刷到的好东西，别让它划走就丢了。**
A local-first personal knowledge **agent** for the age of endless scrolling.

[![CI](https://github.com/94xhn/Sift/actions/workflows/ci.yml/badge.svg)](https://github.com/94xhn/Sift/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 这是什么 / What it is

刷短视频/读长文时看到有用的东西，划走就再也找不到了。Sift 让你点一下悬浮球，截屏交给一个 **on-device agent**：它读懂屏幕内容、总结成笔记、归类，并**检索你已有的笔记**判断是否重复、跟旧笔记建立关联——攒着攒着长成一张属于你自己的知识图谱。

它**不**试图让你"刷得心安理得"。它用真实数据跟你对话：本周刷了多久、沉淀了几条、收藏利用率多少；久刷零沉淀时它甚至会劝你直接休息。诚实，是它跟那些自欺打卡 App 的分界线（见 [docs/PHILOSOPHY.md](docs/PHILOSOPHY.md)）。

> Tap a floating ball → screenshot → an on-device agent summarizes it, runs RAG over your existing notes to dedup/relate, and saves a structured note. All data stays on-device. Bring your own LLM API key.

## 为什么说它是 Agent / Why "agent"

不是套时髦词，是真的 tool-use agent：

1. **多轮工具调用循环** — 模型可自主调用 `search_similar` 工具检索你已有的笔记，看到结果后再决定怎么总结/是否重复/归到哪类（有界推理循环 + 强制收尾）。
2. **对私有笔记的 RAG** — `search_similar` 走 embedding + 余弦 Top-K 语义检索，失败自动降级关键词。
3. **有记忆、会成长** — agent 把相关笔记连成边，形成可视化的个人知识图谱，越用越懂你。

详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 核心特性 / Features

- 🎈 **悬浮球一键捕获** — 在任意 App 上方截屏（截图瞬间自动隐藏自己），不打断刷视频
- 🤖 **tool-use Agent** — 检索旧笔记 → 总结 / 归类 / 查重 / 关联，而非无脑收藏
- 🔎 **RAG 语义检索** — embedding + 余弦 Top-K（暴力，个人规模够用），降级关键词
- 🕸️ **知识图谱** — 笔记自动关联 + Canvas 可视化，点节点跳转
- 📰 **周报 Agent** — 本周笔记一键汇成 Markdown 周报，可复制/分享
- 📊 **诚实仪表盘** — 刷视频时长 vs 沉淀数 vs 收藏利用率，用数据说话
- 🔑 **自带 API Key (BYO-Key)** — 任何 OpenAI 兼容服务（智谱 / OpenAI / DeepSeek / Moonshot / 本地 Ollama / 中转…）
- 🔒 **本地优先** — 数据全存本机，截图默认用完即删，Key 加密存储，零云端上报

## 状态 / Status

🟢 **早期但可用 (early, functional)**。模拟器实测：捕获 → AI 总结 → 沉淀 → 关联 全链路跑通。
核心逻辑有单测，CI 绿。Android-only（iOS 沙盒禁止跨 App 截屏，技术不可行）。路线见 [ROADMAP.md](ROADMAP.md)。

## 配置 / Configure

App 内「API 设置」填三项即可（自带 Key，数据只发往你自己配置的 endpoint）：

| 字段 | 推荐（免费、大陆直连） |
|---|---|
| Base URL | `https://open.bigmodel.cn/api/paas/v4` |
| 模型名（需多模态） | `glm-4.6v-flash` |
| 嵌入模型 | `embedding-3` |
| API Key | 在 [bigmodel.cn](https://bigmodel.cn) 注册创建 |

> 模型**必须支持图片输入**（多模态）；任何 OpenAI 兼容 endpoint 都行，靠 Base URL 区分。

## 构建 / Build

需要 **Android Studio**（自带 JDK 17 + Android SDK + Gradle）：用 IDE 打开根目录 → Gradle sync → 连真机/模拟器 Run。

只想验核心逻辑（纯 Kotlin，不需要 Android SDK）：

```bash
./gradlew :core:domain:test
```

## 项目结构 / Structure

```
:app            Compose UI、导航、Hilt 装配、截图处理管线（只接线，不写业务逻辑）
:core:capture   MediaProjection 截屏、悬浮球 Overlay、UsageStats（隔离 Android 脏活）
:core:data      Room、Repository 实现、LLMProvider/EmbeddingProvider、加密存储
:core:domain    纯 Kotlin：实体 + Agent 循环 + Tool/LLMProvider 接口 + 向量数学（单测打这层）
docs/           架构 (ARCHITECTURE) 与设计哲学 (PHILOSOPHY)
```

技术栈：Kotlin · Jetpack Compose · Hilt · Room · Coroutines/Flow · OkHttp · kotlinx.serialization。

## 隐私 / Privacy

- 截图默认 **用完即删**，仅在你显式开启时保留原图。
- API Key 用 `EncryptedSharedPreferences` 加密存储，从不写日志，关闭系统备份。
- 截屏内容只发往**你自己配置的** endpoint，App 内无任何 Sift 自有上报。

## 许可 / License

MIT — 见 [LICENSE](LICENSE)。

---

<div align="center"><sub>诚实的工具，配诚实的名字。Honest tool, honest name.</sub></div>
