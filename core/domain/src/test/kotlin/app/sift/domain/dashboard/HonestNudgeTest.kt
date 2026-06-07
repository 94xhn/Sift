package app.sift.domain.dashboard

import app.sift.domain.model.DailyUsage
import kotlin.test.Test
import kotlin.test.assertTrue

class HonestNudgeTest {

    private fun usage(minutes: Long = 0, captured: Int = 0, kept: Int = 0) =
        DailyUsage(
            date = "2026-06-07",
            scrollMillis = minutes * 60_000L,
            capturedCount = captured,
            keptCount = kept,
        )

    @Test
    fun nothingYet() {
        assertTrue(HonestNudge.forToday(usage()).contains("还没开始"))
    }

    @Test
    fun longSessionWithNothingKeptSuggestsRest() {
        val msg = HonestNudge.forToday(usage(minutes = 180, captured = 5, kept = 0))
        assertTrue(msg.contains("休息"), "久刷无沉淀应温和劝休息，而非制造内疚")
        assertTrue(msg.contains("180"))
    }

    @Test
    fun keptSomethingIsAcknowledgedNotPraisedExcessively() {
        val msg = HonestNudge.forToday(usage(minutes = 30, captured = 3, kept = 2))
        assertTrue(msg.contains("2"))
        assertTrue(msg.contains("捞到"))
    }

    @Test
    fun shortBrowsingNoKeepJustStatesFact() {
        val msg = HonestNudge.forToday(usage(minutes = 20, captured = 1, kept = 0))
        assertTrue(msg.contains("20"))
        assertTrue(!msg.contains("休息")) // 没到 2 小时，不劝退
    }
}
