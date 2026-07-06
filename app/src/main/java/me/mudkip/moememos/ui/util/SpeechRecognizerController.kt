package me.mudkip.moememos.ui.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android [SpeechRecognizer] for use in Compose.
 *
 * Lifecycle: call [startListening] to begin, [stopListening] to finish and
 * collect the final transcript. Call [destroy] when the host is disposed.
 *
 * The recognizer streams partial results via [partialResults] so the UI can
 * show live transcription. On stop or end-of-speech the final transcript is
 * emitted via [finalResult].
 */
class SpeechRecognizerController(private val context: Context) {

    enum class State { IDLE, LISTENING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()

    private val _finalResult = MutableStateFlow<String?>(null)
    val finalResult: StateFlow<String?> = _finalResult.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = State.LISTENING
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            // Recognizer will transition to RESULTS automatically.
        }

        override fun onError(error: Int) {
            _state.value = State.ERROR
            Log.w(TAG, "SpeechRecognizer error: ${errorCodeToString(error)}")
            // Some errors (no-speech, silence) should not block the user.
            // Emit whatever partial we had as final so the user keeps their words.
            val partial = _partialResults.value
            if (partial.isNotEmpty()) {
                _finalResult.value = partial
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            _partialResults.value = ""
            _finalResult.value = text.ifEmpty { _partialResults.value }
            _state.value = State.IDLE
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotEmpty()) {
                _partialResults.value = text
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun startListening() {
        if (!isAvailable) {
            _state.value = State.ERROR
            return
        }
        // Reset state for a fresh session.
        _partialResults.value = ""
        _finalResult.value = null

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Default to device locale for better accuracy.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _state.value = State.LISTENING
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        // If recognizer never emitted final results, treat partial as final.
        if (_finalResult.value == null && _partialResults.value.isNotEmpty()) {
            _finalResult.value = _partialResults.value
            _partialResults.value = ""
        }
        _state.value = State.IDLE
    }

    fun cancel() {
        recognizer?.cancel()
        _partialResults.value = ""
        _finalResult.value = null
        _state.value = State.IDLE
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _state.value = State.IDLE
    }

    /** Consume and clear the final result. */
    fun consumeFinalResult(): String? {
        val v = _finalResult.value
        _finalResult.value = null
        return v
    }

    private fun errorCodeToString(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient permissions"
        else -> "unknown($code)"
    }

    companion object {
        private const val TAG = "SpeechRecognizer"
    }
}
