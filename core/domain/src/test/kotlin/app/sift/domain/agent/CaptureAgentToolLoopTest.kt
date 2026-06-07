package app.sift.domain.agent

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.FinishReason
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.llm.LlmResponse
import app.sift.domain.llm.ToolCall
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

/** 记录被调用情况的假工具。 */
private class RecordingTool : Tool {
    var calledWith: String? = null
    override val spec = ToolSpec(
        name = "search_similar",
        description = "desc",
        parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string"}}}""",
    )

    override suspend fun execute(argumentsJson: String): String {
        calledWith = argumentsJson
        return """{"matches":[{"id":"n1","title":"旧笔记","summary":"s","category":"电子"}]}"""
    }
}

/** 按脚本依次返回预设响应的 provider。 */
private class ScriptedProvider(responses: List<LlmResponse>) : LLMProvider {
    override val id = "scripted"
    private val queue = ArrayDeque(responses)
    var calls = 0
    val toolsSeen = mutableListOf<List<ToolSpec>>()

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        config: LlmConfig,
    ): LlmResponse {
        calls++
        toolsSeen.add(tools)
        return queue.removeFirst()
    }
}

class CaptureAgentToolLoopTest {
    private val config = LlmConfig(baseUrl = "x", apiKey = "x", model = "x")
    private val request = CaptureRequest(imageBase64 = "AAAA")

    @Test
    fun callsToolThenProducesNote() = runTest {
        val tool = RecordingTool()
        val agent = CaptureAgent(
            clock = Clock { 1_700_000_000_000 },
            idProvider = IdProvider { "id" },
            tools = listOf(tool),
        )
        val provider = ScriptedProvider(
            listOf(
                LlmResponse(
                    toolCalls = listOf(
                        ToolCall("call1", "search_similar", """{"query":"滤波器"}"""),
                    ),
                    finishReason = FinishReason.TOOL_CALLS,
                ),
                LlmResponse(
                    text = """{"keep":true,"title":"RC 滤波器","summary":"f=1/(2πRC)","category":"电子"}""",
                ),
            ),
        )

        val result = agent.run(request, provider, config)

        val kept = assertIs<CaptureResult.Kept>(result)
        assertEquals("RC 滤波器", kept.note.title)
        // 工具被真正执行，且拿到模型给的参数
        assertEquals("""{"query":"滤波器"}""", tool.calledWith)
        // 循环跑了两轮（先调工具，再出最终答案）
        assertEquals(2, provider.calls)
        // 工具规格确实被传给了模型
        assertTrue(provider.toolsSeen.first().any { it.name == "search_similar" })
    }

    @Test
    fun failsIfModelKeepsCallingToolsBeyondMaxSteps() = runTest {
        val agent = CaptureAgent(
            clock = Clock { 1L },
            idProvider = IdProvider { "id" },
            tools = listOf(RecordingTool()),
            maxSteps = 2,
        )
        val loopForever = LlmResponse(
            toolCalls = listOf(ToolCall("c", "search_similar", "{}")),
            finishReason = FinishReason.TOOL_CALLS,
        )
        val provider = ScriptedProvider(listOf(loopForever, loopForever))

        val result = agent.run(request, provider, config)
        assertIs<CaptureResult.Failed>(result)
    }
}
