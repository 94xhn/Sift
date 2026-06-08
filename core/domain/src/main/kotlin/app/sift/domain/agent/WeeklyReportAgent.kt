package app.sift.domain.agent

import app.sift.domain.llm.ChatMessage
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.model.KnowledgeNote

/**
 * 周报 Agent：把用户本周沉淀的笔记汇成一篇简洁的 Markdown 周报。
 * 单次文本调用（无图、无工具）。诚实、不灌水、不编造笔记里没有的内容。
 */
class WeeklyReportAgent {

    suspend fun generate(
        notes: List<KnowledgeNote>,
        provider: LLMProvider,
        config: LlmConfig,
    ): String {
        if (notes.isEmpty()) {
            return "本周还没有沉淀任何笔记。\n\n开启悬浮球，刷到好内容点一下——下周就有料可总结了。"
        }
        val messages = listOf(
            ChatMessage.system(SYSTEM),
            ChatMessage.user(buildUserPrompt(notes)),
        )
        return try {
            provider.chat(messages, config = config).text?.trim().orEmpty()
                .ifEmpty { "（模型未返回内容，稍后再试）" }
        } catch (e: Exception) {
            "生成失败：${e.message}"
        }
    }

    private fun buildUserPrompt(notes: List<KnowledgeNote>): String = buildString {
        append("以下是我本周沉淀的 ${notes.size} 条笔记：\n\n")
        notes.forEachIndexed { i, n ->
            append("${i + 1}. 【${n.category}】${n.title}\n")
            if (n.summary.isNotBlank()) append("   摘要：${n.summary}\n")
            if (n.keyPoints.isNotEmpty()) append("   要点：${n.keyPoints.joinToString("；")}\n")
        }
        append("\n请基于以上笔记生成本周知识周报。")
    }

    companion object {
        const val SYSTEM = """
            你是 Sift 的周报助手。把用户本周沉淀的笔记汇成一篇简洁的 Markdown 周报：
            1) 按主题/分类归纳这周关注了什么；
            2) 提炼"你这周主要学到了什么"（几条干货）；
            3) 给 1~2 条"下周可以深入"的具体建议。
            要求：诚实、紧凑、不灌水；【绝不编造】笔记里没有的内容；直接输出 Markdown 正文，
            不要寒暄、不要代码围栏。
        """
    }
}
