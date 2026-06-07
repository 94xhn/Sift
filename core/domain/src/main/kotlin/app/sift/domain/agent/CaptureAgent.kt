package app.sift.domain.agent

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.ImageData
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.llm.Role
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
 * Sift 的核心 Agent。给定一张截图，决定是否值得沉淀，若值得则产出一条结构化 [KnowledgeNote]。
 *
 * v0.2：**多轮 tool-use 循环**。注册一组 [tools]（如 search_similar）让模型自主调度——
 * 模型可先调用工具查用户已有笔记（查重/关联），看到结果后再产出最终结构化 JSON。
 * 若模型不调用任何工具（或没有工具），循环首轮就拿到最终 JSON，等价于单次调用——向后兼容。
 */
class CaptureAgent(
    private val clock: Clock,
    private val idProvider: IdProvider,
    private val tools: List<Tool> = emptyList(),
    private val json: Json = SiftJson,
    private val maxSteps: Int = 4,
) {
    suspend fun run(
        request: CaptureRequest,
        provider: LLMProvider,
        config: LlmConfig,
    ): CaptureResult {
        val toolSpecs = tools.map { it.spec }
        val toolByName = tools.associateBy { it.spec.name }

        val messages = mutableListOf(
            ChatMessage.system(CapturePrompt.system(request.knownCategories, hasTools = tools.isNotEmpty())),
            ChatMessage.user(
                text = CapturePrompt.userInstruction(request.sourceApp),
                images = listOf(ImageData(request.imageBase64, request.mimeType)),
            ),
        )

        repeat(maxSteps) {
            val response = try {
                provider.chat(messages, toolSpecs, config)
            } catch (e: Exception) {
                return CaptureResult.Failed(e.message ?: "LLM 调用失败")
            }

            if (response.toolCalls.isNotEmpty()) {
                // 模型要调工具：把它的 assistant 消息（带 tool_calls）和每个工具结果塞回对话，继续循环
                messages.add(
                    ChatMessage(role = Role.ASSISTANT, text = response.text, toolCalls = response.toolCalls),
                )
                for (call in response.toolCalls) {
                    val result = runCatching {
                        toolByName[call.name]?.execute(call.argumentsJson) ?: "未知工具: ${call.name}"
                    }.getOrElse { "工具执行失败: ${it.message}" }
                    messages.add(ChatMessage(role = Role.TOOL, text = result, toolCallId = call.id))
                }
                return@repeat // 进入下一轮
            }

            // 没有工具调用 → 这是最终回答
            val raw = response.text ?: return CaptureResult.Failed("模型未返回文本")
            return finalize(raw, request)
        }

        return CaptureResult.Failed("超过最大推理步数仍未给出结论")
    }

    private fun finalize(raw: String, request: CaptureRequest): CaptureResult {
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
