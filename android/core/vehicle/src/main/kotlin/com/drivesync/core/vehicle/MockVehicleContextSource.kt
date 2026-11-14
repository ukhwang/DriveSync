package com.drivesync.core.vehicle

import com.drivesync.core.model.DataOrigin
import com.drivesync.core.model.Destination
import com.drivesync.core.model.Gear
import com.drivesync.core.model.Signal
import com.drivesync.core.model.UxRestriction
import com.drivesync.core.model.VehicleContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Scriptable vehicle for the emulator demo and tests. Every emitted context is tagged
 * [DataOrigin.SIMULATED]. Starts parked with no destination. Emits a heartbeat like a real
 * car service, unless [freezeHeartbeat] simulates a hung service (stale-signal Fail-Safe demo).
 */
class MockVehicleContextSource(
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleContextSource {

    private val state = MutableStateFlow(
        VehicleContext(
            uxRestriction = Signal.Available(UxRestriction.UNRESTRICTED),
            gear = Signal.Available(Gear.PARK),
            speedKmh = Signal.Available(0f),
            destination = Signal.Unavailable("목적지 미설정"),
            etaMinutes = Signal.Unavailable("목적지 미설정"),
            origin = DataOrigin.SIMULATED,
            observedAtMillis = clock(),
        ),
    )

    /** Latest scripted context (no heartbeat). */
    val current: StateFlow<VehicleContext> = state.asStateFlow()

    private val heartbeatFrozen = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val contexts: Flow<VehicleContext> = state.flatMapLatest { latest ->
        flow {
            var context = latest
            while (true) {
                emit(context)
                delay(VehicleContextSource.HEARTBEAT_MILLIS)
                if (!heartbeatFrozen.value) context = context.copy(observedAtMillis = clock())
            }
        }
    }

    /** Stops refreshing timestamps, as if the car service hung without reporting an error. */
    fun freezeHeartbeat(frozen: Boolean) {
        heartbeatFrozen.value = frozen
    }

    fun setDestination(destination: Destination, etaMinutes: Int) = mutate {
        it.copy(destination = Signal.Available(destination), etaMinutes = Signal.Available(etaMinutes))
    }

    fun setEta(etaMinutes: Int) = mutate { it.copy(etaMinutes = Signal.Available(etaMinutes)) }

    fun startDriving(speedKmh: Float = 40f) = mutate {
        it.copy(
            uxRestriction = Signal.Available(UxRestriction.LIMITED),
            gear = Signal.Available(Gear.DRIVE),
            speedKmh = Signal.Available(speedKmh),
        )
    }

    fun park() = mutate {
        it.copy(
            uxRestriction = Signal.Available(UxRestriction.UNRESTRICTED),
            gear = Signal.Available(Gear.PARK),
            speedKmh = Signal.Available(0f),
        )
    }

    /** Simulates CarUxRestrictions receive failure or permission denial (Fail-Safe demo). */
    fun loseRestrictionSignal(reason: String = "차량 상태 수신 실패") = mutate {
        it.copy(uxRestriction = Signal.Unavailable(reason))
    }

    fun restoreRestrictionSignal(restriction: UxRestriction) = mutate {
        it.copy(uxRestriction = Signal.Available(restriction))
    }

    /** Vehicle without a gear signal capability: policy must fall back to CarUxRestrictions only. */
    fun markGearUnsupported() = mutate { it.copy(gear = Signal.Unsupported) }

    private fun mutate(change: (VehicleContext) -> VehicleContext) {
        state.update { change(it).copy(observedAtMillis = clock()) }
    }
}
