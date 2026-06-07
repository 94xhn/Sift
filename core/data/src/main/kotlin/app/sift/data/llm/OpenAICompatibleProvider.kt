package app.sift.data.llm

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.FinishReason
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.llm.LlmResponse
import app.sift.domain.llm.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/**
 * 覆盖所有 OpenAI 兼容协议厂商的 Provider —— 一份代码靠 [LlmConfig.baseUrl] 区分
 * OpenAI / DeepSeek / Moonshot / 通义 / 本地 Ollama / 各类中转。
 *
 * 请求体直接用 JsonObject 构造而非固定 DTO，因为 OpenAI 的 message.content 在多模态时
 * 是异构数组（text + image_url），rigid DTO 反而别扭。
 */
class OpenAICompatibleProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : LLMProvider {

    override val id: String = "openai-compatible"

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        config: LlmConfig,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val payload = buildRequest(messages, config)
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${raw.take(300)}")
            }
            parseResponse(raw)
        }
    }

    private fun buildRequest(messages: List<ChatMessage>, config: LlmConfig): JsonObject =
        buildJsonObject {
            put("model", config.model)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            putJsonArray("messages") {
                messages.forEach { add(encodeMessage(it)) }
            }
        }

    private fun encodeMessage(msg: ChatMessage): JsonObject = buildJsonObject {
        put("role", msg.role.name.lowercase())
        if (msg.images.isEmpty()) {
            put("content", msg.text.orEmpty())
        } else {
            putJsonArray("content") {
                if (!msg.text.isNullOrEmpty()) {
                    addJsonObject {
                        put("type", "text")
                        put("text", msg.text)
                    }
                }
                msg.images.forEach { img ->
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:${img.mimeType};base64,${img.base64}")
                        }
                    }
                }
            }
        }
    }

    private fun parseResponse(raw: String): LlmResponse {
        val root = json.parseToJsonElement(raw).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull
        val finish = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
        return LlmResponse(
            text = content,
            finishReason = when (finish) {
                "stop" -> FinishReason.STOP
                "length" -> FinishReason.LENGTH
                "tool_calls" -> FinishReason.TOOL_CALLS
                else -> FinishReason.OTHER
            },
        )
    }
}
