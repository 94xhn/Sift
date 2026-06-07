package app.sift.domain.agent

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.ImageData
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.model.CaptureRequest
import app.sift.domain.model.CaptureResult
import app.sift.domain.model.KnowledgeNote
import app.sift.domain.util.Clock
import app.sift.domain.util.IdProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 全局共用的宽松 Json：忽略未知字段、容忍模型输出小瑕疵。 */
val SiftJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Sift 的核心 Agent。给定一张刷视频时的截图，决定是否值得沉淀，
 * 若值得则产出一条结构化 [KnowledgeNote]。
 *
 * v0.1：单次多模态调用，要求模型直接输出结构化 JSON（等价于一次性调齐所有工具结果）。
 * v0.2：升级为多轮 tool-use 循环（decide_keep / search_similar / relate / save_note），
 *       届时复用 [LLMProvider] 与 [Tool] 接口，本类对外签名不变。
 */
class CaptureAgent(
    private val clock: Clock,
    private val idProvider: IdProvider,
    private val json: Json = SiftJson,
) {
    suspend fun run(
        request: CaptureRequest,
        provider: LLMProvider,
        config: LlmConfig,
    ): CaptureResult {
        val messages = listOf(
            ChatMessage.system(CapturePrompt.system(request.knownCategories)),
            ChatMessage.user(
                text = CapturePrompt.userInstruction(request.sourceApp),
                images = listOf(ImageData(request.imageBase64, request.mimeType)),
            ),
        )

        val raw = try {
            provider.chat(messages, config = config).text
        } catch (e: Exception) {
            return CaptureResult.Failed(e.message ?: "LLM 调用失败")
        } ?: return CaptureResult.Failed("模型未返回文本")

        val decision = try {
            json.decodeFromString(AgentDecision.serializer(), extractJson(raw))
        } catch (e: SerializationException) {
            return CaptureResult.Failed("无法解析模型输出: ${e.message}")
        }

        if (!decision.keep) {
            return CaptureResult.Discarded(decision.reason ?: "判定为无沉淀价值的内容")
        }

        val note = KnowledgeNote(
            id = idProvider.newId(),
            createdAt = clock.nowMillis(),
            title = decision.title?.trim().orEmpty().ifEmpty { "未命名笔记" },
            summary = decision.summary?.trim().orEmpty(),
            keyPoints = decision.keyPoints.orEmpty().map { it.trim() }.filter { it.isNotEmpty() },
            category = decision.category?.trim().orEmpty().ifEmpty { "未分类" },
            tags = decision.tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() },
            sourceApp = request.sourceApp,
        )
        return CaptureResult.Kept(note)
    }

    companion object {
        /** 从可能带 ```json 围栏或前后噪声的文本里抽出 JSON 主体。 */
        internal fun extractJson(raw: String): String {
            val cleaned = if (raw.contains("```")) {
                raw.substringAfter("```")
                    .removePrefix("json")
                    .removePrefix("JSON")
                    .substringBeforeLast("```")
            } else {
                raw
            }
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            return if (start in 0 until end) cleaned.substring(start, end + 1) else cleaned.trim()
        }
    }
}
