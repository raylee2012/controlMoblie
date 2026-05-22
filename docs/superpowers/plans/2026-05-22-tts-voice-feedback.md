# TTS 语音反馈实现计划

> **自动化执行者须知:** 必需子技能: 使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 来逐任务实现本计划。步骤使用勾选框 (`- [ ]`) 语法进行跟踪。

**目标:** 使用 sherpa-onnx + vits-zh-hf-echo 模型添加离线 TTS 语音反馈，使操作结果在执行后通过语音播报。

**架构:** 新增 `TtsSpeaker` 封装 sherpa-onnx `OfflineTts` 进行生成 + `AudioTrack` 进行播放。`TtsModelManager` 处理模型下载。`VoiceControlService` 编排: 停止 ASR → 播报结果 → 重新启动 ASR。`MainActivity` 增加下载 UI。`ExecutionEngine` 的结果消息用作 TTS 文本。

**技术栈:** Kotlin, sherpa-onnx Android AAR, vits-zh-hf-echo 模型（约 120MB 下载）

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|--------|---------------|
| `app/libs/sherpa-onnx-1.13.2.aar` | 添加 | sherpa-onnx Android 库 |
| `app/src/main/jniLibs/<abi>/libsherpa-onnx-jni.so` | 添加 | 原生 JNI 库（4 个 ABI） |
| `app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt` | 创建 | TTS 引擎封装 + AudioTrack 播放 |
| `app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt` | 创建 | 模型下载与解压 |
| `app/src/main/java/com/controlmoblie/service/VoiceControlService.kt` | 修改 | 集成 TTS: 播报前停止 ASR，播报后重新启动 |
| `app/src/main/java/com/controlmoblie/execution/ExecutionEngine.kt` | 修改 | 将结果消息改为中文，添加原始应用名称 |
| `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt` | 修改 | OpenApp 结果使用原始名称 |
| `app/src/main/java/com/controlmoblie/model/Action.kt` | 修改 | 为 `OpenApp` 添加 `displayName` 字段以存储中文名称 |
| `app/src/main/java/com/controlmoblie/MainActivity.kt` | 修改 | 添加 TTS 模型下载 UI |
| `app/build.gradle.kts` | 修改 | 添加 sherpa-onnx AAR 依赖 |
| `gradle/libs.versions.toml` | 修改 | 添加 sherpa-onnx 版本号 |

---

### 任务 1: 添加 sherpa-onnx 依赖

**涉及文件:**
- 修改: `gradle/libs.versions.toml`
- 修改: `app/build.gradle.kts`
- 添加: `app/libs/sherpa-onnx-1.13.2.aar`
- 添加: `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so`
- 添加: `app/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so`
- 添加: `app/src/main/jniLibs/x86/libsherpa-onnx-jni.so`
- 添加: `app/src/main/jniLibs/x86_64/libsherpa-onnx-jni.so`

- [ ] **步骤 1: 下载 sherpa-onnx AAR**

从 `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-1.13.2.aar` 下载并放置到 `app/libs/sherpa-onnx-1.13.2.aar`。

- [ ] **步骤 2: 下载全部 4 个 ABI 的 JNI .so 文件**

从 `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-v1.13.2-android-native-lib.tar.bz2` 下载，解压后将各 ABI 的 `libsherpa-onnx-jni.so` 放入 `app/src/main/jniLibs/<abi>/`。

- [ ] **步骤 3: 在 `gradle/libs.versions.toml` 中添加版本号**

在 `[versions]` 节中添加 `sherpa-onnx = "1.13.2"`。

- [ ] **步骤 4: 在 `app/build.gradle.kts` 中添加 AAR 依赖**

在 dependencies 块中添加 `implementation(files("libs/sherpa-onnx-1.13.2.aar"))`。

- [ ] **步骤 5: 验证编译通过**

运行: `.\gradlew :app:compileDebugKotlin`
预期: BUILD SUCCESSFUL

- [ ] **步骤 6: 提交**

```
git add app/libs/sherpa-onnx-1.13.2.aar app/src/main/jniLibs/ gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add sherpa-onnx TTS dependency"
```

---

### 任务 2: 创建 TtsModelManager

**涉及文件:**
- 创建: `app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt`

- [ ] **步骤 1: 参照 VoskModelManager 模式创建 TtsModelManager**

创建 `app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt`:

