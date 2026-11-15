package com.drivesync.core.ai

import com.drivesync.core.action.ActionExecutor
import com.drivesync.core.action.ExecutionRequest
import com.drivesync.core.action.ExecutionResult
import com.drivesync.core.model.ActionType
import com.drivesync.core.model.Briefing
import com.drivesync.core.model.BriefingSentence
import com.drivesync.core.model.BriefingTier
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.model.Insight
import com.drivesync.core.model.ItemSource
import com.drivesync.core.model.SourcedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline demo mode (no server configured).
 *
 * The release APK handed to judges has no Supabase URL, so every Edge Function call would end in
 * "오프라인". In that case the app binds these two classes instead, so the full 5-stage loop
 * (브리핑 → 메모 → 치트시트 → 디브리핑 → 승인·실행/재시도) can be walked through on an emulator.
 *
 * Rules kept identical to the server path:
 * - Every response is built from fictional seed data and re-validated by [ContractValidator].
 * - Extracted actions start as DRAFT; nothing is executed without approval (ActionEngine enforces it).
 * - Nothing leaves the device. The executor never sends mail and says so in its log.
 */
data class DemoVisitContext(
    val companyName: String,
    val contactName: String,
    val contactEmail: String,
    val historySummary: String,
    val purpose: String,
)

class OfflineDemoAiGateway(
    private val contextFor: suspend (visitId: String) -> DemoVisitContext?,
) : AiGateway {

    override suspend fun briefing(request: BriefingRequest): EdgeResult<Briefing> {
        val ctx = contextFor(request.visitId) ?: return notFound(request.visitId)
        return EdgeResult.Success(OfflineDemoContent.briefing(ctx, request.tier))
            .validate { ContractValidator.briefing(it, request.tier) }
    }

    override suspend fun insight(request: InsightRequest): EdgeResult<Insight> {
        val ctx = contextFor(request.visitId) ?: return notFound(request.visitId)
        return EdgeResult.Success(OfflineDemoContent.insight(ctx, request.travelNotes))
            .validate(ContractValidator::insight)
    }

    override suspend fun extractActions(
        request: ExtractActionsRequest,
        customerId: String,
    ): EdgeResult<List<FollowUpAction>> {
        val ctx = contextFor(request.visitId) ?: return notFound(request.visitId)
        val result = EdgeResult.Success(OfflineDemoContent.extract(ctx, request))
            .validate(ContractValidator::extractedActions)
        if (result !is EdgeResult.Success) return result.mapFailure()
        return EdgeResult.Success(result.value.toFollowUps(request.sessionId, customerId))
    }

    /** No external transfer happens offline, so there is nothing to consent to; recorded as granted. */
    override suspend fun recordConsent(granted: Boolean): EdgeResult<Unit> = EdgeResult.Success(Unit)

    private fun notFound(visitId: String): EdgeResult<Nothing> =
        EdgeResult.Server(404, "visit_not_found", "방문 정보를 찾을 수 없습니다($visitId)")
}

/**
 * Simulated executor for the offline demo.
 *
 * - The first [failFirstAttempts] requests fail as if the car were in an underground car park, so the
 *   Failed → Retry → (재승인) → Executed path can be shown on the emulator.
 * - Executed idempotency keys are remembered: the same key is answered with AlreadyExecuted, never re-sent.
 * - No e-mail is sent. Every log line says "시뮬레이션".
 */
class SimulatedActionExecutor(
    private val failFirstAttempts: Int = 1,
    private val latencyMillis: Long = 600,
) : ActionExecutor {

    private val mutex = Mutex()
    private var attempts = 0
    private val executedKeys = mutableSetOf<String>()

    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        if (latencyMillis > 0) delay(latencyMillis)
        return mutex.withLock {
            attempts += 1
            when {
                request.idempotencyKey in executedKeys ->
                    ExecutionResult.AlreadyExecuted("시뮬레이션 · 같은 실행 키(${request.idempotencyKey})는 다시 발송하지 않음")
                attempts <= failFirstAttempts ->
                    ExecutionResult.Failed("통신 음영(지하주차장 시뮬레이션) · 다음 정차 때 재시도하세요")
                else -> {
                    executedKeys += request.idempotencyKey
                    ExecutionResult.Executed(
                        "시뮬레이션 발송 완료 · 실제 메일은 보내지 않았습니다 (수신 ${request.draft.to}, 키 ${request.idempotencyKey})",
                    )
                }
            }
        }
    }
}

/** Deterministic, schema-valid content. Pure functions so they are unit-tested directly. */
internal object OfflineDemoContent {

    const val INTERNAL_RECIPIENT = "sales-team@drivesync.example"
    private const val NOTE_CHARS = 120
    private const val BODY_DEBRIEF_CHARS = 600

