# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/) 与语义化版本。

## [Unreleased]

### Added
- 项目骨架：4 模块 Clean Architecture（`:app` / `:core:capture` / `:core:data` / `:core:domain`）。
- `:core:domain`：领域模型、`LLMProvider` / `Tool` / Repository 接口、`CaptureAgent`（单次调用版）+ JVM 单测。
- `:core:data`：Room 持久化、`OpenAICompatibleProvider`、`EncryptedSharedPreferences` 存 Key、Hilt 装配。
- `:core:capture`：MediaProjection 截屏服务、悬浮球 Overlay 服务、UsageStats 读取。
- `:app`：Compose UI（主屏 / 设置 / 笔记列表 / 详情）、投屏触发 Activity、截图处理管线。
- **诚实仪表盘**：今日刷视频时长 vs 沉淀数 vs 收藏利用率；`HonestNudge` 纯逻辑（久刷零沉淀时温和劝休息，而非制造内疚）+ 单测；`docs/PHILOSOPHY.md` 设计立场。
- 截屏改为 **一次授权、整个会话复用**（`MediaProjectionHolder` 保活 projection），不再每次点悬浮球都弹投屏授权框。
- 打开笔记即记为"已回看"，喂给利用率指标。
- **手动捕获默认总结、不再二次过滤**：既然每次都是用户主动点球，agent 默认 keep=true 出总结（仅纯空白页才跳过），直接服务"长文懒得读、要 AI 总结"的诉求；"会筛/会拒绝"的过滤智能留给未来的自动捕获模式。
- 运行时申请 POST_NOTIFICATIONS（否则结果通知/Toast 被静默吞掉）；悬浮球加 2s 防抖避免撞免费档限流；OkHttp 读/写超时放宽到 60s（视觉模型慢）；捕获管线加 Logcat 日志（tag SiftCapture）。
- 体验打磨：截图前自动隐藏悬浮球（抓"下一帧"无球的新鲜帧，静止画面 400ms 兜底用缓存）；主屏显示"最近捕获结果"卡片（通知被吞也能看到）。
- CI：GitHub Actions（domain 单测 + gitleaks），README 加 CI / MIT 徽章。
- 提示词升级：明确忽略状态栏/导航栏/平台 logo 等界面家具，只总结内容主体；按"知识密度"调整详略（知识密集多抽要点，信息稀薄不硬凑）。
- UI 统一：全 App 改用原生顶栏返回箭头（`SiftScaffold`），去掉底部"返回"大按钮；笔记详情页标签改 Chip + 显示时间/来源 + 删除按钮；设置页占位符改为推荐的免费 GLM-4.6V-Flash。
- **真 tool-use Agent**：`CaptureAgent` 升级为多轮工具调用循环（有界 maxSteps）。模型可自主调用 `search_similar` 工具检索用户已有笔记，据此查重/保持归类一致后再产出最终笔记。`OpenAICompatibleProvider` 支持发送 `tools`、解析 `tool_calls`、编码 assistant.tool_calls / role=tool 消息。新增循环单测。无工具时自动退化为单次调用（向后兼容）。
- **知识图谱**：agent 输出 `related_note_ids`，`CaptureProcessor` 据此建立 `NoteRelation` 边；笔记详情页显示"相关笔记"并可互相跳转。
- **RAG 语义检索**：`EmbeddingProvider`（OpenAI 兼容 `/embeddings`，默认智谱 embedding-3）+ 纯函数 `cosine`/`topKByCosine`/FloatArray↔ByteArray（含单测）。笔记保存时算 embedding 存入 Room（v2 schema，BLOB 列）；`search_similar` 改为"语义检索优先、失败降级关键词"。向量暴力 Top-K，个人规模够用、不引入向量库。
- **周报 Agent**：`WeeklyReportAgent` 把本周笔记汇成一篇 Markdown 周报（按主题归纳 + 提炼学到了什么 + 下周建议，诚实不灌水）；周报页可复制/分享/重新生成。空笔记不调用模型、调用失败如实提示（含单测）。
- **知识图谱可视化**：Canvas 圆形布局节点-连线图，点节点跳转到该笔记。
- 设置页可配置**嵌入模型**（语义检索用，默认 embedding-3）。
- 补齐 **gradle wrapper**（gradlew/gradlew.bat/gradle-wrapper.jar @ 8.9）：clone 后可直接 `./gradlew`。

### Fixed
- `:core:domain` 模块 Kotlin/Java target 不一致（21 vs 17）导致编译失败：显式设 Kotlin `jvmTarget = 17`。
