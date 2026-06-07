package app.sift.domain.dashboard

import app.sift.domain.model.DailyUsage

/**
 * "诚实镜子"的核心：根据今天的真实数据生成一句话。
 *
 * 设计原则（见 docs/PHILOSOPHY.md）：不制造内疚、不鞭策。该劝休息时就劝休息——
 * 一个敢于在恰当时刻让你放下它的产品，反而赢得最深的信任。纯函数，便于单测。
 */
object HonestNudge {

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val LONG_SESSION_MINUTES = 120 // 刷够 2 小时算"久"

    fun forToday(usage: DailyUsage): String {
        val minutes = usage.scrollMillis / MILLIS_PER_MINUTE
        return when {
            usage.scrollMillis == 0L && usage.capturedCount == 0 ->
                "今天还没开始。刷到好东西，点悬浮球留下它。"

            minutes >= LONG_SESSION_MINUTES && usage.keptCount == 0 ->
                "今天刷了 $minutes 分钟，沉淀了 0 条。或许今天不必沉淀什么——直接休息也很好。"

            usage.keptCount > 0 ->
                "今天刷了 $minutes 分钟，沉淀了 ${usage.keptCount} 条。至少，捞到了点东西。"

            else ->
                "今天刷了 $minutes 分钟。"
        }
    }
}
