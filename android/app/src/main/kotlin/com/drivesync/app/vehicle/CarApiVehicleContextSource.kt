package com.drivesync.app.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.drivesync.core.model.DataOrigin
import com.drivesync.core.model.Gear
import com.drivesync.core.model.Signal
import com.drivesync.core.model.UxRestriction
import com.drivesync.core.model.VehicleContext
import com.drivesync.core.vehicle.VehicleContextSource
import com.drivesync.core.vehicle.VehicleContextSource.Companion.HEARTBEAT_MILLIS
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Real Vehicle Context Adapter on AAOS.
 *
 * - CarUxRestrictions: the single source of truth for what may be shown.
 * - GEAR_SELECTION / PERF_VEHICLE_SPEED via CarPropertyManager: auxiliary signals.
 *   Missing config => [Signal.Unsupported]; error/permission => [Signal.Unavailable].
 * - Destination/ETA: Pleos NaviHelper SDK is wired here once the contest SDK is provisioned.
 *   Until then they are reported as [Signal.Unsupported], never faked.
 */
class CarApiVehicleContextSource(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleContextSource {

    override val contexts: Flow<VehicleContext> = callbackFlow {
        var current = VehicleContext(
            uxRestriction = Signal.Unavailable("차량 서비스 연결 중"),
            gear = Signal.Unavailable("차량 서비스 연결 중"),
            speedKmh = Signal.Unavailable("차량 서비스 연결 중"),
            destination = Signal.Unsupported,
            etaMinutes = Signal.Unsupported,
            origin = DataOrigin.REAL,
            observedAtMillis = clock(),
        )
        val lock = Any()
        fun emit(change: (VehicleContext) -> VehicleContext) {
            val next = synchronized(lock) {
                current = change(current).copy(observedAtMillis = clock())
                current
            }
            trySend(next)
        }
        trySend(current)

        var uxManager: CarUxRestrictionsManager? = null
        var propertyManager: CarPropertyManager? = null
        val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { raw ->
                        emit { it.copy(gear = gearSignal(raw)) }
                    }
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { metersPerSecond ->
                        emit { it.copy(speedKmh = Signal.Available(metersPerSecond * MPS_TO_KMH)) }
                    }
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                when (propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION -> emit { it.copy(gear = Signal.Unavailable("기어 신호 오류")) }
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> emit { it.copy(speedKmh = Signal.Unavailable("속도 신호 오류")) }
                }
            }
        }

        val car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready ->
            if (!ready) {
                emit { it.copy(uxRestriction = Signal.Unavailable("차량 서비스 연결 끊김")) }
                return@createCar
            }
            val ux = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            if (ux == null) {
                emit { it.copy(uxRestriction = Signal.Unsupported) }
            } else {
                uxManager = ux
                emit { it.copy(uxRestriction = restrictionSignal(ux.currentCarUxRestrictions?.isRequiresDistractionOptimization)) }
                ux.registerListener { restrictions ->
                    emit { it.copy(uxRestriction = restrictionSignal(restrictions.isRequiresDistractionOptimization)) }
                }
            }

            val properties = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            propertyManager = properties
            listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PERF_VEHICLE_SPEED).forEach { id ->
                val supported = properties?.getCarPropertyConfig(id) != null
                val registered = supported && runCatching {
                    properties?.registerCallback(propertyCallback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) == true
                }.getOrDefault(false)
                if (!registered) {
                    val signal = if (supported) Signal.Unavailable("권한 거부") else Signal.Unsupported
                    when (id) {
                        VehiclePropertyIds.GEAR_SELECTION -> emit { it.copy(gear = signal) }
                        else -> emit { it.copy(speedKmh = signal) }
                    }
                }
            }
        }

        // Heartbeat: re-read the restriction every second. If the car service is gone the read
        // fails (=> Unavailable) or never happens (=> timestamp goes stale); both end in Fail-Safe.
        launch {
            while (isActive) {
                delay(HEARTBEAT_MILLIS)
                val ux = uxManager ?: continue
                val signal = runCatching { restrictionSignal(ux.currentCarUxRestrictions?.isRequiresDistractionOptimization) }
                    .getOrElse { Signal.Unavailable("UX 제한 상태 읽기 실패") }
                emit { it.copy(uxRestriction = signal) }
            }
        }

        awaitClose {
            runCatching { uxManager?.unregisterListener() }
            runCatching { propertyManager?.unregisterCallback(propertyCallback) }
            car.disconnect()
        }
    }

    private fun restrictionSignal(requiresDo: Boolean?): Signal<UxRestriction> = when (requiresDo) {
        null -> Signal.Unavailable("UX 제한 상태 없음")
        true -> Signal.Available(UxRestriction.LIMITED)
        false -> Signal.Available(UxRestriction.UNRESTRICTED)
    }

    /** android.car.VehicleGear constants (stable VHAL values). */
    private fun gearSignal(raw: Int): Signal<Gear> = when (raw) {
        GEAR_PARK -> Signal.Available(Gear.PARK)
        GEAR_REVERSE -> Signal.Available(Gear.REVERSE)
        GEAR_NEUTRAL -> Signal.Available(Gear.NEUTRAL)
        else -> if (raw >= GEAR_DRIVE) Signal.Available(Gear.DRIVE) else Signal.Unavailable("알 수 없는 기어 값 $raw")
    }

    private companion object {
        const val GEAR_NEUTRAL = 0x0001
        const val GEAR_REVERSE = 0x0002
        const val GEAR_PARK = 0x0004
        const val GEAR_DRIVE = 0x0008
        const val MPS_TO_KMH = 3.6f
    }
}
