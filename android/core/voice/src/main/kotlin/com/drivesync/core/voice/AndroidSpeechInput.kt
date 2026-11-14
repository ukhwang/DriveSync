package com.drivesync.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot STT fallback using the platform recognizer. Pleos SpeechToText replaces it once
 * provisioned. Exactly one utterance; the caller plays only a confirmation tone afterwards.
 */
class AndroidSpeechInput(private val context: Context) : SpeechInput {

    fun isAvailable(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            SpeechRecognizer.isRecognitionAvailable(context)

    override suspend fun captureOnce(maxSeconds: Int): CaptureResult {
        if (!isAvailable()) return CaptureResult.Unavailable("음성 인식 권한 또는 엔진이 없습니다")
        return withContext(Dispatchers.Main) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            try {
                withTimeoutOrNull(maxSeconds * 1_000L + GRACE_MILLIS) { listen(recognizer, maxSeconds) }
                    ?: CaptureResult.NothingHeard
            } finally {
                recognizer.destroy()
            }
        }
    }

    private suspend fun listen(recognizer: SpeechRecognizer, maxSeconds: Int): CaptureResult =
        suspendCancellableCoroutine { cont ->
            val limiter = Handler(Looper.getMainLooper())
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        limiter.removeCallbacksAndMessages(null)
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (cont.isActive) cont.resume(if (text.isNullOrBlank()) CaptureResult.NothingHeard else CaptureResult.Text(text))
                    }

                    override fun onError(error: Int) {
                        limiter.removeCallbacksAndMessages(null)
                        if (!cont.isActive) return
                        cont.resume(
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> CaptureResult.NothingHeard
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> CaptureResult.Unavailable("마이크 권한이 없습니다")
                                else -> CaptureResult.Unavailable("음성 인식 오류($error)")
                            },
                        )
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
            // End on silence (the memo is short when the driver is), and cap the length with
            // stopListening() so whatever was said up to the limit is still returned.
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_END_MILLIS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_END_MILLIS)
            limiter.postDelayed({ recognizer.stopListening() }, maxSeconds * 1_000L)
            recognizer.startListening(intent)
            cont.invokeOnCancellation {
                limiter.removeCallbacksAndMessages(null)
                recognizer.cancel()
            }
        }

    private companion object {
        const val GRACE_MILLIS = 3_000L
        const val SILENCE_END_MILLIS = 3_000L
    }
}