    fun briefing(ctx: DemoVisitContext, tier: BriefingTier): Briefing {
        val goal = BriefingSentence("${ctx.companyName} 방문입니다. 오늘 목표는 ${ctx.purpose}입니다.", core = true)
        val mustAsk = BriefingSentence("꼭 확인할 질문은 다음 결정 일정과 결재 담당자입니다.", core = true)
        val history = BriefingSentence("지난 방문 기록입니다. ${clip(ctx.historySummary, NOTE_CHARS)}", core = false)
        val questions = BriefingSentence(
            "확인 질문 세 가지. 예산 편성 시점, 경쟁사 대비 우려사항, 다음 미팅 참석자입니다.",
            core = false,
        )
        val openItems = BriefingSentence(
            "미완료 후속 업무가 있다면 오늘 미팅 끝에 일정과 담당자를 확정하세요.",
            core = false,
        )
        val script = when (tier) {
            BriefingTier.SHORT -> listOf(goal, mustAsk)
            BriefingTier.MEDIUM -> listOf(goal, history, questions, mustAsk)
            BriefingTier.LONG -> listOf(goal, history, openItems, questions, mustAsk)
        }
        return Briefing(
            tier = tier,
            screenLines = listOf(
                "${ctx.companyName} · ${ctx.contactName}",
                "방문 목적: ${ctx.purpose}",
                "지난 방문: ${clip(ctx.historySummary, NOTE_CHARS)}",
            ),
            voiceScript = script,
            preArrivalRecap = if (tier == BriefingTier.SHORT) null else "곧 도착합니다. 오늘 목표는 ${ctx.purpose}입니다.",
        )
    }

    fun insight(ctx: DemoVisitContext, travelNotes: List<String>): Insight {
        val notes = travelNotes.map(String::trim).filter(String::isNotEmpty)
        val goal = SourcedText(clip("오늘 목표: ${ctx.purpose}", NOTE_CHARS), ItemSource.USER)
        val history = SourcedText(clip("지난 방문: ${ctx.historySummary}", NOTE_CHARS), ItemSource.AI)
        val latestNote = notes.lastOrNull()?.let { SourcedText(clip(it, NOTE_CHARS), ItemSource.USER) }
        val closing = SourcedText("미팅 끝에 다음 단계(견적·데모·계약) 일정과 담당자를 확정합니다", ItemSource.AI)
        val summary = listOf(goal, latestNote ?: history, if (latestNote != null) history else closing)

        val concerns = buildList {
            add(SourcedText("지난 방문에서 남은 이슈가 오늘 다시 제기될 수 있습니다", ItemSource.AI))
            notes.dropLast(1).takeLast(ContractValidator.MAX_LIST_ITEMS - 1)
                .forEach { add(SourcedText(clip(it, NOTE_CHARS), ItemSource.USER)) }
        }
        val questions = listOf(
            SourcedText("이번 분기 안에 결정이 가능한가요?", ItemSource.AI),
            SourcedText("경쟁사 대비 가장 걱정하시는 부분은 무엇인가요?", ItemSource.AI),
            SourcedText("다음 미팅에 함께하실 분은 누구인가요?", ItemSource.AI),
        )
        return Insight(summary = summary, concerns = concerns, expectedQuestions = questions)
    }

    fun extract(ctx: DemoVisitContext, request: ExtractActionsRequest): ExtractActionsResponse {
        val debrief = clip(request.debrief.trim(), BODY_DEBRIEF_CHARS).ifEmpty { "디브리핑 내용 없음" }
        val firstSentence = clip(
            debrief.split('.', '\n').firstOrNull { it.isNotBlank() }?.trim() ?: debrief,
            NOTE_CHARS,
        )
        val customerMail = EmailDraft(
            to = ctx.contactEmail,
            subject = "[${ctx.companyName}] 오늘 미팅 감사드립니다 · 후속 안내",
            body = buildString {
                appendLine("${ctx.contactName}님, 오늘 시간 내주셔서 감사합니다.")
                appendLine()
                appendLine("오늘 말씀 나눈 내용을 정리해 드립니다.")
                appendLine("- $firstSentence")
                appendLine()
                appendLine("논의한 다음 단계 일정은 확정되는 대로 다시 안내드리겠습니다.")
            }.trim(),
        )
        val internalMail = EmailDraft(
            to = INTERNAL_RECIPIENT,
            subject = "[방문 보고] ${ctx.companyName} · ${ctx.purpose}",
            body = buildString {
                appendLine("방문 목적: ${ctx.purpose}")
                appendLine("디브리핑 원문:")
                appendLine(debrief)
            }.trim(),
        )
        return ExtractActionsResponse(
            decisions = listOf(firstSentence),
            actions = listOf(
                ExtractedAction("${request.sessionId}-email-customer", ActionType.EMAIL, customerMail),
                ExtractedAction("${request.sessionId}-email-internal", ActionType.EMAIL, internalMail),
            ),
        )
    }

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
}
