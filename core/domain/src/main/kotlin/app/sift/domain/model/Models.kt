package app.sift.domain.model

/** 一条沉淀下来的知识笔记。纯领域模型，不含任何持久化/平台注解。 */
data class KnowledgeNote(
    val id: String,
    val createdAt: Long,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val category: String,
    val tags: List<String>,
    val sourceApp: String? = null,
    /** 原图本地路径。默认用完即删保护隐私，仅在用户开启"保留原图"时写盘。 */
    val rawImagePath: String? = null,
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,
)

/** 知识图谱的一条边：两条笔记之间的关联。 */
data class NoteRelation(
    val id: String,
    val fromNoteId: String,
    val toNoteId: String,
    /** 为什么相关（由 agent 给出）。 */
    val reason: String,
)

/** 某一天的使用统计，"数据说话"仪表盘的原料。 */
data class DailyUsage(
    val date: String, // yyyy-MM-dd
    val scrollMillis: Long,
    val capturedCount: Int,
    val keptCount: Int,
) {
    /** 当天 keep 率（保留数 / 捕获数），供仪表盘参考。 */
    val keepRate: Double
        get() = if (capturedCount == 0) 0.0 else keptCount.toDouble() / capturedCount
}
