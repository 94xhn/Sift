# Sift Roadmap

语义化版本。范围纪律：**能跑通的一条线 > 半残的五条线。**

## v0.1 — 端到端最小闭环（MVP）

目标：悬浮球 → 截屏 → 调用户 API → 出结构化笔记 → 存库 → 列表能看。
此版本 agent 循环允许简化为「单次多模态调用直接产出结构化 JSON」。

- [ ] 项目骨架：4 模块 Gradle 工程，Hilt + Compose + Room 跑通空壳
- [ ] `:core:domain`：实体、`LLMProvider`/`Tool`/Repository 接口、`CaptureAgent`（单次调用版）
- [ ] `:core:data`：Room 库、`OpenAICompatibleProvider`、`EncryptedSharedPreferences` 存 Key
- [ ] `:core:capture`：MediaProjection 截屏 + 悬浮球 Overlay 前台服务
- [ ] `:app`：设置页（填 Key/baseUrl/model）、笔记列表、笔记详情
- [ ] `:core:domain` 单测：JSON 解析、provider 请求构造、agent 决策分支
- [ ] 开源标准件 + 一段 demo GIF

## v0.2 — 真 Agent + 诚实仪表盘

- [x] 诚实仪表盘：UsageStats 时长 vs 沉淀数 vs 利用率 + `HonestNudge` 劝退逻辑
- [x] `CaptureAgent` 升级为真正的多轮 tool-use 循环（有界步数，已接入 search_similar）
- [x] 本地查重（关键词检索 search_similar）；嵌入向量语义检索留 v0.3
- [x] 知识图谱：`NoteRelation` 建边 + 关联展示（agent 输出 related_note_ids）
- [x] RAG 向量升级：embedding + 余弦 Top-K（语义检索优先、降级关键词）
- [ ] `AnthropicProvider`（Claude tool-use）
- [ ] 设置页加 embedding 模型字段（当前默认 embedding-3，不可改）

## v0.3 — 回看与传播

- [ ] 间隔重复推送（遗忘曲线把笔记推回来）
- [x] 周报 Agent：本周笔记汇成 Markdown，可复制/分享/重生成
- [ ] 知识图谱可视化

## Backlog / 待定

- [ ] 本地小模型 OCR 预处理（省 token / 离线兜底）
- [ ] 嵌入向量做语义查重（替代关键词）
- [ ] 导出到 Obsidian / Notion
