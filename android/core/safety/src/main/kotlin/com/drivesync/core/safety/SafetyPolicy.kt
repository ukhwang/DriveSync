package com.drivesync.core.safety

import com.drivesync.core.model.Gear
import com.drivesync.core.model.Signal
import com.drivesync.core.model.UxRestriction
import com.drivesync.core.model.VehicleContext

/** Every user-facing capability that the policy gates. UI must ask before rendering any of them. */
enum class Feature {
    DESTINATION_ETA,
    SESSION_STATUS,
    STOP_AUDIO,
    ONE_SHOT_VOICE_MEMO,
    ONEWAY_TTS_BRIEFING,
    BRIEFING_CARD,
    CHEAT_SHEET,
    TEXT_INPUT,
    LIST_BROWSING,
    AI_CONVERSATION,
    DEBRIEF,
    ACTION_REVIEW,
    ACTION_APPROVAL,
}

enum class DrivingMode {
    /** Distraction optimization required: minimum screen. */
    LIMITED,

    /** Restriction lifted: full work screen. */
    PARKED_WORK,
}

sealed interface FailSafeReason {
    data class SignalUnavailable(val reason: String) : FailSafeReason
    data object SignalUnsupported : FailSafeReason
    data class SignalStale(val ageMillis: Long) : FailSafeReason
    data object NoSignalYet : FailSafeReason
}

/** Platform permissions discovered at runtime (기획서: 공모전 환경 권한 확인 후 적용). */
data class PlatformCapabilities(
    val ttsPermitted: Boolean,
    val sttPermitted: Boolean,
    /** Whether the platform's DO rules allow a one-shot STT capture while driving. */
    val sttAllowedWhileDriving: Boolean,
) {
    companion object {
        val NONE = PlatformCapabilities(ttsPermitted = false, sttPermitted = false, sttAllowedWhileDriving = false)
    }
}

data class PolicyState(
    val mode: DrivingMode,
    val failSafe: FailSafeReason?,
    /** Gear confirmed in P, or gear signal unsupported (fallback: CarUxRestrictions only). */
    val parkConfirmed: Boolean,
    val allowed: Set<Feature>,
) {
    fun allows(feature: Feature): Boolean = feature in allowed
}

sealed interface PolicyDecision {
    data object Allowed : PolicyDecision
    data class Denied(val feature: Feature, val reason: String) : PolicyDecision
}

/**
 * Safety & Policy Controller (기획서 1-①, 3-③-2).
 *
 * Rules:
 * 1. CarUxRestrictions is the single source of truth. Gear = P alone never unlocks anything.
 * 2. Fail-Safe: missing, unsupported, unavailable or stale restriction signal => LIMITED.
 * 3. LIMITED shows destination/ETA/session status/memo button only; no text input,
 *    no list browsing, no AI conversation.
 * 4. Cheat sheet additionally needs P gear, unless the vehicle does not support the gear signal.
 */
class SafetyPolicy(
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
) {

    fun evaluate(
        context: VehicleContext?,
        capabilities: PlatformCapabilities,
        nowMillis: Long,
    ): PolicyState {
        val failSafe = failSafeReason(context, nowMillis)
        val restriction = (context?.uxRestriction as? Signal.Available)?.value
        val mode = if (failSafe == null && restriction == UxRestriction.UNRESTRICTED) {
            DrivingMode.PARKED_WORK
        } else {
            DrivingMode.LIMITED
        }
        val parkConfirmed = mode == DrivingMode.PARKED_WORK && when (val gear = context?.gear) {
            is Signal.Available -> gear.value == Gear.PARK
            Signal.Unsupported -> true
            is Signal.Unavailable, null -> false
        }
        return PolicyState(
            mode = mode,
            failSafe = failSafe,
            parkConfirmed = parkConfirmed,
            allowed = allowedFeatures(mode, parkConfirmed, capabilities),
        )
    }

    fun check(state: PolicyState, feature: Feature): PolicyDecision =
        if (state.allows(feature)) {
            PolicyDecision.Allowed
        } else {
            PolicyDecision.Denied(feature, denialReason(state, feature))
        }

    private fun failSafeReason(context: VehicleContext?, nowMillis: Long): FailSafeReason? {
        if (context == null) return FailSafeReason.NoSignalYet
        return when (val signal = context.uxRestriction) {
            is Signal.Unavailable -> FailSafeReason.SignalUnavailable(signal.reason)
            Signal.Unsupported -> FailSafeReason.SignalUnsupported
            is Signal.Available -> {
                val age = nowMillis - context.observedAtMillis
                if (age > staleAfterMillis) FailSafeReason.SignalStale(age) else null
            }
        }
    }

    private fun allowedFeatures(
        mode: DrivingMode,
        parkConfirmed: Boolean,
        caps: PlatformCapabilities,
    ): Set<Feature> = buildSet {
        add(Feature.DESTINATION_ETA)
        add(Feature.SESSION_STATUS)
        add(Feature.STOP_AUDIO)
        if (caps.ttsPermitted) add(Feature.ONEWAY_TTS_BRIEFING)
        when (mode) {
            DrivingMode.LIMITED -> {
                if (caps.sttPermitted && caps.sttAllowedWhileDriving) add(Feature.ONE_SHOT_VOICE_MEMO)
            }
            DrivingMode.PARKED_WORK -> {
                if (caps.sttPermitted) add(Feature.ONE_SHOT_VOICE_MEMO)
                add(Feature.BRIEFING_CARD)
                add(Feature.TEXT_INPUT)
                add(Feature.LIST_BROWSING)
                add(Feature.AI_CONVERSATION)
                add(Feature.DEBRIEF)
                add(Feature.ACTION_REVIEW)
                add(Feature.ACTION_APPROVAL)
                if (parkConfirmed) add(Feature.CHEAT_SHEET)
            }
        }
    }

    private fun denialReason(state: PolicyState, feature: Feature): String = when {
        state.failSafe != null -> "차량 상태를 확인할 수 없어 주행 제한 상태로 간주합니다"
        state.mode == DrivingMode.LIMITED -> "주행 중에는 사용할 수 없습니다"
        feature == Feature.CHEAT_SHEET && !state.parkConfirmed -> "P단 확인 후 표시됩니다"
        else -> "현재 플랫폼 권한으로 사용할 수 없습니다"
    }

    companion object {
        const val DEFAULT_STALE_AFTER_MILLIS: Long = 5_000
    }
}
