# SenseVoiceSmall ASR 实现计划

> **面向 agentic worker：** 必须加载子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 来逐任务实现本计划。各步骤使用 checkbox（`- [ ]`）语法进行跟踪。

**目标：** 将 Vosk ASR 替换为 sherpa-onnx + SenseVoiceSmall 模型，以提升带口音普通话的识别效果。

**架构：** 新建 `SenseVoiceModelManager`（下载）、重写 `SpeechRecognizerManager`（sherpa-onnx OnlineRecognizer + AudioRecord 轮询循环）、小幅修改 `VoiceControlService` 和 `MainActivity`。AsrEvent 类型不变。

**技术栈：** Kotlin, sherpa-onnx v1.13.2 AAR（已集成）, Android AudioRecord, Apache Commons Compress（已集成）

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `asr/SenseVoiceModelManager.kt` | 新建 | 下载并解压 SenseVoiceSmall 模型 |
| `asr/SpeechRecognizerManager.kt` | 重写 | sherpa-onnx OnlineRecognizer + AudioRecord |
| `service/VoiceControlService.kt` | 修改 | 将模型初始化指向 SenseVoiceModelManager |
| `MainActivity.kt` | 修改 | SenseVoice 下载 UI 区域 |

---

### 任务 1：创建 SenseVoiceModelManager

**涉及文件：**
- 新建：`app/src/main/java/com/controlmoblie/asr/SenseVoiceModelManager.kt`

- [ ] **步骤 1：编写 SenseVoiceModelManager**

创建 `app/src/main/java/com/controlmoblie/asr/SenseVoiceModelManager.kt`：

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

- [ ] **步骤 2：验证编译通过**

执行：`.\gradlew :app:compileDebugKotlin`
预期结果：BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```
git add app/src/main/java/com/controlmoblie/asr/SenseVoiceModelManager.kt
git commit -m "feat: 添加 SenseVoiceModelManager 用于 ASR 模型下载"
```

---

### 任务 2：重写 SpeechRecognizerManager，改用 sherpa-onnx

**涉及文件：**
- 重写：`app/src/main/java/com/controlmoblie/asr/SpeechRecognizerManager.kt`

- [ ] **步骤 1：使用 sherpa-onnx OnlineRecognizer 重写 SpeechRecognizerManager**

替换整个文件内容：

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
            Log.d(TAG, "OnlineRecognizer 已初始化，使用 SenseVoiceSmall")
            true
        } catch (e: Exception) {
            Log.e(TAG, "加载 SenseVoice 模型失败", e)
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
            Log.e(TAG, "启动识别失败", e)
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

- [ ] **步骤 2：验证编译通过**

执行：`.\gradlew :app:compileDebugKotlin`
预期结果：BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```
git add app/src/main/java/com/controlmoblie/asr/SpeechRecognizerManager.kt
git commit -m "feat: 使用 sherpa-onnx OnlineRecognizer 重写 SpeechRecognizerManager"
```

---

### 任务 3：更新 VoiceControlService 使用 SenseVoice

**涉及文件：**
- 修改：`app/src/main/java/com/controlmoblie/service/VoiceControlService.kt`

- [ ] **步骤 1：替换 Vosk 导入和模型引用**

移除 import：
```kotlin
import com.controlmoblie.asr.VoskModelManager
```

添加 import：
```kotlin
import com.controlmoblie.asr.SenseVoiceModelManager
```

在 `initAndStart()` 中，将：
```kotlin
if (!VoskModelManager.isModelReady(this)) {
    overlay.updateState(OverlayState.ERROR, result = "语音模型未下载")
    return
}
val modelPath = VoskModelManager.getModelPath(this)
```

改为：
```kotlin
if (!SenseVoiceModelManager.isModelReady(this)) {
    overlay.updateState(OverlayState.ERROR, result = "语音模型未下载")
    return
}
val modelPath = SenseVoiceModelManager.getModelPath(this)
```

- [ ] **步骤 2：验证编译通过**

执行：`.\gradlew :app:compileDebugKotlin`
预期结果：BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```
git add app/src/main/java/com/controlmoblie/service/VoiceControlService.kt
git commit -m "feat: 将 VoiceControlService 从 Vosk 切换到 SenseVoiceSmall ASR"
```

---

### 任务 4：更新 MainActivity，添加 SenseVoice 下载 UI

**涉及文件：**
- 修改：`app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **步骤 1：添加 SenseVoice 下载区域**

添加 import：
```kotlin
import com.controlmoblie.asr.SenseVoiceModelManager
```

在 `ControlScreen()` 中，在现有 `isDownloading` 之后添加状态变量：
```kotlin
var senseVoiceModelReady by remember { mutableStateOf(SenseVoiceModelManager.isModelReady(this@MainActivity)) }
var senseVoiceDownloadProgress by remember { mutableStateOf(-1f) }
var senseVoiceDownloading by remember { mutableStateOf(false) }
```

在现有 Vosk 下载 UI 之前添加 UI 区域（SenseVoice 为主要）：

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

将启动按钮的判定条件从 `voskModelReady` 改为 `senseVoiceModelReady`：
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

- [ ] **步骤 2：验证编译通过**

执行：`.\gradlew :app:compileDebugKotlin`
预期结果：BUILD SUCCESSFUL

- [ ] **步骤 3：构建完整 APK**

执行：`.\gradlew :app:assembleDebug`
预期结果：BUILD SUCCESSFUL

- [ ] **步骤 4：提交**

```
git add app/src/main/java/com/controlmoblie/MainActivity.kt
git commit -m "feat: 添加 SenseVoiceSmall ASR 下载 UI，优先于 Vosk"
```

---

### 任务 5：完整集成构建与测试

- [ ] **步骤 1：构建并验证**

执行：`.\gradlew :app:assembleDebug`
预期结果：BUILD SUCCESSFUL

- [ ] **步骤 2：如有修复需提交**

---

## 自查

1. **规范覆盖**：所有需求已覆盖 — 模型下载（任务 1）、引擎重写（任务 2）、服务接线（任务 3）、UI（任务 4）、集成测试（任务 5）。
2. **占位符扫描**：无 TBD/TODO。所有代码均完整展示。
3. **类型一致性**：`SpeechRecognizerManager` 保持相同的公开 API（`init()`、`startListening()`、`stopListening()`、`release()`、`events`）。`AsrEvent` sealed class 不变。`VoiceControlService` 仅更换 model manager 的导入和检查逻辑。
