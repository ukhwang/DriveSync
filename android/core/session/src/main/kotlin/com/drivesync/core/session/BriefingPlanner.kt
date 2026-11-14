package com.drivesync.core.session

import com.drivesync.core.model.BriefingSentence
import com.drivesync.core.model.BriefingTier
import kotlin.math.abs
import kotlin.math.ceil

enum class ScreenCard {
    /** 핵심 3줄 카드 */
    CORE_THREE,

    /** 핵심 3줄 + 우려사항 */
    CORE_THREE_WITH_CONCERNS,

    /** 상세 브리핑 카드 */
    DETAILED,
}

data class BriefingPlan(
    val tier: BriefingTier,
    val screenCard: ScreenCard,
    val voiceBudgetSeconds: Int,
    /** Null = no pre-arrival recap for this tier. */
    val preArrivalRecapSeconds: Int?,
)

/**
 * ETA-aware briefing sizing (기획서 1-② table) and mid-drive re-planning when ETA changes.
 */
object BriefingPlanner {

    const val SHORT_TIER_UPPER_MINUTES = 10
    const val LONG_TIER_LOWER_MINUTES = 25
    const val PRE_ARRIVAL_LEAD_MINUTES = 5

    /** Korean TTS speaks roughly this many characters per second at default rate. */
    private const val CHARS_PER_SECOND = 6.0

    /** ETA change that triggers a re-plan: 3 minutes or 20%, whichever is larger. */
    private const val SIGNIFICANT_CHANGE_MINUTES = 3
    private const val SIGNIFICANT_CHANGE_RATIO = 0.2

    fun plan(etaMinutes: Int): BriefingPlan = when {
        etaMinutes < SHORT_TIER_UPPER_MINUTES -> BriefingPlan(
            tier = BriefingTier.SHORT,
            screenCard = ScreenCard.CORE_THREE,
            voiceBudgetSeconds = 30,
            preArrivalRecapSeconds = null,
        )
        etaMinutes < LONG_TIER_LOWER_MINUTES -> BriefingPlan(
            tier = BriefingTier.MEDIUM,
            screenCard = ScreenCard.CORE_THREE_WITH_CONCERNS,
            voiceBudgetSeconds = 60,
            preArrivalRecapSeconds = 15,
        )
        else -> BriefingPlan(
            tier = BriefingTier.LONG,
            screenCard = ScreenCard.DETAILED,
            voiceBudgetSeconds = 120,
            preArrivalRecapSeconds = 15,
        )
    }

    fun isSignificantChange(previousEtaMinutes: Int, newEtaMinutes: Int): Boolean {
        val delta = abs(newEtaMinutes - previousEtaMinutes)
        val threshold = maxOf(SIGNIFICANT_CHANGE_MINUTES, ceil(previousEtaMinutes * SIGNIFICANT_CHANGE_RATIO).toInt())
        return delta >= threshold
    }

    fun estimateSeconds(sentence: BriefingSentence): Int =
        ceil(sentence.text.length / CHARS_PER_SECOND).toInt().coerceAtLeast(1)

    /**
     * Decides which of the not-yet-played sentences still fit.
     *
     * Available time = min(remaining voice budget, time left before the pre-arrival recap window).
     * If everything fits, the script is unchanged. Otherwise only core sentences are kept,
     * in order, until the time runs out (기획서: 시간이 모자라면 핵심 문장만 재생).
     */
    fun replan(
        remaining: List<BriefingSentence>,
        plan: BriefingPlan,
        elapsedVoiceSeconds: Int,
        remainingEtaMinutes: Int,
    ): List<BriefingSentence> {
        val budgetLeft = (plan.voiceBudgetSeconds - elapsedVoiceSeconds).coerceAtLeast(0)
        val recapLeadSeconds = if (plan.preArrivalRecapSeconds != null) PRE_ARRIVAL_LEAD_MINUTES * 60 else 0
        val driveLeft = (remainingEtaMinutes * 60 - recapLeadSeconds).coerceAtLeast(0)
        val available = minOf(budgetLeft, driveLeft)

        if (remaining.sumOf(::estimateSeconds) <= available) return remaining

        var used = 0
        return buildList {
            for (sentence in remaining) {
                if (!sentence.core) continue
                val cost = estimateSeconds(sentence)
                if (used + cost > available) break
                used += cost
                add(sentence)
            }
        }
    }
}
