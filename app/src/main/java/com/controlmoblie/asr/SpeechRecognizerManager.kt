package com.controlmoblie.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed class AsrEvent {
    data class PartialResult(val text: String) : AsrEvent()
    data class FinalResult(val text: String) : AsrEvent()
    data class Error(val message: String) : AsrEvent()
    object Ready : AsrEvent()
}

class SpeechRecognizerManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private val _events = Channel<AsrEvent>(Channel.CONFLATED)
    val events: Flow<AsrEvent> = _events.receiveAsFlow()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.trySend(AsrEvent.Ready)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "说话超时"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                else -> "识别错误: $error"
            }
            _events.trySend(AsrEvent.Error(msg))
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!texts.isNullOrEmpty()) {
                _events.trySend(AsrEvent.FinalResult(texts[0]))
            } else {
                _events.trySend(AsrEvent.Error("未获取到识别结果"))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!texts.isNullOrEmpty()) {
                _events.trySend(AsrEvent.PartialResult(texts[0]))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        stopListening()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
    }
}
