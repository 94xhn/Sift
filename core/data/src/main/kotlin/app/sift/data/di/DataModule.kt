package app.sift.data.di

import android.content.Context
import androidx.room.Room
import app.sift.data.db.NoteDao
import app.sift.data.db.SiftDatabase
import app.sift.data.db.UsageDao
import app.sift.data.llm.OpenAICompatibleProvider
import app.sift.data.llm.OpenAiEmbeddingProvider
import app.sift.domain.llm.EmbeddingProvider
import app.sift.data.repository.NoteRepositoryImpl
import app.sift.data.repository.SettingsRepositoryImpl
import app.sift.data.repository.UsageRepositoryImpl
import app.sift.domain.agent.CaptureAgent
import app.sift.domain.agent.SearchSimilarTool
import app.sift.domain.agent.SiftJson
import app.sift.domain.agent.Tool
import app.sift.domain.llm.LLMProvider
import app.sift.domain.repository.NoteRepository
import app.sift.domain.repository.SettingsRepository
import app.sift.domain.repository.UsageRepository
import app.sift.domain.util.Clock
import app.sift.domain.util.IdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideJson(): Json = SiftJson

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // 视觉模型处理一张图常要十几秒，默认 10s 会超时
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SiftDatabase =
        Room.databaseBuilder(context, SiftDatabase::class.java, "sift.db")
            // 早期开发：schema 变更直接重建（笔记是可丢的测试数据）。上线前再写正式 Migration。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNoteDao(db: SiftDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideUsageDao(db: SiftDatabase): UsageDao = db.usageDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideIdProvider(): IdProvider = IdProvider { UUID.randomUUID().toString() }

    /** agent 注册的工具集。当前：search_similar（查重/关联，RAG-lite）。 */
    @Provides
    @Singleton
    fun provideAgentTools(noteRepository: NoteRepository, json: Json): List<Tool> =
        listOf(SearchSimilarTool(noteRepository, json))

    @Provides
    @Singleton
    fun provideCaptureAgent(
        clock: Clock,
        idProvider: IdProvider,
        tools: List<Tool>,
        json: Json,
    ): CaptureAgent = CaptureAgent(clock = clock, idProvider = idProvider, tools = tools, json = json)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLlmProvider(impl: OpenAICompatibleProvider): LLMProvider

    @Binds
    @Singleton
    abstract fun bindEmbeddingProvider(impl: OpenAiEmbeddingProvider): EmbeddingProvider
}
