package com.drivesync.core.action

import com.drivesync.core.model.ActionLogEntry
import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.safety.Feature
import com.drivesync.core.safety.PolicyState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Allowed edges of the 6-state lifecycle. Anything else is rejected. */
object ActionTransitions {
    private val edges: Map<ActionStatus, Set<ActionStatus>> = mapOf(
        ActionStatus.DRAFT to setOf(ActionStatus.REVIEW),
        ActionStatus.REVIEW to setOf(ActionStatus.REVIEW, ActionStatus.APPROVED),
        ActionStatus.APPROVED to setOf(ActionStatus.EXECUTED, ActionStatus.FAILED),
        ActionStatus.FAILED to setOf(ActionStatus.RETRY),
        ActionStatus.RETRY to setOf(ActionStatus.REVIEW, ActionStatus.APPROVED),
        ActionStatus.EXECUTED to emptySet(),
    )

    fun canTransition(from: ActionStatus, to: ActionStatus): Boolean = to in edges.getValue(from)
}

/** Server request for an approved action. The server re-checks everything. */
data class ExecutionRequest(
    val actionId: String,
    val idempotencyKey: String,
    val draft: EmailDraft,
    val draftVersion: Int,
)

sealed interface ExecutionResult {
    /** Server confirmed the external side effect. The ONLY path to EXECUTED. */
    data class Executed(val serverLog: String) : ExecutionResult

    /** Server already executed this idempotency key earlier; treated as executed, not re-sent. */
    data class AlreadyExecuted(val serverLog: String) : ExecutionResult

    data class Failed(val reason: String) : ExecutionResult
}

fun interface ActionExecutor {
    suspend fun execute(request: ExecutionRequest): ExecutionResult
}

sealed interface ActionOutcome {
    data class Ok(val action: FollowUpAction) : ActionOutcome
    data class Rejected(val reason: String) : ActionOutcome
}

/**
 * Action Engine (기획서 2-③, 3-③-5).
 *
 * Invariants (each covered by ActionEngineTest):
 * - Nothing reaches the executor unless it is APPROVED (미승인 실행 0건).
 * - EXECUTED is set only from a server success, never optimistically.
 * - Approval needs the parked work mode.
 * - Same draft => same idempotency key, so a retry after an unknown result cannot double-send.
 * - Per-action mutex: two taps on "execute" cannot run two requests.
 */
class ActionEngine(
    private val store: ActionStore,
    private val executor: ActionExecutor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    suspend fun startReview(actionId: String): ActionOutcome = withAction(actionId) { action ->
        transition(action, ActionStatus.REVIEW, "검토 시작")
    }

    suspend fun editDraft(actionId: String, draft: EmailDraft): ActionOutcome = withAction(actionId) { action ->
        if (action.status != ActionStatus.REVIEW) return@withAction ActionOutcome.Rejected("검토 중인 작업만 수정할 수 있습니다")
        val edited = action.copy(draft = draft, draftVersion = action.draftVersion + 1, idempotencyKey = null)
        transition(edited, ActionStatus.REVIEW, "초안 수정 v${edited.draftVersion}")
    }

    suspend fun approve(actionId: String, policy: PolicyState): ActionOutcome = withAction(actionId) { action ->
        if (!policy.allows(Feature.ACTION_APPROVAL)) {
            return@withAction ActionOutcome.Rejected("주행 중에는 승인할 수 없습니다")
        }
        val keyed = action.copy(idempotencyKey = idempotencyKeyFor(action))
        transition(keyed, ActionStatus.APPROVED, "사용자 승인")
    }

    suspend fun execute(actionId: String): ActionOutcome = withAction(actionId) { action ->
        val key = action.idempotencyKey
        if (action.status != ActionStatus.APPROVED || key == null) {
            return@withAction ActionOutcome.Rejected("승인되지 않은 작업은 실행할 수 없습니다")
        }
        val request = ExecutionRequest(action.id, key, action.draft, action.draftVersion)
        when (val result = executor.execute(request)) {
            is ExecutionResult.Executed ->
                transition(action.copy(failureReason = null), ActionStatus.EXECUTED, result.serverLog)
            is ExecutionResult.AlreadyExecuted ->
                transition(action.copy(failureReason = null), ActionStatus.EXECUTED, "이미 실행됨: ${result.serverLog}")
            is ExecutionResult.Failed ->
                transition(action.copy(failureReason = result.reason), ActionStatus.FAILED, result.reason)
        }
    }

    /** FAILED -> RETRY: shown as "다음 정차 때 재시도" until the user approves again. */
    suspend fun scheduleRetry(actionId: String): ActionOutcome = withAction(actionId) { action ->
        transition(action, ActionStatus.RETRY, "재시도 대기")
    }

    private fun idempotencyKeyFor(action: FollowUpAction): String = "${action.id}:v${action.draftVersion}"

    private suspend fun transition(action: FollowUpAction, to: ActionStatus, message: String): ActionOutcome {
        val current = store.get(action.id) ?: return ActionOutcome.Rejected("작업을 찾을 수 없습니다")
        if (!ActionTransitions.canTransition(current.status, to)) {
            return ActionOutcome.Rejected("${current.status} → $to 전환은 허용되지 않습니다")
        }
        val updated = action.copy(
            status = to,
            log = current.log + ActionLogEntry(clock(), current.status, to, message),
        )
        store.upsert(updated)
        return ActionOutcome.Ok(updated)
    }

    private suspend fun withAction(
        actionId: String,
        block: suspend (FollowUpAction) -> ActionOutcome,
    ): ActionOutcome {
        val lock = locksGuard.withLock { locks.getOrPut(actionId) { Mutex() } }
        return lock.withLock {
            val action = store.get(actionId) ?: return@withLock ActionOutcome.Rejected("작업을 찾을 수 없습니다")
            block(action)
        }
    }
}
