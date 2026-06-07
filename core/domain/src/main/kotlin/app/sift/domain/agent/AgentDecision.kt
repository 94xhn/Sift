package app.sift.domain.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单次调用模式下，模型被要求输出的结构化决策。
 * 字段全部可空 + 宽松解析，容忍模型输出的小瑕疵（缺字段 / 多字段）。
 */
@Serializable
data class AgentDecision(
    val keep: Boolean,
    val reason: String? = null,
    val title: String? = null,
    val summary: String? = null,
    @SerialName("key_points") val keyPoints: List<String>? = null,
    val category: String? = null,
    val tags: List<String>? = null,
)
