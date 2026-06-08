package app.sift.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.sift.data.db.DailyUsageEntity
import app.sift.data.db.NoteDao
import app.sift.data.db.UsageDao
import app.sift.data.db.toDomain
import app.sift.data.db.toEntity
import app.sift.domain.llm.EmbeddingProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.model.DailyUsage
import app.sift.domain.model.KnowledgeNote
import app.sift.domain.model.NoteRelation
import app.sift.domain.repository.NoteRepository
import app.sift.domain.repository.SettingsRepository
import app.sift.domain.repository.UsageRepository
import app.sift.domain.util.toBytes
import app.sift.domain.util.toFloatArray
import app.sift.domain.util.topKByCosine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val embeddings: EmbeddingProvider,
    private val settings: SettingsRepository,
) : NoteRepository {
    override fun observeNotes(): Flow<List<KnowledgeNote>> =
        dao.observeNotes().map { list -> list.map { it.toDomain() } }

    override suspend fun getNote(id: String): KnowledgeNote? = dao.getNote(id)?.toDomain()
    override suspend fun upsert(note: KnowledgeNote) = dao.upsert(note.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun markReviewed(id: String, at: Long) = dao.markReviewed(id, at)
    override suspend fun knownCategories(): List<String> = dao.categories()

    /** 先试语义检索（embedding + 余弦 Top-K）；任何原因失败就降级为关键词 LIKE。 */
    override suspend fun searchNotes(query: String, limit: Int): List<KnowledgeNote> {
        runCatching { semanticSearch(query, limit) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return dao.search(query.trim(), limit).map { it.toDomain() }
    }

    private suspend fun semanticSearch(query: String, limit: Int): List<KnowledgeNote> {
        val key = settings.getApiKey()
        if (key.isNullOrBlank()) return emptyList()
        val candidates = dao.allWithEmbedding()
        if (candidates.isEmpty()) return emptyList()
        val config = LlmConfig(settings.getBaseUrl(), key, settings.getEmbeddingModel())
        val queryVec = embeddings.embed(listOf(query), config).firstOrNull() ?: return emptyList()
        return topKByCosine(queryVec, candidates, limit) { it.embedding!!.toFloatArray() }
            .map { it.first.toDomain() }
    }

    override suspend fun updateEmbedding(id: String, vector: FloatArray) =
        dao.updateEmbedding(id, vector.toBytes())

    override suspend fun notesSince(epochMillis: Long): List<KnowledgeNote> =
        dao.notesSince(epochMillis).map { it.toDomain() }

    override suspend fun addRelation(relation: NoteRelation) = dao.addRelation(relation.toEntity())
    override suspend fun relationsOf(noteId: String): List<NoteRelation> =
        dao.relationsOf(noteId).map { it.toDomain() }
}

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao: UsageDao,
) : UsageRepository {
    override fun observeRecent(days: Int): Flow<List<DailyUsage>> =
        dao.observeRecent(days).map { list -> list.map { it.toDomain() } }

    override suspend fun recordCapture(date: String, kept: Boolean) {
        val cur = dao.get(date) ?: DailyUsageEntity(date, 0L, 0, 0)
        dao.upsert(
            cur.copy(
                capturedCount = cur.capturedCount + 1,
                keptCount = cur.keptCount + if (kept) 1 else 0,
            ),
        )
    }

    override suspend fun setScrollMillis(date: String, millis: Long) {
        val cur = dao.get(date) ?: DailyUsageEntity(date, 0L, 0, 0)
        dao.upsert(cur.copy(scrollMillis = millis))
    }
}

/**
 * 设置仓库。apiKey 走 [EncryptedSharedPreferences]（AES-256），从不进日志、不进系统备份。
 * baseUrl / model / providerId 同存加密区，省得再开一个文件。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sift_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val configured = MutableStateFlow(isConfigured())

    override fun observeConfigured(): Flow<Boolean> = configured.asStateFlow()

    override suspend fun getProviderId(): String =
        prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER

    override suspend fun getBaseUrl(): String =
        prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    override suspend fun getModel(): String =
        prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    override suspend fun getEmbeddingModel(): String =
        prefs.getString(KEY_EMBEDDING_MODEL, DEFAULT_EMBEDDING_MODEL) ?: DEFAULT_EMBEDDING_MODEL

    override suspend fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    override suspend fun save(providerId: String, baseUrl: String, model: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_PROVIDER, providerId)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_MODEL, model)
            .putString(KEY_API_KEY, apiKey)
            .apply()
        configured.value = isConfigured()
    }

    private fun isConfigured(): Boolean {
        val key = prefs.getString(KEY_API_KEY, null)
        val url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL)
        return !key.isNullOrBlank() && !url.isNullOrBlank() && !model.isNullOrBlank()
    }

    companion object {
        private const val KEY_PROVIDER = "provider_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_EMBEDDING_MODEL = "embedding_model"
        private const val KEY_API_KEY = "api_key"

        private const val DEFAULT_PROVIDER = "openai-compatible"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val DEFAULT_EMBEDDING_MODEL = "embedding-3" // 智谱免费 embedding
    }
}
