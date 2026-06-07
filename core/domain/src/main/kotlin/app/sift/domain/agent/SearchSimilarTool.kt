package app.sift.domain.agent

import app.sift.domain.llm.ToolSpec
import app.sift.domain.repository.NoteRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 让 agent 检索用户已有笔记的工具（RAG-lite：先用关键词检索，v0.2 后续可升级为向量检索）。
 * 模型用它判断当前内容是否与既有笔记重复、或找出相关笔记，从而做出更聪明的去留/归类决策。
 */
class SearchSimilarTool(
    private val notes: NoteRepository,
    private val json: Json = SiftJson,
) : Tool {

    override val spec = ToolSpec(
        name = "search_similar",
        description = "在用户已有的知识笔记里按关键词检索。用于判断当前内容是否与既有笔记重复、" +
            "或找出主题相关的旧笔记。返回命中笔记的 id/标题/摘要。",
        parametersJsonSchema = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "检索关键词，用主题词而非整句" }
              },
              "required": ["query"]
            }
        """.trimIndent(),
    )

    override suspend fun execute(argumentsJson: String): String {
        val query = runCatching {
            json.parseToJsonElement(argumentsJson).jsonObject["query"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty().trim()

        val hits = if (query.isBlank()) emptyList() else notes.searchNotes(query, limit = 5)

        return buildJsonObject {
            putJsonArray("matches") {
                hits.forEach { n ->
                    addJsonObject {
                        put("id", n.id)
                        put("title", n.title)
                        put("summary", n.summary)
                        put("category", n.category)
                    }
                }
            }
        }.toString()
    }
}
