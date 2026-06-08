# Sift — 项目背景与现状（接手必读）

> 这份文档让任何人（包括未来的你 / 另一个 AI）翻开就能接上：项目为什么存在、做成了什么样、
> 关键决策为何如此、还有哪些坑、以及怎么继续往下做。
> 配套文档：产品哲学 [PHILOSOPHY.md](PHILOSOPHY.md) · 架构 [ARCHITECTURE.md](ARCHITECTURE.md) ·
> 路线 [../ROADMAP.md](../ROADMAP.md) · 变更 [../CHANGELOG.md](../CHANGELOG.md)

---

## 1. 缘起 / 它解决什么

人会一边刷短视频、一边后悔把时间用来刷短视频。最初的念头是"做个让刷视频显得有产出的 App"，
但讨论后否定了"放大焦虑"的方向（放大焦虑会反噬：用户缓解焦虑最快的方式是卸载你的 App）。
最终定位翻转为：

> **Sift = 一个本地优先的个人知识 Agent。** 刷到/读到有用的东西，点悬浮球截屏，
> agent 读懂内容、总结成笔记、检索旧笔记查重与关联，沉淀成个人知识图谱。
> 它不美化刷视频，用真实数据说话；该劝休息时就劝休息。

定位是作品集 / 学习项目（对标"AI Agent 工程师"方向），不以盈利为目标，开源、BYO-Key。

---

## 2. 当前状态（截至最近一次提交）

🟢 **早期但可用**。模拟器实测：捕获 → AI 总结 → 沉淀 → 关联 全链路跑通（用智谱 GLM-4.6V-Flash）。

已实现的能力：
- **悬浮球一键捕获**：MediaProjection 截屏（截图瞬间自动隐藏悬浮球），一次授权整会话复用。
- **tool-use Agent**：多轮工具调用循环（有界步数 + 最后一步强制收尾），模型自主调 `search_similar`。
- **RAG 语义检索**：embedding + 余弦 Top-K（暴力），失败降级关键词 LIKE。
- **知识图谱**：agent 输出 related_note_ids → 建 `NoteRelation` 边；Canvas 节点-连线图可视化，点节点跳转。
- **周报 Agent**：本周笔记汇成 Markdown，可复制/分享。
- **诚实仪表盘**：刷视频时长 vs 沉淀数 vs 收藏利用率 + 久刷零沉淀时劝休息。
- **BYO-Key + 多厂商**：一份 OpenAI 兼容 Provider 靠 baseUrl 覆盖智谱/OpenAI/DeepSeek/Moonshot/Ollama/中转。
- **工程**：4 模块 Clean Arch、Hilt、Room、纯逻辑单测、GitHub Actions CI（domain 测试 + gitleaks）全绿、gradle wrapper 齐。

仓库：https://github.com/94xhn/Sift （Public, MIT）。本地目录 `J:\claudeproject\Sift\`。

---

## 3. 架构速览

```
:app           Compose UI + 导航 + Hilt 装配 + 截图处理管线 CaptureProcessor（只接线）
:core:capture  MediaProjection 截屏 / 悬浮球 Overlay / UsageStats（隔离 Android 脏活）
:core:data     Room / Repository 实现 / LLMProvider+EmbeddingProvider(OpenAI 兼容) / 加密存 Key
:core:domain   纯 Kotlin：实体 + CaptureAgent 循环 + Tool/LLMProvider 接口 + 向量数学（单测打这层）
```
依赖单向向内。核心数据流：
`悬浮球→投屏授权→截屏→CaptureProcessor→CaptureAgent(tool 循环, 调 search_similar)→落库+建边+算 embedding→通知/UI`

详见 [ARCHITECTURE.md](ARCHITECTURE.md)。

---

## 4. 关键决策记录（为什么这么做）

