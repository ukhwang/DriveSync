package com.drivesync.core.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Voice output port. Production adapters: Pleos TextToSpeech SDK (once the contest SDK is
 * provisioned) and [AndroidSpeechOutput] (platform TTS) as the emulator fallback.
 *
 * Contract (기획서 1-①, 예외 처리):
 * - One-way only. Never asks the driver anything.
 * - [stop] takes effect immediately (운전자 우선권).
 * - Yields audio focus to navigation prompts and pauses.
 * - New scripts arriving while speaking are queued, never overlapped.
 */
interface SpeechOutput {
    val state: StateFlow<SpeechState>
    fun enqueue(utterances: List<String>)
    fun stop()
    fun release()
}

enum class SpeechState { IDLE, SPEAKING, PAUSED_FOR_NAVIGATION, UNAVAILABLE }

/**
 * Voice input port: a single capture, no dialogue. Ends after a short silence or at [maxSeconds]
 * (default 2 minutes; the app passes the configured `DRIVESYNC_MEMO_MAX_SECONDS`).
 */
interface SpeechInput {
    suspend fun captureOnce(maxSeconds: Int = DEFAULT_MAX_SECONDS): CaptureResult

    companion object {
        const val DEFAULT_MAX_SECONDS = 120
    }
}

sealed interface CaptureResult {
    data class Text(val text: String) : CaptureResult
    data object NothingHeard : CaptureResult
    data class Unavailable(val reason: String) : CaptureResult
}
