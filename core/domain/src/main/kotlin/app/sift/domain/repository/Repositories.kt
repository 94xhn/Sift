package app.sift.domain.repository

import app.sift.domain.model.DailyUsage
import app.sift.domain.model.KnowledgeNote
import app.sift.domain.model.NoteRelation
import kotlinx.coroutines.flow.Flow

/** 知识笔记仓库。实现放 :core:data（Room）。 */
interface NoteRepository {
    fun observeNotes(): Flow<List<KnowledgeNote>>
    suspend fun getNote(id: String): KnowledgeNote?
    suspend fun upsert(note: KnowledgeNote)
    suspend fun delete(id: String)
    suspend fun markReviewed(id: String, at: Long)

    /** 已有分类去重列表，喂给 agent 做归类一致性提示。 */
    suspend fun knownCategories(): List<String>

    /** 检索已有笔记（供 agent 的 search_similar 工具做查重/关联）。实现优先语义检索，失败降级关键词。 */
    suspend fun searchNotes(query: String, limit: Int = 5): List<KnowledgeNote>

    /** 写入某条笔记的 embedding 向量（RAG 语义检索用）。 */
    suspend fun updateEmbedding(id: String, vector: FloatArray)

    /** 取自某时间点(epoch ms)以来创建的笔记，供周报 agent 汇总。 */
    suspend fun notesSince(epochMillis: Long): List<KnowledgeNote>

    // 知识图谱（v0.2 起用）
    suspend fun addRelation(relation: NoteRelation)
    suspend fun relationsOf(noteId: String): List<NoteRelation>

    /** 全部关联边（供知识图谱可视化）。 */
    suspend fun allRelations(): List<NoteRelation>
}

/** 使用统计仓库，"数据说话"仪表盘的数据源。 */
interface UsageRepository {
    fun observeRecent(days: Int): Flow<List<DailyUsage>>
    suspend fun recordCapture(date: String, kept: Boolean)
    suspend fun setScrollMillis(date: String, millis: Long)
}

/** LLM 连接设置。实现放 :core:data，apiKey 走 EncryptedSharedPreferences。 */
interface SettingsRepository {
    /** 是否已配齐可用配置（有 baseUrl + model + key）。 */
    fun observeConfigured(): Flow<Boolean>
    suspend fun getProviderId(): String
    suspend fun getBaseUrl(): String
    suspend fun getModel(): String
    /** 嵌入模型名（语义检索用）。默认智谱 embedding-3。 */
    suspend fun getEmbeddingModel(): String
    suspend fun getApiKey(): String?
    suspend fun save(
        providerId: String,
        baseUrl: String,
        model: String,
        embeddingModel: String,
        apiKey: String,
    )
}