| 决策 | 理由 |
|---|---|
| **Android-only** | iOS 沙盒禁止跨 App 截屏、悬浮球不可行；是平台能力决定，不是取舍。 |
| **不放大焦虑、反而劝休息** | 放大焦虑→用户卸载；诚实/缓解焦虑才有留存。见 PHILOSOPHY.md。 |
| **手动触发默认 keep=true** | 每次都是用户主动点，已是筛选；agent 再二次否决惹人烦。"会拒绝"留给未来自动捕获。 |
| **v0.1 单次调用 → v0.2 才升 tool-use** | 先把价值跑通再加复杂度；两者共用 LLMProvider/Tool 接口，不返工。 |
| **tool-use 循环最后一步抽掉工具** | 弱模型(GLM-4.6V-Flash)会反复调工具不收尾，强制收尾保证终结。 |
| **RAG 用暴力余弦，不上向量库** | 个人规模几百上千条，逐个算 Top-K 毫秒级；Pinecone/HNSW 是百万级才需要。 |
| **语义检索失败降级关键词** | embedding 没配/出错/库空时搜索仍可用，绝不整体挂掉。 |
| **截图用完即删 / Key 加密 / 只发用户 endpoint** | 隐私是本地优先 agent 的信任基石，也是开源会被 review 的部分。 |
| **Room 破坏式迁移(fallbackToDestructiveMigration)** | 早期开发笔记是可丢测试数据；上线前再写正式 Migration。 |

---

## 5. 已知限制与坑（继续前必读）

- ⚠️ **CI 只编译 `:core:domain`**（跑 `gradle :core:domain:test`），**不编译 :core:data / :app**。
  那两层的编译错误只能在 **Android Studio build** 时暴露。改完 data/app 一定要在 AS 验。
- **本机 Java 8 跑不了现代 Android/Gradle**，编译+真机必须用 Android Studio（自带 JBR/SDK/Gradle）。
- **模型 function-call 不稳**：GLM-4.6V-Flash 对"图片+工具"混合请求支持一般；无工具时自动退化为单次调用，功能不退化。若它从不调工具，可换更友好的模型或调提示词。
- **免费档限流/慢**：连点撞 429（悬浮球已加 2s 防抖）；视觉慢十几秒（OkHttp 读超时已放宽 60s）。
- **Claude(Anthropic) 尚未实现**：协议(system 独立/content blocks/tool_use)与 OpenAI 不同，需单独写 Provider。
- **长文只截当前屏**：跨屏长文需"滚动连截+拼接"，留 v0.3。
- 嵌入模型默认 `embedding-3`（智谱）；换厂商需在设置页改。
- CI 的 actions 仍 Node20（2026-06-16 后需升 Node24，非阻塞警告）。

跨项目可复用的踩坑详见仓库外的 `J:\claudeproject\安卓错误教训.txt` 与 `agent创建经验.txt`。

---

## 6. 如何继续开发

**环境**：Android Studio（`C:\Program Files\Android\Android Studio\bin\studio64.exe`）打开根目录 → Gradle sync → Run。
**验核心逻辑**（不需 SDK）：`./gradlew :core:domain:test`（已补 wrapper）。
**配置 API**（App 内「API 设置」，推荐免费直连）：
- Base URL `https://open.bigmodel.cn/api/paas/v4` · 模型 `glm-4.6v-flash` · 嵌入 `embedding-3` · Key 在 bigmodel.cn 创建。

**改动后的提交流程**：小步提交、信息清晰；push 后看 GitHub Actions 绿勾。
**密钥红线**：API Key 只走 EncryptedSharedPreferences，绝不写日志/进 git。

**下一步候选**（按价值/难度）：
1. 真机验证图谱 / 周报 / 语义检索效果（先确认现有功能稳）。
2. `AnthropicProvider`（Claude tool-use）→ 坐实"多 LLM"。
3. 间隔重复推送（遗忘曲线把笔记推回来）→ 完成"回看"闭环。
4. 长文滚动拼接总结（v0.3 较大功能）。
5. CI actions 升 Node24；正式 Room Migration（上线前）。

---

## 7. 文档地图

| 文件 | 作用 |
|---|---|
| [README.md](../README.md) | 对外门面：是什么 / 特性 / 配置 / 构建 |
| [docs/PROJECT.md](PROJECT.md) | 本文：背景 / 现状 / 决策 / 如何继续（接手必读） |
| [docs/ARCHITECTURE.md](ARCHITECTURE.md) | 模块划分、agent 循环、数据模型、隐私底线 |
| [docs/PHILOSOPHY.md](PHILOSOPHY.md) | 为什么这样设计：焦虑分析 + 拒绝黑暗模式的立场 |
| [ROADMAP.md](../ROADMAP.md) | v0.1/v0.2/v0.3 进度 |
| [CHANGELOG.md](../CHANGELOG.md) | 逐项变更记录 |
