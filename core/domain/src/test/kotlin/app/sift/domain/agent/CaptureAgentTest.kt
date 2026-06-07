package app.sift.domain.agent

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.llm.LlmResponse
import app.sift.domain.llm.ToolSpec
import app.sift.domain.model.CaptureRequest
import app.sift.domain.model.CaptureResult
import app.sift.domain.util.Clock
import app.sift.domain.util.IdProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeProvider(private val reply: String?) : LLMProvider {
    override val id = "fake"
    var lastMessages: List<ChatMessage> = emptyList()

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        config: LlmConfig,
    ): LlmResponse {
        lastMessages = messages
        return LlmResponse(text = reply)
    }
}

private class ThrowingProvider : LLMProvider {
    override val id = "throwing"
    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        config: LlmConfig,
    ): LlmResponse = throw RuntimeException("401 Unauthorized")
}

class CaptureAgentTest {
    private val config = LlmConfig(baseUrl = "x", apiKey = "x", model = "x")
    private val agent = CaptureAgent(
        clock = Clock { 1_700_000_000_000 },
        idProvider = IdProvider { "fixed-id" },
    )
    private val request = CaptureRequest(imageBase64 = "AAAA", sourceApp = "抖音")

    @Test
    fun keptWhenModelSaysKeep() = runTest {
        val provider = FakeProvider(
            """
            {"keep":true,"title":"RC 滤波器截止频率","summary":"f=1/(2πRC)",
             "key_points":["截止频率公式","一阶低通"],"category":"电子","tags":["滤波器","公式"]}
            """.trimIndent(),
        )
        val result = agent.run(request, provider, config)
        val kept = assertIs<CaptureResult.Kept>(result)
        assertEquals("fixed-id", kept.note.id)
        assertEquals(1_700_000_000_000, kept.note.createdAt)
        assertEquals("RC 滤波器截止频率", kept.note.title)
        assertEquals(2, kept.note.keyPoints.size)
        assertEquals("电子", kept.note.category)
        assertEquals("抖音", kept.note.sourceApp)
    }

    @Test
    fun discardedWhenModelRejects() = runTest {
        val provider = FakeProvider("""{"keep":false,"reason":"纯娱乐段子"}""")
        val result = agent.run(request, provider, config)
        val discarded = assertIs<CaptureResult.Discarded>(result)
        assertEquals("纯娱乐段子", discarded.reason)
    }

    @Test
    fun handlesCodeFenceWrappedJson() = runTest {
        val provider = FakeProvider("```json\n{\"keep\":false,\"reason\":\"广告\"}\n```")
        val result = agent.run(request, provider, config)
        assertIs<CaptureResult.Discarded>(result)
    }

    @Test
    fun failedWhenProviderThrows() = runTest {
        val result = agent.run(request, ThrowingProvider(), config)
        val failed = assertIs<CaptureResult.Failed>(result)
        assertTrue(failed.error.contains("401"))
    }

    @Test
    fun failedWhenUnparseable() = runTest {
        val provider = FakeProvider("我觉得这张图挺好的，但我不会输出 JSON")
        val result = agent.run(request, provider, config)
        assertIs<CaptureResult.Failed>(result)
    }

    @Test
    fun failedWhenNullText() = runTest {
        val result = agent.run(request, FakeProvider(null), config)
        assertIs<CaptureResult.Failed>(result)
    }

    @Test
    fun emptyTitleFallsBackToPlaceholder() = runTest {
        val provider = FakeProvider("""{"keep":true,"title":"  ","summary":"x"}""")
        val kept = assertIs<CaptureResult.Kept>(agent.run(request, provider, config))
        assertEquals("未命名笔记", kept.note.title)
        assertEquals("未分类", kept.note.category)
    }

    @Test
    fun passesScreenshotAndSystemPromptToProvider() = runTest {
        val provider = FakeProvider("""{"keep":false,"reason":"x"}""")
        agent.run(request, provider, config)
        assertEquals(2, provider.lastMessages.size)
        assertTrue(provider.lastMessages.first().text!!.contains("Sift"))
        assertEquals(1, provider.lastMessages[1].images.size)
    }
}