```kotlin
package com.controlmoblie.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

object TtsModelManager {

    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-echo.tar.bz2"
    private const val MODEL_DIR_NAME = "vits-zh-hf-echo"
    private const val TEMP_FILE_NAME = "vits-zh-hf-echo.tar.bz2.tmp"
    private const val MODEL_FILENAME = "echo.onnx"

    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        val modelFile = File(modelDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun getModelDir(context: Context): String {
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
                            if (relativePath.isBlank() || relativePath == "rule.far") {
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

- [ ] **步骤 2: 添加 Apache Commons Compress 依赖用于 tar.bz2 解压**

在 `gradle/libs.versions.toml` 中添加:
```toml
commons-compress = "1.26.2"
```

在 `[libraries]` 节中添加:
```toml
commons-compress = { group = "org.apache.commons", name = "commons-compress", version.ref = "commons-compress" }
```

在 `app/build.gradle.kts` 的 dependencies 中添加:
```kotlin
implementation(libs.commons.compress)
```

- [ ] **步骤 3: 验证编译通过**

运行: `.\gradlew :app:compileDebugKotlin`
预期: BUILD SUCCESSFUL

- [ ] **步骤 4: 提交**

```
git add app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add TtsModelManager for model download"
```

---

### 任务 3: 创建 TtsSpeaker

**涉及文件:**
- 创建: `app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt`

- [ ] **步骤 1: 创建 TtsSpeaker 类**

创建 `app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt`:

```kotlin
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

                engine.generateWithCallback(text, SPEAKER_ID, SPEED) { samples ->
                    if (!isSpeaking) return@generateWithCallback 0
                    track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    if (isSpeaking) 1 else 0
                }

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
    }

    val isModelReady: Boolean get() = TtsModelManager.isModelReady(context)
}
```

- [ ] **步骤 2: 验证编译通过**

运行: `.\gradlew :app:compileDebugKotlin`
预期: BUILD SUCCESSFUL

- [ ] **步骤 3: 提交**

```
git add app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt
git commit -m "feat: add TtsSpeaker for sherpa-onnx TTS playback"
```

---

### 任务 4: 为 Action.OpenApp 添加 displayName 字段

**涉及文件:**
- 修改: `app/src/main/java/com/controlmoblie/model/Action.kt`
- 修改: `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`
- 修改: `app/src/main/java/com/controlmoblie/llm/InstructionParser.kt`
- 修改: `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt`

- [ ] **步骤 1: 为 Action.OpenApp 添加 displayName 字段**

在 `model/Action.kt` 中将:
```kotlin
data class OpenApp(val `package`: String) : Action()
```
改为:
```kotlin
data class OpenApp(val `package`: String, val displayName: String = "") : Action()
```

- [ ] **步骤 2: 更新 LlmEngine.simulateInference 以包含 displayName**

在 `LlmEngine.kt` 中将 `open_app` 分支改为:
```kotlin
userText.contains("打开") -> {
    val target = extractAfterKeyword(userText, listOf("打开"))
    val pkg = AppResolver.resolve(target)
    "{\"action\": \"open_app\", \"package\": \"$pkg\", \"displayName\": \"$target\"}"
}
```

- [ ] **步骤 3: 更新 InstructionParser.parseAction 以解析 displayName**

在 `InstructionParser.kt` 中将 `open_app` 分支从:
```kotlin
"open_app" -> Action.OpenApp(json.optString("package", ""))
```
改为:
```kotlin
"open_app" -> Action.OpenApp(json.optString("package", ""), json.optString("displayName", ""))
```

- [ ] **步骤 4: 更新 ControlAccessibilityService.executeOpenApp 在结果中使用 displayName**

在 `ControlAccessibilityService.kt` 中修改结果消息:
```kotlin
private fun executeOpenApp(action: Action.OpenApp, onResult: (Boolean, String) -> Unit) {
    val packageName = AppResolver.resolve(action.`package`)
    Log.d(TAG, "executeOpenApp: name=${action.`package`} resolved=$packageName")
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Log.w(TAG, "executeOpenApp: package not found $packageName")
        val displayName = action.displayName.ifBlank { action.`package` }
        onResult(false, "未找到应用: $displayName")
        return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    val displayName = action.displayName.ifBlank { action.`package` }
    onResult(true, "已打开 $displayName")
}
```

- [ ] **步骤 5: 将其他结果消息更新为中文**

在 `ControlAccessibilityService.kt` 中更新以下结果消息:
- `executeClick` 成功: `"已点击 ${action.target}"`（原为 `"clicked ${action.target}"`）
- `executeClick` 失败: `"未找到 ${action.target}"`（原为 `"target '${action.target}' not found"`）
- `executeNavigate` 成功: 使用中文 — 返回=`"已返回"`、回桌面=`"已回桌面"`、最近任务=`"最近任务"`（原为 `"navigate ${action.type}"`）
- `executeNavigate` 失败: `"导航失败"`（原为 `"failed to navigate ${action.type}"`）
- `executeScroll` 成功: `"已${action.direction}"`（原为 `"scrolled ${action.direction}"`）
- `executeScroll` 失败: `"滑动失败"`（原为 `"scroll cancelled"`）
- `executeType` 成功: `"已输入"`（原为 `"typed text"`）
- `executeType` 失败: `"未找到输入框"`（原为 `"no focused input field"`）

- [ ] **步骤 6: 验证编译通过**

运行: `.\gradlew :app:compileDebugKotlin`
预期: BUILD SUCCESSFUL

- [ ] **步骤 7: 提交**

```
git add app/src/main/java/com/controlmoblie/model/Action.kt app/src/main/java/com/controlmoblie/llm/LlmEngine.kt app/src/main/java/com/controlmoblie/llm/InstructionParser.kt app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt
git commit -m "feat: add displayName to Action.OpenApp, Chinese result messages"
```

---

### 任务 5: 将 TTS 集成到 VoiceControlService

**涉及文件:**
- 修改: `app/src/main/java/com/controlmoblie/service/VoiceControlService.kt`

- [ ] **步骤 1: 添加 TtsSpeaker 字段和生命周期管理**

在导入部分添加 `import com.controlmoblie.tts.TtsSpeaker` 和 `import com.controlmoblie.tts.TtsModelManager`。

添加字段:
```kotlin
private lateinit var ttsSpeaker: TtsSpeaker
```

在 `onCreate()` 中，在 `executionEngine = ExecutionEngine()` 之后添加:
```kotlin
ttsSpeaker = TtsSpeaker(this)
```

在 `initAndStart()` 中，在 `loadLlmModel()` 之后添加:
```kotlin
loadTtsModel()
```

添加方法:
```kotlin
private fun loadTtsModel() {
    serviceScope.launch {
        if (TtsModelManager.isModelReady(this@VoiceControlService) && !ttsSpeaker.isModelReady) {
            overlay.updateState(OverlayState.PROCESSING, result = "加载语音合成...")
            val success = ttsSpeaker.init()
            if (success) {
                Log.d(TAG, "TTS model loaded successfully")
            } else {
                Log.w(TAG, "TTS model load failed")
            }
        }
    }
}
```

在 `onDestroy()` 中，在 `super.onDestroy()` 之前添加:
```kotlin
ttsSpeaker.release()
```

- [ ] **步骤 2: 添加 speakResult 辅助方法**

添加方法:
```kotlin
private suspend fun speakResult(text: String) {
    if (text.isBlank()) return
    if (!ttsSpeaker.isModelReady) return
    ttsSpeaker.speak(text) {
        serviceScope.launch {
            delay(500)
            if (isRunning) startListening()
        }
    }
}
```

- [ ] **步骤 3: 修改 processVoiceCommand 以在 TTS 播报前停止 ASR，播报后重新启动**

在 `processVoiceCommand` 中，修改所有三个分支（成功、错误、空操作）以在 TTS 前停止 ASR 并播报结果。将现有流程替换为:

**成功分支**（`executionEngine.execute(action)` 回调之后）:
```kotlin
overlay.updateState(OverlayState.EXECUTING, text = userText)
executionEngine.execute(action) { execResult ->
    serviceScope.launch {
        val speakText = if (execResult.success) execResult.message else execResult.message
        overlay.updateState(
            if (execResult.success) OverlayState.IDLE else OverlayState.ERROR,
            text = userText,
            result = execResult.message
        )
        if (execResult.success && action is Action.Wait) {
            delay(1000)
            if (isRunning) startListening()
        } else {
            speakResult(speakText)
        }
    }
}
```

**解析错误分支**（`result.error != null`）:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = result.error)
speakResult(result.error ?: "解析失败")
```

