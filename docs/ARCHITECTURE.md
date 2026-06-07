# Sift — 架构设计

> 本地优先的个人知识 Agent。刷到的好内容，一键截屏，由 Agent 决定要不要留、怎么归类、跟过去的东西怎么关联。
> 大模型由用户自带 API Key（BYO-Key），Sift 本身不内置任何云端服务、不收集数据。

---

## 1. 设计原则（这些是不可妥协的）

| 原则 | 含义 | 为什么 |
|---|---|---|
| **Local-first** | 所有数据存本机（Room / 加密存储），不上任何 Sift 服务器 | 截屏可能含隐私；开源项目不该碰用户数据 |
| **BYO-Key** | LLM 调用用用户自己的 Key，Sift 不代理 | 零运营成本、零信任负担、可换任意厂商 |
| **诚实而非安慰** | 用真实数据说话（刷了多久 vs 沉淀几条），不美化刷视频 | 反焦虑产品的护城河是诚实，不是操纵情绪 |
| **Agent 会拒绝** | 纯娱乐内容主动丢弃，不是来者不拒 | "会筛"才是 agent，无脑存只是收藏夹 |
| **可测的核心** | 业务逻辑放纯 Kotlin 模块，不依赖 Android framework | 能在 JVM 上跑单测，像被认真维护的项目 |

---

## 2. 模块划分（Clean Architecture，依赖单向向内）

```
:app                Compose UI + 导航 + DI 装配(Hilt)。只接线，不写业务逻辑。
  │  depends on ↓
:core:capture       Android 平台能力：MediaProjection 截屏服务、悬浮球 Overlay 服务、
  │                 UsageStats 读取。把"安卓特有的脏活"隔离在这里。
  │  depends on ↓
:core:data          Room 数据库、Repository 实现、EncryptedSharedPreferences(存 Key)、
  │                 LLMProvider 各厂商实现(OpenAI 兼容 / Anthropic)。
  │  depends on ↓
:core:domain        ★纯 Kotlin，零 Android 依赖。实体 + Agent 循环 + Tool 接口 +
                    LLMProvider 接口 + Repository 接口。←单测全打在这层。
```

依赖规则：外层可以依赖内层，内层**绝不**反向依赖外层。`:core:domain` 不 import 任何 `android.*`，所以它能在普通 JVM 单测里跑。

---

## 3. 核心：Capture Agent 循环

这是 Sift "是个 Agent"的体现。一次截屏触发一次 agent run，标准 tool-use 循环：

```
                  ┌──────────────────────────────────────────┐
  悬浮球点击 ─────▶│ 1. CaptureService 截屏 (MediaProjection)   │
                  └──────────────────────────────────────────┘
                                   │ Bitmap
                                   ▼
                  ┌──────────────────────────────────────────┐
                  │ 2. CaptureAgent.run(image)                 │
                  │    构造 system prompt + 注册 tools，进入循环 │
                  └──────────────────────────────────────────┘
                                   │
        ┌──────────────────────────┴───────────────────────────┐
        ▼                                                        │
  LLMProvider.chat(messages, tools)                              │
        │  模型返回 tool_call 或 final text                       │
        ▼                                                        │
  是 tool_call？ ──是──▶ 执行对应 Tool ──▶ 把结果塞回 messages ───┘ (loop)
        │
        否(final)
        ▼
  Agent 产出 CaptureResult { kept: Boolean, note?: KnowledgeNote }
```

### 注册给模型的 Tools（都是本地函数，模型只负责调度）

| Tool | 作用 | 体现的 agent 性 |
|---|---|---|
| `decide_keep(reason)` | 判定为纯娱乐 → 丢弃；判定为知识 → 继续 | **自主决策、会拒绝** |
| `search_similar(query)` | 在本地知识库里语义/关键词查重 | **有记忆** |
| `tag_and_categorize(tags, category)` | 打标签、归类 | 结构化 |
| `relate(noteIds)` | 与过去的笔记建立关联 | **构建知识图谱** |
| `save_note(title, summary, keyPoints, source)` | 写入 Room | 落地 |

> v0.1 可以把循环简化为「单次多模态调用直接产出结构化 JSON」(等价于模型一次性调齐所有 tool 的结果)。
> 真正的多轮 tool-use 循环作为 v0.2 升级——架构上两者共用 `LLMProvider` 与 `Tool` 接口，无需重写。

