package com.drivesync.core.action

import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.ActionType
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.Feature
import com.drivesync.core.safety.PolicyState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionEngineTest {

    private val parked = PolicyState(DrivingMode.PARKED_WORK, null, true, Feature.entries.toSet())
    private val driving = PolicyState(DrivingMode.LIMITED, null, false, setOf(Feature.DESTINATION_ETA))
    private val draft = EmailDraft("buyer@example.com", "견적 후속", "본문")

    private class RecordingExecutor(var next: ExecutionResult = ExecutionResult.Executed("sent id=1")) : ActionExecutor {
        val requests = mutableListOf<ExecutionRequest>()
        override suspend fun execute(request: ExecutionRequest): ExecutionResult {
            requests += request
            return next
        }
    }

    private suspend fun setup(status: ActionStatus = ActionStatus.DRAFT): Triple<ActionEngine, InMemoryActionStore, RecordingExecutor> {
        val store = InMemoryActionStore()
        store.upsert(FollowUpAction("a1", "s1", "c1", ActionType.EMAIL, draft, status))
        val executor = RecordingExecutor()
        return Triple(ActionEngine(store, executor) { 0L }, store, executor)
    }

    @Test
    fun `full lifecycle draft to executed`() = runTest {
        val (engine, store, executor) = setup()
        assertIs<ActionOutcome.Ok>(engine.startReview("a1"))
        assertIs<ActionOutcome.Ok>(engine.approve("a1", parked))
        assertIs<ActionOutcome.Ok>(engine.execute("a1"))
        val final = store.get("a1")!!
        assertEquals(ActionStatus.EXECUTED, final.status)
        assertEquals(listOf("a1:v1"), executor.requests.map { it.idempotencyKey })
        assertEquals(3, final.log.size)
    }

    @Test
    fun `unapproved actions never reach the executor`() = runTest {
        for (status in ActionStatus.entries.filter { it != ActionStatus.APPROVED }) {
            val (engine, _, executor) = setup(status)
            assertIs<ActionOutcome.Rejected>(engine.execute("a1"), "execute from $status")
            assertTrue(executor.requests.isEmpty(), "executor called from $status")
        }
    }

    @Test
    fun `approval is blocked while driving`() = runTest {
        val (engine, store, _) = setup(ActionStatus.REVIEW)
        assertIs<ActionOutcome.Rejected>(engine.approve("a1", driving))
        assertEquals(ActionStatus.REVIEW, store.get("a1")!!.status)
    }

    @Test
    fun `cannot skip review`() = runTest {
        val (engine, _, _) = setup(ActionStatus.DRAFT)
        assertIs<ActionOutcome.Rejected>(engine.approve("a1", parked))
    }

    @Test
    fun `failure is never shown as success and retry reuses the same key`() = runTest {
        val (engine, store, executor) = setup(ActionStatus.REVIEW)
        executor.next = ExecutionResult.Failed("네트워크 연결 없음")
        engine.approve("a1", parked)
        engine.execute("a1")
        assertEquals(ActionStatus.FAILED, store.get("a1")!!.status)
        assertEquals("네트워크 연결 없음", store.get("a1")!!.failureReason)

        assertIs<ActionOutcome.Ok>(engine.scheduleRetry("a1"))
        assertEquals(ActionStatus.RETRY, store.get("a1")!!.status)
        assertIs<ActionOutcome.Rejected>(engine.execute("a1"), "retry must be re-approved first")

        executor.next = ExecutionResult.AlreadyExecuted("server had it")
        engine.approve("a1", parked)
        engine.execute("a1")
        assertEquals(ActionStatus.EXECUTED, store.get("a1")!!.status)
        assertEquals(listOf("a1:v1", "a1:v1"), executor.requests.map { it.idempotencyKey })
    }

    @Test
    fun `edited draft gets a new idempotency key`() = runTest {
        val (engine, _, executor) = setup(ActionStatus.REVIEW)
        engine.editDraft("a1", draft.copy(body = "수정된 본문"))
        engine.approve("a1", parked)
        engine.execute("a1")
        assertEquals("a1:v2", executor.requests.single().idempotencyKey)
    }

    @Test
    fun `executed action can never execute again`() = runTest {
        val (engine, _, executor) = setup(ActionStatus.REVIEW)
        engine.approve("a1", parked)
        val results = List(5) { async { engine.execute("a1") } }.awaitAll()
        assertEquals(1, results.count { it is ActionOutcome.Ok })
        assertEquals(1, executor.requests.size)
    }
}
