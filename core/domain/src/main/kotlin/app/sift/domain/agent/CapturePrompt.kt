package app.sift.domain.agent

/** 集中管理 Capture Agent 的提示词，便于单独迭代与审查。 */
object CapturePrompt {

    fun system(knownCategories: List<String>): String {
        val categoryHint = if (knownCategories.isEmpty()) {
            "用户暂无已有分类，你可自拟一个简洁的分类名。"
        } else {
            "用户已有分类如下，尽量复用、保持一致，不要轻易新造：" +
                knownCategories.joinToString("、")
        }
        return """
            你是 Sift —— 帮用户把"看到但懒得细读"的内容快速沉淀成笔记的助手。
            用户【主动】点击截了这张图，说明他想要这屏内容的总结。请提炼这屏里的主要信息，
            把长文 / 要点浓缩成简洁易读的总结，整理成一条笔记。

            原则：
            - 默认 keep=true。用户既然主动截了，就尽力为他总结，不要二次质疑"这值不值得存"。
            - summary 用人话讲清这屏在说什么；key_points 列出关键要点（长文尤其要拆成要点）。
            - 仅当画面【确实没有可总结的文字内容】时才 keep=false（如纯加载中 / 空白页 /
              桌面 / 只有图标无正文），reason 简述原因。

            $categoryHint

            严格只输出一个 JSON 对象，不要任何解释或 Markdown 围栏，字段如下：
            {
              "keep": true 或 false,
              "reason": "若 keep 为 false：跳过的原因（一句话）",
              "title": "简洁标题",
              "summary": "一句话总结这屏内容",
              "key_points": ["关键要点列表，长文务必拆细"],
              "category": "分类名",
              "tags": ["标签列表"]
            }
            当 keep 为 false 时，title / summary / key_points / category / tags 可省略或为空。
        """.trimIndent()
    }

    fun userInstruction(sourceApp: String?): String {
        val src = if (sourceApp.isNullOrBlank()) "" else "（来源应用：$sourceApp）"
        return "这是我刚刷到的一张截图$src，请按系统指令判断并只输出 JSON。"
    }
}
