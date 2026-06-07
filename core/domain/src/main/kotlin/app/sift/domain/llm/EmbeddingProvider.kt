package app.sift.domain.llm

/**
 * 文本嵌入（embedding）抽象：把文本变成向量，供 RAG 语义检索用。
 * 复用 [LlmConfig]（baseUrl/apiKey + model 填 embedding 模型名）。实现放 :core:data。
 */
interface EmbeddingProvider {
    /** 批量嵌入；返回与输入等长的向量列表。失败可抛异常，由调用方降级处理。 */
    suspend fun embed(texts: List<String>, config: LlmConfig): List<FloatArray>
}
