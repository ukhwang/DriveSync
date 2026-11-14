package com.drivesync.core.session

import com.drivesync.core.model.BriefingSentence
import com.drivesync.core.model.BriefingTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BriefingPlannerTest {

    @Test
    fun `ETA tiers follow the proposal table`() {
        assertEquals(BriefingTier.SHORT, BriefingPlanner.plan(9).tier)
        assertEquals(30, BriefingPlanner.plan(9).voiceBudgetSeconds)
        assertNull(BriefingPlanner.plan(9).preArrivalRecapSeconds)

        assertEquals(BriefingTier.MEDIUM, BriefingPlanner.plan(10).tier)
        assertEquals(BriefingTier.MEDIUM, BriefingPlanner.plan(24).tier)
        assertEquals(60, BriefingPlanner.plan(24).voiceBudgetSeconds)
        assertEquals(ScreenCard.CORE_THREE_WITH_CONCERNS, BriefingPlanner.plan(24).screenCard)

        assertEquals(BriefingTier.LONG, BriefingPlanner.plan(25).tier)
        assertEquals(120, BriefingPlanner.plan(40).voiceBudgetSeconds)
        assertEquals(15, BriefingPlanner.plan(40).preArrivalRecapSeconds)
    }

    @Test
    fun `significant ETA change uses 3 minutes or 20 percent`() {
        assertFalse(BriefingPlanner.isSignificantChange(10, 12))
        assertTrue(BriefingPlanner.isSignificantChange(10, 13))
        assertFalse(BriefingPlanner.isSignificantChange(30, 35))
        assertTrue(BriefingPlanner.isSignificantChange(30, 36))
    }

    @Test
    fun `script is untouched when it fits`() {
        val script = listOf(BriefingSentence("짧은 문장입니다", core = false))
        assertEquals(script, BriefingPlanner.replan(script, BriefingPlanner.plan(30), 0, 30))
    }

    @Test
    fun `only core sentences remain when ETA shrinks`() {
        val core1 = BriefingSentence("오늘 목표는 2차 견적 합의입니다.", core = true) // 19 chars ~ 4s
        val filler = BriefingSentence("지난 방문에서는 담당 부장님이 납기 일정에 대해 상세히 질문하셨고 경쟁사 견적도 언급하셨습니다.".repeat(8), core = false)
        val core2 = BriefingSentence("꼭 확인할 질문은 예산 확정 시점입니다.", core = true)
        val plan = BriefingPlanner.plan(18) // MEDIUM, 60s budget, recap 5 min before arrival

        // ETA collapsed to 6 min: only 60s of drive left before the 5-min recap window,
        // and the long filler sentence alone exceeds that.
        val result = BriefingPlanner.replan(listOf(core1, filler, core2), plan, elapsedVoiceSeconds = 0, remainingEtaMinutes = 6)
        assertEquals(listOf(core1, core2), result)
    }

    @Test
    fun `nothing plays once the recap window is reached`() {
        val script = listOf(BriefingSentence("핵심 문장", core = true))
        assertEquals(emptyList(), BriefingPlanner.replan(script, BriefingPlanner.plan(18), 0, remainingEtaMinutes = 5))
    }
}
