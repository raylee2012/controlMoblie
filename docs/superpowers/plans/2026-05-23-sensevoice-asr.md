# SenseVoiceSmall ASR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Vosk ASR with sherpa-onnx + SenseVoiceSmall model for better accented Mandarin recognition.

**Architecture:** New `SenseVoiceModelManager` (download), rewritten `SpeechRecognizerManager` (sherpa-onnx OnlineRecognizer + AudioRecord polling loop), minor edits to `VoiceControlService` and `MainActivity`. AsrEvent types unchanged.

**Tech Stack:** Kotlin, sherpa-onnx v1.13.2 AAR (already integrated), Android AudioRecord, Apache Commons Compress (already integrated)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `asr/SenseVoiceModelManager.kt` | Create | Download & extract SenseVoiceSmall model |
| `asr/SpeechRecognizerManager.kt` | Rewrite | sherpa-onnx OnlineRecognizer + AudioRecord |
| `service/VoiceControlService.kt` | Modify | Point model init to SenseVoiceModelManager |
| `MainActivity.kt` | Modify | SenseVoice download UI section |

---

### Task 1: Create SenseVoiceModelManager

**Files:**
- Create: `app/src/main/java/com/controlmoblie/asr/SenseVoiceModelManager.kt`

- [ ] **Step 1: Write SenseVoiceModelManager**

Create `app/src/main/java/com/controlmoblie/asr/SenseVoiceModelManager.kt`:

```kotlin
package com.controlmoblie.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

object SenseVoiceModelManager {

    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-small-zh-en-ja-ko-yue-2025-03-27.tar.bz2"
    private const val MODEL_DIR_NAME = "sense-voice-small-onnx"
    private const val TEMP_FILE_NAME = "sense-voice-small.tar.bz2.tmp"
    private const val MODEL_FILENAME = "model.onnx"

    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        val modelFile = File(modelDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun getModelPath(context: Context): String {
        return File(context.filesDir, MODEL_DIR_NAME).absolutePath
    }

    suspend fun downloadAndExtract(context: Context, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val modelDir = File(context.filesDir, MODEL_DIR_NAME)
            if (isModelReady(context)) {
                withContext(Dispatchers.Main) { onProgress(1f) }
                return@withContext true
            }

            val tmpFile = File(context.cacheDir, TEMP_FILE_NAME)
            try {
                withContext(Dispatchers.Main) { onProgress(0f) }
                val url = URL(MODEL_URL)
                val connection = url.openConnection().apply {
                    connectTimeout = 60000
                    readTimeout = 120000
                }
                connection.connect()
                val fileLength = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (fileLength > 0) {
                                withContext(Dispatchers.Main) {
                                    onProgress(totalRead.toFloat() / fileLength * 0.7f)
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) { onProgress(0.75f) }
                if (modelDir.exists()) modelDir.deleteRecursively()
                modelDir.mkdirs()

                extractTarBz2(tmpFile, modelDir)

                withContext(Dispatchers.Main) { onProgress(1f) }
                isModelReady(context)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                tmpFile.delete()
            }
        }
    }

    private fun extractTarBz2(tarBz2File: File, destDir: File) {
        BufferedInputStream(tarBz2File.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name
                            val slashIdx = entryName.indexOf('/')
                            val relativePath = if (slashIdx >= 0) entryName.substring(slashIdx + 1) else entryName
                            if (relativePath.isBlank()) {
                                entry = tarIn.nextTarEntry
                                continue
                            }
                            val destFile = File(destDir, relativePath)
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/controlmoblie/asr/SenseVoiceModelManager.kt
git commit -m "feat: add SenseVoiceModelManager for ASR model download"
```

---

### Task 2: Rewrite SpeechRecognizerManager for sherpa-onnx

**Files:**
- Rewrite: `app/src/main/java/com/controlmoblie/asr/SpeechRecognizerManager.kt`

- [ ] **Step 1: Rewrite SpeechRecognizerManager with sherpa-onnx OnlineRecognizer**

Replace entire file content:

```kotlin
package com.controlmoblie.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineSenseVoiceModelConfig
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
            val modelConfig = OnlineModelConfig(
                senseVoice = OnlineSenseVoiceModelConfig(
                    model = "$modelPath/model.onnx",
                ),
                tokens = "$modelPath/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                debug = false,
            )
            val featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80,
            )
            val config = OnlineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                enableEndpoint = true,
                rule1MinTrailingSilence = 2.4f,
                rule2MinTrailingSilence = 1.2f,
                rule3MinUtteranceLength = 20.0f,
            )
            recognizer = OnlineRecognizer(config)
            Log.d(TAG, "OnlineRecognizer initialized for SenseVoiceSmall")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SenseVoice model", e)
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
                name = "SenseVoice-capture"
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
```

