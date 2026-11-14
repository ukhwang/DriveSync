package com.drivesync.core.session

import com.drivesync.core.model.SessionPhase
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.FailSafeReason
import com.drivesync.core.safety.PolicyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionStateMachineTest {

    private val parked = PolicyState(DrivingMode.PARKED_WORK, failSafe = null, parkConfirmed = true, allowed = emptySet())
    private val parkedNotP = parked.copy(parkConfirmed = false)
    private val driving = PolicyState(DrivingMode.LIMITED, failSafe = null, parkConfirmed = false, allowed = emptySet())
    private val lost = driving.copy(failSafe = FailSafeReason.SignalUnavailable("수신 실패"))

    private fun SessionState.on(event: SessionEvent): SessionState = SessionStateMachine.reduce(this, event).state
    private fun SessionState.vehicle(policy: PolicyState, eta: Int? = 18) = on(SessionEvent.VehicleUpdated(policy, eta))

    @Test
    fun `happy path walks all five visit stages`() {
        val s = SessionState()
            .vehicle(parked)
            .on(SessionEvent.SessionStarted("visit-1"))
            .also { assertEquals(SessionPhase.PREPARE_SESSION, it.phase) }
            .vehicle(driving, eta = 18)
            .also { assertEquals(SessionPhase.DRIVING_LIMITED, it.phase) }
            .vehicle(driving, eta = 5)
            .also { assertEquals(SessionPhase.ARRIVAL_PENDING, it.phase) }
            .vehicle(parked, eta = 0)
            .also { assertEquals(SessionPhase.PARKED_REVIEW, it.phase) }
            .on(SessionEvent.DebriefCompleted(draftedActions = 2))
            .also { assertEquals(SessionPhase.AWAITING_APPROVAL, it.phase) }
            .on(SessionEvent.ExecutionStarted)
            .also { assertEquals(SessionPhase.EXECUTING, it.phase) }
            .on(SessionEvent.ExecutionFinished(openActions = 0))
        assertEquals(SessionPhase.COMPLETED, s.phase)
    }

    @Test
    fun `session cannot start while driving or before first vehicle signal`() {
        assertIs<Transition.Rejected>(SessionStateMachine.reduce(SessionState(), SessionEvent.SessionStarted("v")))
        val drivingState = SessionState().vehicle(driving)
        assertIs<Transition.Rejected>(SessionStateMachine.reduce(drivingState, SessionEvent.SessionStarted("v")))
    }

    @Test
    fun `arrival requires restriction lifted AND park confirmed`() {
        val arriving = SessionState().vehicle(parked).on(SessionEvent.SessionStarted("v"))
            .vehicle(driving, 12).vehicle(driving, 4)
        assertEquals(SessionPhase.ARRIVAL_PENDING, arriving.phase)
        assertEquals(SessionPhase.ARRIVAL_PENDING, arriving.vehicle(parkedNotP, 0).phase)
        assertEquals(SessionPhase.PARKED_REVIEW, arriving.vehicle(parked, 0).phase)
    }

    @Test
    fun `signal loss pauses the session and recovery resumes the previous phase`() {
        val drivingState = SessionState().vehicle(parked).on(SessionEvent.SessionStarted("v")).vehicle(driving, 20)
        val paused = drivingState.vehicle(lost, null)
        assertEquals(SessionPhase.PAUSED_BY_POLICY, paused.phase)
        assertEquals(SessionPhase.DRIVING_LIMITED, paused.resumePhase)
        val resumed = paused.vehicle(driving, 20)
        assertEquals(SessionPhase.DRIVING_LIMITED, resumed.phase)
    }

    @Test
    fun `recovery after pause re-evaluates arrival immediately`() {
        val paused = SessionState().vehicle(parked).on(SessionEvent.SessionStarted("v"))
            .vehicle(driving, 20).vehicle(lost, null)
        assertEquals(SessionPhase.ARRIVAL_PENDING, paused.vehicle(driving, 3).phase)
    }

    @Test
    fun `driving away during approval holds approvals and blocks execution`() {
        val awaiting = SessionState().vehicle(parked).on(SessionEvent.SessionStarted("v"))
            .vehicle(driving, 6).vehicle(driving, 2).vehicle(parked, 0)
            .on(SessionEvent.DebriefCompleted(1))
        val moving = awaiting.vehicle(driving, null)
        assertTrue(moving.approvalsOnHold)
        assertIs<Transition.Rejected>(SessionStateMachine.reduce(moving, SessionEvent.ExecutionStarted))
        val parkedAgain = moving.vehicle(parked, null)
        assertIs<Transition.Accepted>(SessionStateMachine.reduce(parkedAgain, SessionEvent.ExecutionStarted))
    }

    @Test
    fun `failed actions return to approval instead of completing`() {
        val executing = SessionState(phase = SessionPhase.EXECUTING, drivingMode = DrivingMode.PARKED_WORK)
        assertEquals(SessionPhase.AWAITING_APPROVAL, executing.on(SessionEvent.ExecutionFinished(1)).phase)
    }

    @Test
    fun `debrief is rejected outside parked review`() {
        val drivingState = SessionState().vehicle(parked).on(SessionEvent.SessionStarted("v")).vehicle(driving)
        assertIs<Transition.Rejected>(SessionStateMachine.reduce(drivingState, SessionEvent.DebriefCompleted(1)))
    }

    @Test
    fun `missing ETA falls back to parking for arrival`() {
        val s = SessionState().vehicle(parked).on(SessionEvent.SessionStarted("v"))
            .vehicle(driving, null).vehicle(parked, null)
        assertEquals(SessionPhase.PARKED_REVIEW, s.phase)
    }

    @Test
    fun `error recovers from local snapshot but never resumes mid-execution`() {
        val snapshot = SessionState(phase = SessionPhase.EXECUTING, visitId = "v")
        val error = SessionState(drivingMode = DrivingMode.PARKED_WORK).on(SessionEvent.Fatal("db"))
        assertEquals(SessionPhase.ERROR, error.phase)
        val recovered = error.on(SessionEvent.RecoveredFromLocal(snapshot))
        assertEquals(SessionPhase.AWAITING_APPROVAL, recovered.phase)
        assertEquals("v", recovered.visitId)
    }
}
