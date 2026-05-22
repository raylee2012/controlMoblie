package com.controlmoblie.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import com.k2fsa.sherpa.onnx.OnlineCtcFstDecoderConfig
import com.k2fsa.sherpa.onnx.OnlineLMConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed class AsrEvent {
    data class PartialResult(val text: String) : AsrEvent()
    data class FinalResult(val text: String) : AsrEvent()
    data class Error(val message: String) : AsrEvent()
    object Ready : AsrEvent()
}

class SpeechRecognizerManager(private val modelPath: String) {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val _events = Channel<AsrEvent>(Channel.CONFLATED)
    val events: Flow<AsrEvent> = _events.receiveAsFlow()
    private var isListening = false

    fun init(): Boolean {
        return try {
            val dir = File(modelPath)
            val onnxFiles = dir.listFiles { f -> f.name.endsWith(".onnx") }
            if (onnxFiles.isNullOrEmpty()) {
                Log.e(TAG, "No .onnx files found in $modelPath")
                _events.trySend(AsrEvent.Error("模型文件缺失"))
                return false
            }
            val encoder = onnxFiles.find { it.name.contains("encoder", ignoreCase = true) }?.absolutePath
                ?: onnxFiles.first().absolutePath
            val decoder = onnxFiles.find { it.name.contains("decoder", ignoreCase = true) }?.absolutePath
                ?: onnxFiles.first().absolutePath
            val joiner = onnxFiles.find { it.name.contains("joiner", ignoreCase = true) }?.absolutePath
                ?: onnxFiles.first().absolutePath
            Log.d(TAG, "encoder=$encoder, decoder=$decoder, joiner=$joiner")

            val featConfig = FeatureConfig(SAMPLE_RATE, 80, 0.0f)

            val modelConfig = OnlineModelConfig().apply {
                transducer = OnlineTransducerModelConfig(encoder, decoder, joiner)
                tokens = "$modelPath/tokens.txt"
                numThreads = 2
                provider = "cpu"
                debug = false
            }

            val rule1 = EndpointRule(false, 2.4f, 0.0f)
            val rule2 = EndpointRule(true, 1.2f, 0.0f)
            val rule3 = EndpointRule(false, 0.0f, 20.0f)
            val endpointConfig = EndpointConfig(rule1, rule2, rule3)

            val config = OnlineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                lmConfig = OnlineLMConfig(),
                ctcFstDecoderConfig = OnlineCtcFstDecoderConfig(),
                hr = HomophoneReplacerConfig(),
                endpointConfig = endpointConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
                hotwordsFile = "",
                hotwordsScore = 1.5f,
                ruleFsts = "",
                ruleFars = "",
                blankPenalty = 0.0f
            )

            recognizer = OnlineRecognizer(config = config)
            Log.d(TAG, "OnlineRecognizer initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _events.trySend(AsrEvent.Error("模型加载失败: ${e.message}"))
            false
        }
    }

    fun startListening() {
        if (isListening) stopListening()
        val rec = recognizer ?: run {
            _events.trySend(AsrEvent.Error("模型未加载"))
            return
        }

        try {
            stream = rec.createStream()

            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, SAMPLE_RATE / 5)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _events.trySend(AsrEvent.Error("麦克风初始化失败"))
                return
            }

            audioRecord?.startRecording()
            isListening = true
            _events.trySend(AsrEvent.Ready)

            captureThread = Thread {
                val buffer = ShortArray(bufferSize / 2)
                var lastPartialText = ""

                while (isListening) {
                    val len = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (len <= 0) continue

                    val samples = FloatArray(len) { buffer[it] / 32768f }
                    val s = stream ?: break
                    s.acceptWaveform(samples, SAMPLE_RATE)

                    while (rec.isReady(s)) {
                        rec.decode(s)
                    }

                    val result = rec.getResult(s)
                    val text = result.text.trim()

                    if (text.isNotEmpty() && rec.isEndpoint(s)) {
                        rec.reset(s)
                        _events.trySend(AsrEvent.FinalResult(text))
                        lastPartialText = ""
                    } else if (text.isNotEmpty() && text != lastPartialText) {
                        _events.trySend(AsrEvent.PartialResult(text))
                        lastPartialText = text
                    }
                }
            }.apply {
                name = "Asr-capture"
                priority = Thread.MAX_PRIORITY
                start()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _events.trySend(AsrEvent.Error("启动识别失败: ${e.message}"))
        }
    }

    fun stopListening() {
        isListening = false
        captureThread?.join(500)
        captureThread = null
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        stream?.release()
        stream = null
    }

    fun release() {
        stopListening()
        recognizer?.release()
        recognizer = null
    }

    companion object {
        private const val TAG = "SpeechRecognizerManager"
        private const val SAMPLE_RATE = 16000
    }
}