- [ ] **Step 2: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/controlmoblie/asr/SpeechRecognizerManager.kt
git commit -m "feat: rewrite SpeechRecognizerManager with sherpa-onnx OnlineRecognizer"
```

---

### Task 3: Update VoiceControlService to use SenseVoice

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/service/VoiceControlService.kt`

- [ ] **Step 1: Replace Vosk import and model references**

Remove import:
```kotlin
import com.controlmoblie.asr.VoskModelManager
```

Add import:
```kotlin
import com.controlmoblie.asr.SenseVoiceModelManager
```

In `initAndStart()`, change:
```kotlin
if (!VoskModelManager.isModelReady(this)) {
    overlay.updateState(OverlayState.ERROR, result = "语音模型未下载")
    return
}
val modelPath = VoskModelManager.getModelPath(this)
```

To:
```kotlin
if (!SenseVoiceModelManager.isModelReady(this)) {
    overlay.updateState(OverlayState.ERROR, result = "语音模型未下载")
    return
}
val modelPath = SenseVoiceModelManager.getModelPath(this)
```

- [ ] **Step 2: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/controlmoblie/service/VoiceControlService.kt
git commit -m "feat: switch VoiceControlService from Vosk to SenseVoiceSmall ASR"
```

---

### Task 4: Update MainActivity with SenseVoice download UI

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: Add SenseVoice download section**

Add import:
```kotlin
import com.controlmoblie.asr.SenseVoiceModelManager
```

Add state variables in `ControlScreen()` after existing `isDownloading`:
```kotlin
var senseVoiceModelReady by remember { mutableStateOf(SenseVoiceModelManager.isModelReady(this@MainActivity)) }
var senseVoiceDownloadProgress by remember { mutableStateOf(-1f) }
var senseVoiceDownloading by remember { mutableStateOf(false) }
```

Add UI section BEFORE the existing Vosk download UI (SenseVoice is primary):

```kotlin
if (senseVoiceDownloading) {
    LinearProgressIndicator(
        progress = { if (senseVoiceDownloadProgress >= 0f) senseVoiceDownloadProgress else 0f },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        if (senseVoiceDownloadProgress >= 0f) "下载语音识别模型中... ${(senseVoiceDownloadProgress * 100).toInt()}%"
        else "准备下载...",
        style = MaterialTheme.typography.bodySmall
    )
}

if (!senseVoiceModelReady && !senseVoiceDownloading) {
    OutlinedButton(
        onClick = {
            senseVoiceDownloading = true
            senseVoiceDownloadProgress = 0f
            this@MainActivity.lifecycleScope.launch(Dispatchers.Main) {
                val success = SenseVoiceModelManager.downloadAndExtract(this@MainActivity) { progress ->
                    senseVoiceDownloadProgress = progress
                }
                senseVoiceModelReady = success
                senseVoiceDownloading = false
                senseVoiceDownloadProgress = -1f
            }
        },
        enabled = hasAudio && !senseVoiceDownloading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("下载语音识别模型 (~230MB)")
    }
}

if (senseVoiceModelReady && !senseVoiceDownloading) {
    Text("语音识别 ✓", color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall)
}
```

Update the start button condition from `voskModelReady` to `senseVoiceModelReady`:
```kotlin
if (hasOverlay && hasAudio && senseVoiceModelReady) {
    Button(
        onClick = onStartService,
        ...
    ) {
        Text("启动语音控制")
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build full APK**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/controlmoblie/MainActivity.kt
git commit -m "feat: add SenseVoiceSmall ASR download UI, primary over Vosk"
```

---

### Task 5: Full integration build and test

- [ ] **Step 1: Build and verify**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit any fixes if needed**

---

## Self-Review

1. **Spec coverage**: All requirements covered — model download (Task 1), engine rewrite (Task 2), service wiring (Task 3), UI (Task 4), integration test (Task 5).
2. **Placeholder scan**: No TBD/TODO. All code shown in full.
3. **Type consistency**: `SpeechRecognizerManager` keeps same public API (`init()`, `startListening()`, `stopListening()`, `release()`, `events`). `AsrEvent` sealed class unchanged. `VoiceControlService` only changes the model manager import and check.
