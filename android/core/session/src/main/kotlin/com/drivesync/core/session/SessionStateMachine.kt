package com.drivesync.core.session

import com.drivesync.core.model.SessionPhase
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.PolicyState

data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val visitId: String? = null,
    /** Phase to return to after PAUSED_BY_POLICY or ERROR recovery. */
    val resumePhase: SessionPhase? = null,
    /** True while approvals are held because the car started moving (주행 시작 → 승인 보류). */
    val approvalsOnHold: Boolean = false,
    /** Last known driving mode; defaults to LIMITED until the first vehicle update (Fail-Safe). */
    val drivingMode: DrivingMode = DrivingMode.LIMITED,
    val etaMinutes: Int? = null,
)

sealed interface SessionEvent {
    /** User confirmed that the navigation destination matches this visit. */
    data class SessionStarted(val visitId: String) : SessionEvent

    data class VehicleUpdated(val policy: PolicyState, val etaMinutes: Int?) : SessionEvent

    data class DebriefCompleted(val draftedActions: Int) : SessionEvent

    data object ExecutionStarted : SessionEvent

    /** [openActions] = actions still not EXECUTED (failed/retry/unapproved). */
    data class ExecutionFinished(val openActions: Int) : SessionEvent

    /** User ends the visit; open actions carry over to the next visit briefing. */
    data object SessionClosed : SessionEvent

    data class Fatal(val reason: String) : SessionEvent

    /** Restore from the encrypted local snapshot after ERROR or process death. */
    data class RecoveredFromLocal(val snapshot: SessionState?) : SessionEvent
}

sealed interface Transition {
    val state: SessionState

    data class Accepted(override val state: SessionState) : Transition
    data class Rejected(override val state: SessionState, val reason: String) : Transition
}

/**
 * Pure reducer for the visit session (기획서 3-④). No I/O; fully unit-tested.
 */
object SessionStateMachine {

    const val ARRIVAL_ETA_MINUTES = 5

    private val pausable = setOf(
        SessionPhase.PREPARE_SESSION,
        SessionPhase.DRIVING_LIMITED,
        SessionPhase.ARRIVAL_PENDING,
        SessionPhase.PARKED_REVIEW,
        SessionPhase.AWAITING_APPROVAL,
    )

    fun reduce(state: SessionState, event: SessionEvent): Transition = when (event) {
        is SessionEvent.SessionStarted -> startSession(state, event.visitId)
        is SessionEvent.VehicleUpdated -> Transition.Accepted(onVehicle(state, event))
        is SessionEvent.DebriefCompleted -> onDebrief(state, event.draftedActions)
        SessionEvent.ExecutionStarted -> onExecutionStarted(state)
        is SessionEvent.ExecutionFinished -> onExecutionFinished(state, event.openActions)
        SessionEvent.SessionClosed -> onClosed(state)
        is SessionEvent.Fatal -> Transition.Accepted(
            state.copy(phase = SessionPhase.ERROR, resumePhase = state.phase.takeIf { it != SessionPhase.ERROR }),
        )
        is SessionEvent.RecoveredFromLocal -> onRecovered(state, event.snapshot)
    }

    private fun startSession(state: SessionState, visitId: String): Transition = when {
        state.phase != SessionPhase.IDLE && state.phase != SessionPhase.COMPLETED ->
            Transition.Rejected(state, "진행 중인 세션이 있습니다")
        state.drivingMode != DrivingMode.PARKED_WORK ->
            Transition.Rejected(state, "세션 시작은 정차 중에만 가능합니다")
        else -> Transition.Accepted(
            SessionState(
                phase = SessionPhase.PREPARE_SESSION,
                visitId = visitId,
                drivingMode = state.drivingMode,
                etaMinutes = state.etaMinutes,
            ),
        )
    }

    private fun onVehicle(state: SessionState, event: SessionEvent.VehicleUpdated): SessionState {
        val policy = event.policy
        val updated = state.copy(drivingMode = policy.mode, etaMinutes = event.etaMinutes ?: state.etaMinutes)

        if (policy.failSafe != null) {
            return if (updated.phase in pausable) {
                updated.copy(phase = SessionPhase.PAUSED_BY_POLICY, resumePhase = updated.phase)
            } else {
                updated
            }
        }

        if (updated.phase == SessionPhase.PAUSED_BY_POLICY) {
            val resumed = updated.copy(phase = updated.resumePhase ?: SessionPhase.IDLE, resumePhase = null)
            return advance(resumed, policy, event.etaMinutes)
        }
        return advance(updated, policy, event.etaMinutes)
    }

