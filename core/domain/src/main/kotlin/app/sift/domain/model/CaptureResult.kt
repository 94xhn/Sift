package app.sift.domain.model

/** 一次捕获请求：截图 + 上下文。 */
data class CaptureRequest(
    val imageBase64: String,
    val mimeType: String = "image/png",
    val sourceApp: String? = null,
    /** 已有分类，作为归类一致性的提示，避免每次都新造类目。 */
    val knownCategories: List<String> = emptyList(),
)

/** Agent 处理一次截图的结果。 */
sealed interface CaptureResult {
    /** 判定为有价值的知识，已生成笔记。relatedNoteIds 为 agent 认为相关的既有笔记 id。 */
    data class Kept(
        val note: KnowledgeNote,
        val relatedNoteIds: List<String> = emptyList(),
    ) : CaptureResult

    /** 判定为纯娱乐 / 无沉淀价值，主动丢弃 —— 这是 agent "会拒绝"的体现。 */
    data class Discarded(val reason: String) : CaptureResult

    /** 处理失败（网络 / 解析 / Key 错误等）。 */
    data class Failed(val error: String) : CaptureResult
}
