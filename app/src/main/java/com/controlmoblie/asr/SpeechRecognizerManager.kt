package com.controlmoblie.asr

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.LogLevel
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.IOException

sealed class AsrEvent {
    data class PartialResult(val text: String) : AsrEvent()
    data class FinalResult(val text: String) : AsrEvent()
    data class Error(val message: String) : AsrEvent()
    object Ready : AsrEvent()
}

class SpeechRecognizerManager(private val modelPath: String) {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var currentRecognizer: Recognizer? = null
    private val _events = Channel<AsrEvent>(Channel.CONFLATED)
    val events: Flow<AsrEvent> = _events.receiveAsFlow()
    private var isListening = false

    fun init(): Boolean {
        return try {
            org.vosk.LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(modelPath)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load Vosk model", e)
            _events.trySend(AsrEvent.Error("模型加载失败: ${e.message}"))
            false
        }
    }

    fun startListening() {
        if (isListening) {
            stopListening()
        }
        val m = model ?: run {
            _events.trySend(AsrEvent.Error("模型未加载"))
            return
        }
        try {
            currentRecognizer?.close()
            val recognizer = Recognizer(m, SAMPLE_RATE)
            currentRecognizer = recognizer
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(partialResult: String?) {
                    try {
                        val json = JSONObject(partialResult ?: return)
                        val partial = json.optString("partial", "")
                        if (partial.isNotBlank()) {
                            _events.trySend(AsrEvent.PartialResult(partial))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse partial result", e)
                    }
                }

                override fun onResult(result: String?) {
                    try {
                        val json = JSONObject(result ?: return)
                        val text = json.optString("text", "")
                        if (text.isNotBlank()) {
                            _events.trySend(AsrEvent.FinalResult(text))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse result", e)
                    }
                }

                override fun onFinalResult(partialResult: String?) {
                }

                override fun onError(e: Exception?) {
                    _events.trySend(AsrEvent.Error(e?.message ?: "识别错误"))
                }

                override fun onTimeout() {
                    _events.trySend(AsrEvent.Error("识别超时"))
                }
            })
            isListening = true
            _events.trySend(AsrEvent.Ready)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _events.trySend(AsrEvent.Error("启动识别失败: ${e.message}"))
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
        } catch (_: Exception) {}
        speechService = null
        currentRecognizer?.close()
        currentRecognizer = null
        isListening = false
    }

    fun release() {
        stopListening()
        model?.close()
        model = null
    }

    companion object {
        private const val TAG = "SpeechRecognizerManager"
        private const val SAMPLE_RATE = 16000f
    }
}