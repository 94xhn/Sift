package app.sift.domain.agent

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.llm.LlmResponse
import app.sift.domain.llm.ToolSpec
import app.sift.domain.model.KnowledgeNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TextProvider(private val reply: String?) : LLMProvider {
    override val id = "text"
    var lastUserText: String? = null
    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        config: LlmConfig,
    ): LlmResponse {
        lastUserText = messages.lastOrNull()?.text
        return LlmResponse(text = reply)
    }
}

class WeeklyReportAgentTest {
    private val config = LlmConfig(baseUrl = "x", apiKey = "x", model = "x")
    private val agent = WeeklyReportAgent()

    private fun note(title: String, category: String = "电子") = KnowledgeNote(
        id = title, createdAt = 0L, title = title, summary = "s", keyPoints = listOf("k"),
        category = category, tags = emptyList(),
    )

    @Test
    fun emptyNotesGivesFriendlyMessageWithoutCallingModel() = runTest {
        val provider = TextProvider("不应被调用")
        val out = agent.generate(emptyList(), provider, config)
        assertTrue(out.contains("还没有沉淀"))
        assertEquals(null, provider.lastUserText) // 没调用模型
    }

    @Test
    fun summarizesNotesIntoReport() = runTest {
        val provider = TextProvider("# 本周周报\n- 学了 RC 滤波器")
        val out = agent.generate(listOf(note("RC 滤波器"), note("PID 控制")), provider, config)
        assertTrue(out.contains("周报"))
        // 笔记内容被带进了 user prompt
        assertTrue(provider.lastUserText!!.contains("RC 滤波器"))
        assertTrue(provider.lastUserText!!.contains("本周沉淀的 2 条"))
    }

    @Test
    fun providerErrorIsReportedNotThrown() = runTest {
        val failing = object : LLMProvider {
            override val id = "f"
            override suspend fun chat(m: List<ChatMessage>, t: List<ToolSpec>, c: LlmConfig): LlmResponse =
                throw RuntimeException("boom")
        }
        val out = agent.generate(listOf(note("x")), failing, config)
        assertTrue(out.contains("生成失败"))
    }
}
