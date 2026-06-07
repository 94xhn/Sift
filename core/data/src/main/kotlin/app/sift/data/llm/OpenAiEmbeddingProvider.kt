package app.sift.data.llm

import app.sift.domain.llm.EmbeddingProvider
import app.sift.domain.llm.LlmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/**
 * OpenAI 兼容的 /embeddings 实现（智谱 embedding-3 / OpenAI text-embedding-* / 等）。
 * 把文本转成向量，供 RAG 语义检索。
 */
class OpenAiEmbeddingProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : EmbeddingProvider {

    override suspend fun embed(texts: List<String>, config: LlmConfig): List<FloatArray> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val payload = buildJsonObject {
                put("model", config.model)
                putJsonArray("input") { texts.forEach { add(it) } }
            }
            val request = Request.Builder()
                .url(config.baseUrl.trimEnd('/') + "/embeddings")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${raw.take(200)}")
                }
                val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray ?: emptyList()
                data.map { el ->
                    val arr = el.jsonObject["embedding"]?.jsonArray ?: emptyList()
                    FloatArray(arr.size) { i -> arr[i].jsonPrimitive.float }
                }
            }
        }
}
