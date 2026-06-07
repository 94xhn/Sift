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

### Fixed
- `:core:domain` 模块 Kotlin/Java target 不一致（21 vs 17）导致编译失败：显式设 Kotlin `jvmTarget = 17`。
