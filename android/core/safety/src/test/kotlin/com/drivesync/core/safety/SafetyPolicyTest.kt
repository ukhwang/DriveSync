package com.drivesync.core.safety

import com.drivesync.core.model.DataOrigin
import com.drivesync.core.model.Gear
import com.drivesync.core.model.Signal
import com.drivesync.core.model.UxRestriction
import com.drivesync.core.model.VehicleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafetyPolicyTest {

    private val policy = SafetyPolicy(staleAfterMillis = 5_000)
    private val allCaps = PlatformCapabilities(ttsPermitted = true, sttPermitted = true, sttAllowedWhileDriving = true)

    /** Features that must NEVER be reachable while the restriction is active (P0-2: 위반 0건). */
    private val forbiddenWhileLimited = setOf(
        Feature.TEXT_INPUT,
        Feature.LIST_BROWSING,
        Feature.AI_CONVERSATION,
        Feature.CHEAT_SHEET,
        Feature.BRIEFING_CARD,
        Feature.DEBRIEF,
        Feature.ACTION_REVIEW,
        Feature.ACTION_APPROVAL,
    )

    private fun ctx(
        restriction: Signal<UxRestriction> = Signal.Available(UxRestriction.UNRESTRICTED),
        gear: Signal<Gear> = Signal.Available(Gear.PARK),
        observedAt: Long = 0L,
    ) = VehicleContext(
        uxRestriction = restriction,
        gear = gear,
        speedKmh = Signal.Available(0f),
        destination = Signal.Unsupported,
        etaMinutes = Signal.Unsupported,
        origin = DataOrigin.SIMULATED,
        observedAtMillis = observedAt,
    )

    @Test
    fun `parked and unrestricted unlocks the work screen`() {
        val state = policy.evaluate(ctx(), allCaps, nowMillis = 0)
        assertEquals(DrivingMode.PARKED_WORK, state.mode)
        assertTrue(state.allows(Feature.CHEAT_SHEET))
        assertTrue(state.allows(Feature.ACTION_APPROVAL))
    }

    @Test
    fun `no forbidden feature is reachable in any limited or fail-safe context`() {
        val limitedContexts = listOf(
            ctx(restriction = Signal.Available(UxRestriction.LIMITED), gear = Signal.Available(Gear.DRIVE)),
            ctx(restriction = Signal.Available(UxRestriction.LIMITED), gear = Signal.Available(Gear.PARK)),
            ctx(restriction = Signal.Unavailable("permission denied")),
            ctx(restriction = Signal.Unsupported),
            ctx(observedAt = 0L), // evaluated as stale below
        )
        limitedContexts.forEachIndexed { index, context ->
            val now = if (index == limitedContexts.lastIndex) 60_000L else 0L
            val state = policy.evaluate(context, allCaps, now)
            assertEquals(DrivingMode.LIMITED, state.mode, "context #$index must be LIMITED")
            forbiddenWhileLimited.forEach { feature ->
                assertIs<PolicyDecision.Denied>(policy.check(state, feature), "$feature leaked in context #$index")
            }
        }
        assertEquals(DrivingMode.LIMITED, policy.evaluate(null, allCaps, 0).mode)
    }

    @Test
    fun `P gear alone never unlocks when restriction is active`() {
        val state = policy.evaluate(
            ctx(restriction = Signal.Available(UxRestriction.LIMITED), gear = Signal.Available(Gear.PARK)),
            allCaps,
            0,
        )
        assertFalse(state.parkConfirmed)
        assertFalse(state.allows(Feature.CHEAT_SHEET))
    }

    @Test
    fun `fail-safe reason is reported when signal is lost`() {
        val state = policy.evaluate(ctx(restriction = Signal.Unavailable("수신 실패")), allCaps, 0)
        assertEquals(FailSafeReason.SignalUnavailable("수신 실패"), state.failSafe)
    }

    @Test
    fun `cheat sheet waits for P gear but falls back when gear is unsupported`() {
        val inDrive = policy.evaluate(ctx(gear = Signal.Available(Gear.NEUTRAL)), allCaps, 0)
        assertFalse(inDrive.allows(Feature.CHEAT_SHEET))
        assertTrue(inDrive.allows(Feature.DEBRIEF))

        val unsupported = policy.evaluate(ctx(gear = Signal.Unsupported), allCaps, 0)
        assertTrue(unsupported.allows(Feature.CHEAT_SHEET))

        val unavailable = policy.evaluate(ctx(gear = Signal.Unavailable("x")), allCaps, 0)
        assertFalse(unavailable.allows(Feature.CHEAT_SHEET))
    }

    @Test
    fun `voice memo while driving needs both STT permission and DO allowance`() {
        val driving = ctx(restriction = Signal.Available(UxRestriction.LIMITED), gear = Signal.Available(Gear.DRIVE))
        val noDo = allCaps.copy(sttAllowedWhileDriving = false)
        assertFalse(policy.evaluate(driving, noDo, 0).allows(Feature.ONE_SHOT_VOICE_MEMO))
        assertTrue(policy.evaluate(driving, allCaps, 0).allows(Feature.ONE_SHOT_VOICE_MEMO))
        assertTrue(policy.evaluate(ctx(), noDo, 0).allows(Feature.ONE_SHOT_VOICE_MEMO))
    }

    @Test
    fun `stop audio is always allowed and TTS follows permission`() {
        val driving = ctx(restriction = Signal.Available(UxRestriction.LIMITED))
        val state = policy.evaluate(driving, PlatformCapabilities.NONE, 0)
        assertTrue(state.allows(Feature.STOP_AUDIO))
        assertFalse(state.allows(Feature.ONEWAY_TTS_BRIEFING))
    }
}