    private fun advance(state: SessionState, policy: PolicyState, eta: Int?): SessionState {
        val parkedAndConfirmed = policy.mode == DrivingMode.PARKED_WORK && policy.parkConfirmed
        return when (state.phase) {
            SessionPhase.PREPARE_SESSION ->
                if (policy.mode == DrivingMode.LIMITED) state.copy(phase = SessionPhase.DRIVING_LIMITED) else state

            SessionPhase.DRIVING_LIMITED -> when {
                eta != null && eta <= ARRIVAL_ETA_MINUTES ->
                    advance(state.copy(phase = SessionPhase.ARRIVAL_PENDING), policy, eta)
                // ETA signal missing (NaviHelper unsupported): arrival judged by parking alone.
                eta == null && parkedAndConfirmed -> state.copy(phase = SessionPhase.PARKED_REVIEW)
                else -> state
            }

            SessionPhase.ARRIVAL_PENDING ->
                if (parkedAndConfirmed) state.copy(phase = SessionPhase.PARKED_REVIEW) else state

            SessionPhase.AWAITING_APPROVAL ->
                state.copy(approvalsOnHold = policy.mode == DrivingMode.LIMITED)

            else -> state
        }
    }

    private fun onDebrief(state: SessionState, drafted: Int): Transition = when {
        state.phase != SessionPhase.PARKED_REVIEW ->
            Transition.Rejected(state, "디브리핑은 도착 후 정차 상태에서만 가능합니다")
        state.drivingMode != DrivingMode.PARKED_WORK ->
            Transition.Rejected(state, "주행 중에는 디브리핑할 수 없습니다")
        drafted == 0 -> Transition.Accepted(state.copy(phase = SessionPhase.COMPLETED))
        else -> Transition.Accepted(state.copy(phase = SessionPhase.AWAITING_APPROVAL, approvalsOnHold = false))
    }

    private fun onExecutionStarted(state: SessionState): Transition = when {
        state.phase != SessionPhase.AWAITING_APPROVAL -> Transition.Rejected(state, "승인 대기 상태가 아닙니다")
        state.approvalsOnHold || state.drivingMode != DrivingMode.PARKED_WORK ->
            Transition.Rejected(state, "주행 중에는 승인이 보류됩니다")
        else -> Transition.Accepted(state.copy(phase = SessionPhase.EXECUTING))
    }

    private fun onExecutionFinished(state: SessionState, openActions: Int): Transition =
        if (state.phase != SessionPhase.EXECUTING) {
            Transition.Rejected(state, "실행 중이 아닙니다")
        } else if (openActions > 0) {
            Transition.Accepted(state.copy(phase = SessionPhase.AWAITING_APPROVAL))
        } else {
            Transition.Accepted(state.copy(phase = SessionPhase.COMPLETED))
        }

    private fun onClosed(state: SessionState): Transition =
        if (state.phase == SessionPhase.AWAITING_APPROVAL || state.phase == SessionPhase.PARKED_REVIEW) {
            Transition.Accepted(state.copy(phase = SessionPhase.COMPLETED, approvalsOnHold = false))
        } else {
            Transition.Rejected(state, "지금은 세션을 종료할 수 없습니다")
        }

    private fun onRecovered(state: SessionState, snapshot: SessionState?): Transition {
        if (state.phase != SessionPhase.ERROR && state.phase != SessionPhase.IDLE) {
            return Transition.Rejected(state, "복구할 세션이 없습니다")
        }
        // Never resume straight into EXECUTING: the server result is unknown, so go back to approval.
        val restored = when (snapshot?.phase) {
            null -> SessionState(drivingMode = state.drivingMode)
            SessionPhase.EXECUTING -> snapshot.copy(phase = SessionPhase.AWAITING_APPROVAL)
            SessionPhase.PAUSED_BY_POLICY, SessionPhase.ERROR ->
                snapshot.copy(phase = snapshot.resumePhase ?: SessionPhase.IDLE)
            else -> snapshot
        }
        return Transition.Accepted(restored.copy(drivingMode = state.drivingMode, resumePhase = null))
    }
}
