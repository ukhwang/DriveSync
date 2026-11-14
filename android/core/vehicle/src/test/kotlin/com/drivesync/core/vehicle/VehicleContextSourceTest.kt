package com.drivesync.core.vehicle

import com.drivesync.core.model.DataOrigin
import com.drivesync.core.model.Signal
import com.drivesync.core.model.UxRestriction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleContextSourceTest {

    @Test
    fun `mock source is always tagged simulated`() {
        val source = MockVehicleContextSource { 0L }
        source.startDriving()
        assertEquals(DataOrigin.SIMULATED, source.current.value.origin)
        assertEquals(Signal.Available(UxRestriction.LIMITED), source.current.value.uxRestriction)
    }

    @Test
    fun `heartbeat refreshes the timestamp every second`() = runTest {
        val source = MockVehicleContextSource { currentTime }
        val stamps = source.contexts.take(3).toList().map { it.observedAtMillis }
        assertEquals(listOf(0L, 1_000L, 2_000L), stamps)
    }

    @Test
    fun `frozen heartbeat keeps the old timestamp so the policy can go stale`() = runTest {
        val source = MockVehicleContextSource { currentTime }
        source.freezeHeartbeat(true)
        val stamps = source.contexts.take(3).toList().map { it.observedAtMillis }
        assertTrue(stamps.all { it == 0L })
    }
}
