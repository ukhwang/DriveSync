package com.drivesync.core.model

/**
 * Mirrors AAOS `CarUxRestrictions.isRequiresDistractionOptimization()`.
 * This is the ONLY input that decides what the screen may show (기획서 1-①).
 */
enum class UxRestriction { UNRESTRICTED, LIMITED }

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE }

/** Where a value came from. Simulated values must never be presented as measurements. */
enum class DataOrigin { REAL, SIMULATED }

data class Destination(
    val name: String,
    val location: GeoPoint,
)

/**
 * A vehicle signal with its capability status made explicit, so callers cannot
 * silently treat "unsupported" or "failed" as a real value.
 */
sealed interface Signal<out T> {
    data class Available<T>(val value: T) : Signal<T>

    /** Capability check reported the vehicle/emulator does not provide this signal. */
    data object Unsupported : Signal<Nothing>

    /** Signal is supported but currently untrustworthy (receive failure, permission denied). */
    data class Unavailable(val reason: String) : Signal<Nothing>
}

fun <T> Signal<T>.valueOrNull(): T? = (this as? Signal.Available<T>)?.value

/**
 * One merged snapshot from the Vehicle Context Adapter
 * (CarUxRestrictions + Vehicle SDK + NaviHelper + FusedLocation).
 */
data class VehicleContext(
    val uxRestriction: Signal<UxRestriction>,
    val gear: Signal<Gear>,
    val speedKmh: Signal<Float>,
    val destination: Signal<Destination>,
    val etaMinutes: Signal<Int>,
    val origin: DataOrigin,
    val observedAtMillis: Long,
) {
    /** Same vehicle meaning, ignoring the timestamp. Used to debounce duplicate events. */
    fun sameMeaningAs(other: VehicleContext): Boolean =
        uxRestriction == other.uxRestriction &&
            gear == other.gear &&
            destination == other.destination &&
            etaMinutes == other.etaMinutes &&
            origin == other.origin &&
            speedBucket() == other.speedBucket()

    private fun speedBucket(): Int? = speedKmh.valueOrNull()?.let { (it / SPEED_BUCKET_KMH).toInt() }

    private companion object {
        const val SPEED_BUCKET_KMH = 5f
    }
}
