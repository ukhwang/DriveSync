package com.drivesync.core.ai

import com.drivesync.core.model.Briefing
import com.drivesync.core.model.BriefingTier
import com.drivesync.core.model.Insight
import com.drivesync.core.model.SourcedText

/**
 * Client-side re-validation of server output (defence in depth; the server validates with Zod).
 * Rules mirror the JSON Schemas in the repo-level schemas directory. Returns a list of problems; empty = valid.
 */
object ContractValidator {

    const val SUMMARY_LINES = 3
    const val MAX_LIST_ITEMS = 5
    const val MAX_LINE_CHARS = 200
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun insight(insight: Insight): List<String> = buildList {
        if (insight.summary.size != SUMMARY_LINES) add("summary must have exactly $SUMMARY_LINES lines")
        if (insight.concerns.size > MAX_LIST_ITEMS) add("too many concerns")
        if (insight.expectedQuestions.size > MAX_LIST_ITEMS) add("too many expectedQuestions")
        (insight.summary + insight.concerns + insight.expectedQuestions).forEach { lineProblems(it)?.let(::add) }
    }

    fun briefing(briefing: Briefing, expectedTier: BriefingTier): List<String> = buildList {
        if (briefing.tier != expectedTier) add("tier ${briefing.tier} != requested $expectedTier")
        if (briefing.screenLines.isEmpty()) add("screenLines empty")
        if (briefing.voiceScript.none { it.core }) add("voiceScript needs at least one core sentence")
        if (expectedTier == BriefingTier.SHORT && briefing.preArrivalRecap != null) add("SHORT tier has no recap")
        if (expectedTier != BriefingTier.SHORT && briefing.preArrivalRecap.isNullOrBlank()) add("recap missing")
        briefing.voiceScript.forEach { if (it.text.isBlank()) add("blank sentence") }
    }

    fun extractedActions(response: ExtractActionsResponse): List<String> = buildList {
        val ids = response.actions.map { it.id }
        if (ids.size != ids.toSet().size) add("duplicate action ids")
        response.actions.forEach { action ->
            if (!EMAIL.matches(action.draft.to)) add("invalid recipient for ${action.id}")
            if (action.draft.subject.isBlank()) add("empty subject for ${action.id}")
            if (action.draft.body.isBlank()) add("empty body for ${action.id}")
        }
    }

    private fun lineProblems(line: SourcedText): String? = when {
        line.text.isBlank() -> "blank line"
        line.text.length > MAX_LINE_CHARS -> "line exceeds $MAX_LINE_CHARS chars"
        else -> null
    }
}
