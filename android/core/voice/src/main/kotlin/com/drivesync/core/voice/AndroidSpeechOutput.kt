package com.drivesync.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Locale

/**
 * Platform TTS fallback with audio-focus handling and a strict FIFO queue.
 * Focus loss (e.g. navigation guidance) pauses and re-speaks the interrupted sentence
 * when focus returns.
 */
class AndroidSpeechOutput(context: Context) : SpeechOutput {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val queue = ArrayDeque<String>()
    private var current: String? = null
    private var ready = false
    private val mutableState = MutableStateFlow(SpeechState.IDLE)
    override val state: StateFlow<SpeechState> = mutableState.asStateFlow()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(::onFocusChange)
        .build()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            mutableState.value = SpeechState.UNAVAILABLE
            return@TextToSpeech
        }
        tts.language = Locale.KOREAN
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(progress)
        synchronized(queue) { if (current == null) speakNext() }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            synchronized(queue) {
                if (mutableState.value == SpeechState.SPEAKING) {
                    current = null
                    speakNext()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onDone(utteranceId)
    }

    override fun enqueue(utterances: List<String>) {
        synchronized(queue) {
            utterances.filter { it.isNotBlank() }.forEach(queue::addLast)
            if (ready && current == null && mutableState.value != SpeechState.PAUSED_FOR_NAVIGATION) speakNext()
        }
    }

    override fun stop() {
        synchronized(queue) {
            queue.clear()
            current = null
            tts.stop()
            audioManager.abandonAudioFocusRequest(focusRequest)
            if (mutableState.value != SpeechState.UNAVAILABLE) mutableState.value = SpeechState.IDLE
        }
    }

    override fun release() {
        stop()
        tts.shutdown()
    }

    private fun speakNext() {
        val next = queue.pollFirst()
        if (next == null) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            mutableState.value = SpeechState.IDLE
            return
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            queue.addFirst(next)
            mutableState.value = SpeechState.PAUSED_FOR_NAVIGATION
            return
        }
        current = next
        mutableState.value = SpeechState.SPEAKING
        tts.speak(next, TextToSpeech.QUEUE_FLUSH, null, next.hashCode().toString())
    }

    private fun onFocusChange(change: Int) {
        synchronized(queue) {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    mutableState.value = SpeechState.PAUSED_FOR_NAVIGATION
                    tts.stop()
                    current?.let(queue::addFirst)
                    current = null
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (mutableState.value == SpeechState.PAUSED_FOR_NAVIGATION) speakNext()
            }
        }
    }
}
