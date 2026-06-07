package app.sift.domain.llm

/** 对话角色。 */
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/** 多模态图片内容（base64 编码）。 */
data class ImageData(
    val base64: String,
    val mimeType: String = "image/png",
)

/** 模型发起的一次工具调用（v0.2 多轮 tool-use 用）。 */
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * 一条对话消息。可同时携带文本与图片（用户消息附截图）；
 * assistant 消息可携带 [toolCalls]；tool 结果消息用 [toolCallId] 回指。
 */
data class ChatMessage(
    val role: Role,
    val text: String? = null,
    val images: List<ImageData> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
) {
    companion object {
        fun system(text: String) = ChatMessage(Role.SYSTEM, text)

        fun user(text: String, images: List<ImageData> = emptyList()) =
            ChatMessage(Role.USER, text, images)
    }
}

/** 工具规格，等价于一份 function 的 JSON Schema 描述，供模型决定调用。 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
)

enum class FinishReason { STOP, TOOL_CALLS, LENGTH, OTHER }

/** 一次 chat 调用的返回。text 与 toolCalls 取决于 [finishReason]。 */
data class LlmResponse(
    val text: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: FinishReason = FinishReason.STOP,
)

/** 运行时配置。baseUrl 区分各 OpenAI 兼容厂商（OpenAI / DeepSeek / Moonshot / Ollama / 中转）。 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.2,
    val maxTokens: Int = 1024,
)

/**
 * LLM 厂商抽象 —— BYO-Key 的工程核心：一份接口，多家实现（OpenAI 兼容 / Anthropic）。
 * 纯挂起函数，无 Android 依赖，实现放 :core:data。
 */
interface LLMProvider {
    val id: String

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec> = emptyList(),
        config: LlmConfig,
    ): LlmResponse
}
