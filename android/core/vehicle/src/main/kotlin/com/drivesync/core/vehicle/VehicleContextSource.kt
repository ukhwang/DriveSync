package com.drivesync.core.vehicle

import com.drivesync.core.model.VehicleContext
import kotlinx.coroutines.flow.Flow

/**
 * Vehicle Context Adapter contract (기획서 3-③-1).
 *
 * Real implementation (CarUxRestrictionsManager + Pleos Vehicle/NaviHelper/FusedLocation SDK)
 * lives in the Android app module; [MockVehicleContextSource] drives the emulator demo and tests.
 * Both emit through this single interface so nothing downstream knows which one is active,
 * except through [VehicleContext.origin], which the UI uses to show the "시뮬레이션" badge.
 *
 * Heartbeat contract: implementations re-emit the current context at least every
 * [HEARTBEAT_MILLIS] with a fresh [VehicleContext.observedAtMillis]. The safety policy treats a
 * context older than its stale limit as untrustworthy (Fail-Safe), so a dead car service can
 * never leave the screen unlocked. Duplicate meaning is filtered downstream on the derived
 * policy state, not here, so heartbeats are never swallowed.
 */
interface VehicleContextSource {
    val contexts: Flow<VehicleContext>

    companion object {
        const val HEARTBEAT_MILLIS: Long = 1_000
    }
}