**空操作分支**:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = "无法解析指令")
speakResult("无法解析指令")
```

**超时捕获**:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = "推理超时")
speakResult("推理超时")
```

**异常捕获**:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = "处理失败: ${e.message}")
speakResult("处理失败")
```

**重要**: 在每个 `speakResult()` 调用中，`startListening()` 将在 `speakResult` 的 `onDone` 回调中重新启动（通过上述方法）。这意味着我们必须**移除**所有错误/超时分支中现有的 `delay(1500); if (isRunning) startListening()` 调用，因为 `speakResult` 已处理重启逻辑。

对于 `Action.Wait` 成功情况（规范中要求静默），跳过 TTS 并在延迟后直接重新启动监听。

- [ ] **步骤 4: 验证编译通过**

运行: `.\gradlew :app:compileDebugKotlin`
预期: BUILD SUCCESSFUL

- [ ] **步骤 5: 提交**

```
git add app/src/main/java/com/controlmoblie/service/VoiceControlService.kt
git commit -m "feat: integrate TTS into VoiceControlService"
```

---

### 任务 6: 在 MainActivity 中添加 TTS 模型下载 UI

**涉及文件:**
- 修改: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **步骤 1: 在 ControlScreen 中添加 TTS 模型下载区域**

添加导入:
```kotlin
import com.controlmoblie.tts.TtsModelManager
```

在 `ControlScreen` 中添加状态变量:
```kotlin
var ttsModelReady by remember { mutableStateOf(TtsModelManager.isModelReady(this@MainActivity)) }
var ttsDownloadProgress by remember { mutableStateOf(-1f) }
var ttsDownloading by remember { mutableStateOf(false) }
```

在 Vosk 模型区域之后（"开启无障碍服务" PermissionItem 之前）添加 UI 区域:

```kotlin
if (ttsDownloading) {
    LinearProgressIndicator(
        progress = { if (ttsDownloadProgress >= 0f) ttsDownloadProgress else 0f },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        if (ttsDownloadProgress >= 0f) "下载语音合成模型中... ${(ttsDownloadProgress * 100).toInt()}%"
        else "准备下载...",
        style = MaterialTheme.typography.bodySmall
    )
}