---

## 4. LLMProvider 抽象（BYO-Key 的工程核心，也是"我懂 agent 架构"的橱窗）

```kotlin
// :core:domain — 纯接口，不绑任何厂商
interface LLMProvider {
    val id: String                    // "openai", "anthropic", "deepseek"...
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec> = emptyList(),
        config: LlmConfig,            // model 名、温度、baseUrl(兼容自建/中转)
    ): LlmResponse                    // 内含 text 或 toolCalls
}
```

实现（放 `:core:data`）：

- `OpenAICompatibleProvider` —— 一份代码覆盖 **OpenAI / DeepSeek / Moonshot / 通义 / 本地 Ollama / 各种中转**，靠 `baseUrl` 区分。绝大多数厂商走 OpenAI 兼容协议。
- `AnthropicProvider` —— Claude 的 messages API（tool-use 格式不同，单独实现）。

用户在设置页选 provider + 填 baseUrl + Key + model 名。Key 用 `EncryptedSharedPreferences` 存，**绝不进任何日志、绝不进备份**。

---

## 5. 数据模型（Room）

```kotlin
@Entity data class KnowledgeNote(
    @PrimaryKey val id: String,        // UUID
    val createdAt: Long,
    val title: String,
    val summary: String,               // AI 生成的一句话总结
    val keyPoints: List<String>,       // 知识点列表 (TypeConverter -> JSON)
    val category: String,              // AI 归类
    val tags: List<String>,
    val sourceApp: String?,            // 抖音/B站/小红书... (从前台包名推断)
    val rawImagePath: String?,         // 原图本地路径(可选保留, 默认用完即删保护隐私)
    val reviewCount: Int = 0,          // 回看次数 —— 喂给"利用率"指标
    val lastReviewedAt: Long? = null,
)

@Entity data class NoteRelation(       // 知识图谱的边
    @PrimaryKey val id: String,
    val fromNoteId: String,
    val toNoteId: String,
    val reason: String,                // 为什么相关(AI 给的)
)

@Entity data class DailyUsage(         // "数据说话"的原料
    @PrimaryKey val date: String,      // yyyy-MM-dd
    val scrollMillis: Long,            // 来自 UsageStatsManager
    val capturedCount: Int,
    val keptCount: Int,
)
```

---

## 6. "数据说话"仪表盘（App 的灵魂界面，不是附属）

三个诚实的数字，并排，不评判：

1. **本周刷了多久** — `UsageStatsManager` 读抖音/B站等前台时长。
2. **沉淀了几条知识** — `keptCount`。
3. **收藏利用率** — `回看过的笔记数 / 总笔记数`，难看也照实显示。

衍生：**周报 Agent**（v0.2）——每周让 agent 把本周笔记汇成一篇 Markdown，可导出/分享，天然传播点。

---

## 7. 隐私底线（开源项目会被人 review 这部分）

- 截图默认**用完即删**（OCR/分析后丢弃 Bitmap），`rawImagePath` 仅在用户显式开启"保留原图"时写盘。
- API Key 走 `EncryptedSharedPreferences`，从不写日志、`android:allowBackup="false"`。
- 截屏内容只发往**用户自己配置的** endpoint，App 内不存在任何 Sift 自有上报。
- `MediaProjection` 每次授权都有系统提示，符合 Android 合规要求。

---

## 8. 技术栈

| 关注点 | 选型 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose |
| 架构 | Clean Architecture + 单向数据流 (MVI-ish ViewModel) |
| DI | Hilt |
| 本地库 | Room (SQLite) |
| 异步 | Coroutines + Flow |
| 网络 | Ktor Client 或 OkHttp + kotlinx.serialization |
| 加密存储 | androidx.security.crypto (EncryptedSharedPreferences) |
| 截屏 | MediaProjection API |
| 悬浮球 | SYSTEM_ALERT_WINDOW + 前台 Service |
| 使用时长 | UsageStatsManager (PACKAGE_USAGE_STATS 权限) |
| 测试 | JUnit5 + kotlin.test (打在 :core:domain) |
| 最低版本 | minSdk 26 (Android 8.0) / targetSdk 34 |

> iOS 不在范围内：沙盒禁止跨 App 截屏，悬浮球也不可行。Sift 是 Android-only，这是平台能力决定的，不是偷懒。
