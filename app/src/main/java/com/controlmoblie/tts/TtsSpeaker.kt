package com.controlmoblie.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TtsSpeaker(private val context: Context) {

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isSpeaking = false
    private var isInitialized = false

    companion object {
        private const val TAG = "TtsSpeaker"
        private const val SPEAKER_ID = 0
        private const val SPEED = 1.0f
    }

    fun init(): Boolean {
        if (!TtsModelManager.isModelReady(context)) {
            Log.w(TAG, "TTS model not ready")
            return false
        }
        return try {
            val modelDir = TtsModelManager.getModelDir(context)
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$modelDir/echo.onnx",
                        lexicon = "$modelDir/lexicon.txt",
                        tokens = "$modelDir/tokens.txt",
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst",
            )
            tts = OfflineTts(config = config)
            isInitialized = true
            Log.d(TAG, "TTS initialized, sampleRate=${tts?.sampleRate()}, speakers=${tts?.numSpeakers()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TTS init failed", e)
            tts = null
            false
        }
    }

    suspend fun speak(text: String, onDone: () -> Unit) {
        if (isSpeaking) {
            Log.d(TAG, "Already speaking, dropping: $text")
            onDone()
            return
        }
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "TTS not initialized, skipping speak")
            onDone()
            return
        }
        if (text.isBlank()) {
            onDone()
            return
        }
        isSpeaking = true
        withContext(Dispatchers.IO) {
            try {
                val sampleRate = engine.sampleRate()
                val bufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setSampleRate(sampleRate)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                track.play()
                var totalFrames = 0

                engine.generateWithCallback(text, SPEAKER_ID, SPEED) { samples ->
                    if (!isSpeaking) return@generateWithCallback 0
                    track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    totalFrames += samples.size
                    if (isSpeaking) 1 else 0
                }

                // Wait for playback: calculate duration from total frames instead of polling
                val playDurationMs = totalFrames * 1000L / sampleRate + 200
                Thread.sleep(playDurationMs)

                track.stop()
                track.release()
                audioTrack = null
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak error", e)
                audioTrack?.release()
                audioTrack = null
            } finally {
                isSpeaking = false
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    fun stop() {
        isSpeaking = false
        audioTrack?.stop()
    }

    fun release() {
        isSpeaking = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        tts?.release()
        tts = null
        isInitialized = false
    }

    val isModelReady: Boolean get() = TtsModelManager.isModelReady(context) && isInitialized
}