if (!ttsModelReady && !ttsDownloading) {
    OutlinedButton(
        onClick = {
            ttsDownloading = true
            ttsDownloadProgress = 0f
            this@MainActivity.lifecycleScope.launch(Dispatchers.Main) {
                val success = TtsModelManager.downloadAndExtract(this@MainActivity) { progress ->
                    ttsDownloadProgress = progress
                }
                ttsModelReady = success
                ttsDownloading = false
                ttsDownloadProgress = -1f
            }
        },
        enabled = !ttsDownloading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("下载语音合成模型 (~120MB)")
    }
}

if (ttsModelReady && !ttsDownloading) {
    Text("语音合成 ✓", color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall)
}
```

- [ ] **步骤 2: 验证编译通过**

运行: `.\gradlew :app:compileDebugKotlin`
预期: BUILD SUCCESSFUL

- [ ] **步骤 3: 提交**

```
git add app/src/main/java/com/controlmoblie/MainActivity.kt
git commit -m "feat: add TTS model download UI in MainActivity"
```

---

### 任务 7: 完整集成测试

- [ ] **步骤 1: 构建完整 debug APK**

运行: `.\gradlew :app:assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 2: 安装到设备**

运行: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

- [ ] **步骤 3: 测试 TTS 模型下载**

打开应用 → 点击"下载语音合成模型" → 验证进度条和完成状态。检查 logcat 中是否有 `TtsSpeaker: TTS initialized`。

- [ ] **步骤 4: 测试 TTS 语音反馈**

启用无障碍服务，启动语音控制，说"返回" → 验证听到"已返回"语音播报，且 ASR 不会将 TTS 输出识别为新指令。

- [ ] **步骤 5: 若有修复需要则提交**

---

## 自查

1. **规格覆盖**: ✅ TTS 引擎（任务 3）、模型下载（任务 2）、VoiceControlService 集成（任务 5）、MainActivity UI（任务 6）、结果文本映射（任务 4）、依赖项（任务 1）、集成测试（任务 7）。
2. **占位符检查**: 无 TBD/TODO。所有代码均已完整展示。
3. **类型一致性**: `Action.OpenApp` 现在具有带默认值的 `displayName: String = ""` 参数，因此现有 `"open_app"` JSON 解析仍然可以正常工作。`TtsSpeaker.speak()` 接收 `text: String` 和 `onDone: () -> Unit`，与任务 5 中的调用点匹配。`TtsModelManager.isModelReady()` 返回 `Boolean`，与 `TtsSpeaker.init()` 和 `MainActivity` 中的使用一致。
4. **已识别的一个缺口**: 规格要求 `Action.Wait` 应为静默（无 TTS）。任务 5 步骤 3 通过检查 `action is Action.Wait` 并跳过 `speakResult`，在延迟后直接重新启动监听来处理此情况。
