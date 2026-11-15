package com.drivesync.core.ai

import com.drivesync.core.action.ActionEngine
import com.drivesync.core.action.ActionOutcome
import com.drivesync.core.action.ExecutionRequest
import com.drivesync.core.action.ExecutionResult
import com.drivesync.core.action.InMemoryActionStore
import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.BriefingTier
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.ItemSource
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.Feature
import com.drivesync.core.safety.PolicyState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class OfflineDemoTest {

    private val ctx = DemoVisitContext(
        companyName = "한빛메디칼",
        contactName = "김도윤 구매팀장",
        contactEmail = "buyer@hanbit-medical.example",
        historySummary = "1차 방문: 초음파 프로브 교체 수요 확인, 납기 4주 이내 요구. 경쟁사 견적 대비 8% 높다는 피드백.",
        purpose = "2차 견적 협의 · 납기 조건 확정",
    )
    private val gateway = OfflineDemoAiGateway { id -> if (id == "v-1") ctx else null }
    private val parked = PolicyState(DrivingMode.PARKED_WORK, null, true, Feature.entries.toSet())

    private fun <T> EdgeResult<T>.ok(): T = when (this) {
        is EdgeResult.Success -> value
        else -> fail("expected success but was $this")
    }

    @Test
    fun `briefing passes the client contract for every ETA tier`() = runTest {
        for (tier in BriefingTier.entries) {
            val result = gateway.briefing(BriefingRequest("v-1", etaMinutes = 18, tier = tier))
            val briefing = result.ok()
            assertEquals(tier, briefing.tier)
            if (tier == BriefingTier.SHORT) assertNull(briefing.preArrivalRecap)
        }
    }

    @Test
    fun `insight keeps exactly three summary lines and tags travel memos as user records`() = runTest {
        val manyNotes = List(9) { "메모 $it " + "가".repeat(400) }
        for (notes in listOf(emptyList(), listOf("경쟁사 AS 지연 문제 공략"), manyNotes)) {
            val insight = gateway.insight(InsightRequest("v-1", notes)).ok()
            assertTrue(ContractValidator.insight(insight).isEmpty())
            assertEquals(ContractValidator.SUMMARY_LINES, insight.summary.size)
            if (notes.isNotEmpty()) {
                assertTrue(insight.summary.any { it.source == ItemSource.USER && it.text.startsWith(notes.last().take(10)) })
            }
        }
    }

    @Test
    fun `extracted actions are schema-valid drafts with unique ids`() = runTest {
        val request = ExtractActionsRequest("s-1", "v-1", "신규 장비 3대 도입 긍정 검토. 금요일 오전 10시 최종 데모 확정.")
        val actions = gateway.extractActions(request, "c-hanbit").ok()
        assertEquals(2, actions.size)
        assertEquals(actions.size, actions.map { it.id }.toSet().size)
        assertTrue(actions.all { it.status == ActionStatus.DRAFT && it.sessionId == "s-1" && it.customerId == "c-hanbit" })
        assertEquals("buyer@hanbit-medical.example", actions.first().draft.to)
        assertTrue(actions.all { it.draft.to.endsWith(".example") }, "offline demo must only address reserved domains")
    }

    @Test
    fun `blank-looking debrief still yields a valid draft`() = runTest {
        val actions = gateway.extractActions(ExtractActionsRequest("s-2", "v-1", "..."), "c-hanbit").ok()
        assertTrue(actions.all { it.draft.body.isNotBlank() })
    }

    @Test
    fun `unknown visit is reported, not invented`() = runTest {
        assertIs<EdgeResult.Server>(gateway.briefing(BriefingRequest("nope", 5, BriefingTier.SHORT)))
    }

    @Test
    fun `simulated executor fails once then executes and never re-sends the same key`() = runTest {
        val executor = SimulatedActionExecutor(failFirstAttempts = 1, latencyMillis = 0)
        val request = ExecutionRequest("a1", "a1:v1", EmailDraft("buyer@test.example", "s", "b"), 1)
        assertIs<ExecutionResult.Failed>(executor.execute(request))
        assertIs<ExecutionResult.Executed>(executor.execute(request))
        assertIs<ExecutionResult.AlreadyExecuted>(executor.execute(request))
    }

    @Test
    fun `offline loop walks draft, failed, retry, executed with one idempotency key`() = runTest {
        val store = InMemoryActionStore()
        val engine = ActionEngine(store, SimulatedActionExecutor(failFirstAttempts = 1, latencyMillis = 0)) { 0L }
        val actions = gateway.extractActions(ExtractActionsRequest("s-3", "v-1", "데모 일정 확정."), "c-hanbit").ok()
        actions.forEach { store.upsert(it) }
        val id = actions.first().id

        assertIs<ActionOutcome.Rejected>(engine.execute(id), "draft must not execute")
        assertIs<ActionOutcome.Ok>(engine.startReview(id))
        assertIs<ActionOutcome.Ok>(engine.approve(id, parked))
        assertEquals(ActionStatus.FAILED, (engine.execute(id) as ActionOutcome.Ok).action.status)
        assertIs<ActionOutcome.Ok>(engine.scheduleRetry(id))
        assertIs<ActionOutcome.Ok>(engine.approve(id, parked))
        val done = (engine.execute(id) as ActionOutcome.Ok).action
        assertEquals(ActionStatus.EXECUTED, done.status)
        assertEquals("$id:v1", done.idempotencyKey)
        assertTrue(done.log.last().message.contains("시뮬레이션"))
    }
}
