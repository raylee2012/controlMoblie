# TTS Voice Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline TTS voice feedback using sherpa-onnx + vits-zh-hf-echo model, so action results are spoken aloud after execution.

**Architecture:** New `TtsSpeaker` wraps sherpa-onnx `OfflineTts` for generation + `AudioTrack` for playback. `TtsModelManager` handles model download. `VoiceControlService` orchestrates: stop ASR → speak result → restart ASR. `MainActivity` gets a download UI. `ExecutionEngine` result messages are used as TTS text.

**Tech Stack:** Kotlin, sherpa-onnx Android AAR, vits-zh-hf-echo model (~120MB download)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `app/libs/sherpa-onnx-1.13.2.aar` | Add | sherpa-onnx Android library |
| `app/src/main/jniLibs/<abi>/libsherpa-onnx-jni.so` | Add | Native JNI library (4 ABIs) |
| `app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt` | Create | TTS engine wrapper + AudioTrack playback |
| `app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt` | Create | Model download & extraction |
| `app/src/main/java/com/controlmoblie/service/VoiceControlService.kt` | Modify | Integrate TTS: stop ASR before speak, restart after |
| `app/src/main/java/com/controlmoblie/execution/ExecutionEngine.kt` | Modify | Change result messages to Chinese, add original app name |
| `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt` | Modify | OpenApp result uses original name |
| `app/src/main/java/com/controlmoblie/model/Action.kt` | Modify | Add `displayName` field to `OpenApp` for Chinese name |
| `app/src/main/java/com/controlmoblie/MainActivity.kt` | Modify | Add TTS model download UI |
| `app/build.gradle.kts` | Modify | Add sherpa-onnx AAR dependency |
| `gradle/libs.versions.toml` | Modify | Add sherpa-onnx version |

---

### Task 1: Add sherpa-onnx dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Add: `app/libs/sherpa-onnx-1.13.2.aar`
- Add: `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so`
- Add: `app/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so`
- Add: `app/src/main/jniLibs/x86/libsherpa-onnx-jni.so`
- Add: `app/src/main/jniLibs/x86_64/libsherpa-onnx-jni.so`

- [ ] **Step 1: Download sherpa-onnx AAR**

Download from `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-1.13.2.aar` and place at `app/libs/sherpa-onnx-1.13.2.aar`.

- [ ] **Step 2: Download JNI .so files for all 4 ABIs**

Download from `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-v1.13.2-android-native-lib.tar.bz2`, extract, and place each ABI's `libsherpa-onnx-jni.so` into `app/src/main/jniLibs/<abi>/`.

- [ ] **Step 3: Add version to `gradle/libs.versions.toml`**

Add `sherpa-onnx = "1.13.2"` to `[versions]` section.

- [ ] **Step 4: Add AAR dependency to `app/build.gradle.kts`**

Add `implementation(files("libs/sherpa-onnx-1.13.2.aar"))` to dependencies block.

- [ ] **Step 5: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add app/libs/sherpa-onnx-1.13.2.aar app/src/main/jniLibs/ gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add sherpa-onnx TTS dependency"
```

---

### Task 2: Create TtsModelManager

**Files:**
- Create: `app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt`

- [ ] **Step 1: Create TtsModelManager mirroring VoskModelManager pattern**

Create `app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt`:

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

- [ ] **Step 2: Add Apache Commons Compress dependency for tar.bz2 extraction**

In `gradle/libs.versions.toml`, add:
```toml
commons-compress = "1.26.2"
```

In `[libraries]` section add:
```toml
commons-compress = { group = "org.apache.commons", name = "commons-compress", version.ref = "commons-compress" }
```

In `app/build.gradle.kts`, add to dependencies:
```kotlin
implementation(libs.commons.compress)
```

- [ ] **Step 3: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/controlmoblie/tts/TtsModelManager.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add TtsModelManager for model download"
```

---

### Task 3: Create TtsSpeaker

**Files:**
- Create: `app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt`

- [ ] **Step 1: Create TtsSpeaker class**

Create `app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt`:

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

- [ ] **Step 2: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/controlmoblie/tts/TtsSpeaker.kt
git commit -m "feat: add TtsSpeaker for sherpa-onnx TTS playback"
```

---

### Task 4: Add displayName to Action.OpenApp

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/model/Action.kt`
- Modify: `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`
- Modify: `app/src/main/java/com/controlmoblie/llm/InstructionParser.kt`
- Modify: `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt`

- [ ] **Step 1: Add displayName field to Action.OpenApp**

In `model/Action.kt`, change:
```kotlin
data class OpenApp(val `package`: String) : Action()
```
to:
```kotlin
data class OpenApp(val `package`: String, val displayName: String = "") : Action()
```

- [ ] **Step 2: Update LlmEngine.simulateInference to include displayName**

In `LlmEngine.kt`, change the `open_app` case to:
```kotlin
userText.contains("打开") -> {
    val target = extractAfterKeyword(userText, listOf("打开"))
    val pkg = AppResolver.resolve(target)
    "{\"action\": \"open_app\", \"package\": \"$pkg\", \"displayName\": \"$target\"}"
}
```

- [ ] **Step 3: Update InstructionParser.parseAction to parse displayName**

In `InstructionParser.kt`, change the `open_app` case from:
```kotlin
"open_app" -> Action.OpenApp(json.optString("package", ""))
```
to:
```kotlin
"open_app" -> Action.OpenApp(json.optString("package", ""), json.optString("displayName", ""))
```

- [ ] **Step 4: Update ControlAccessibilityService.executeOpenApp to use displayName in result**

In `ControlAccessibilityService.kt`, change the result messages:
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

- [ ] **Step 5: Update other result messages to Chinese**

In `ControlAccessibilityService.kt`, update these result messages:
- `executeClick` success: `"已点击 ${action.target}"` (was `"clicked ${action.target}"`)
- `executeClick` fail: `"未找到 ${action.target}"` (was `"target '${action.target}' not found"`)
- `executeNavigate` success: use Chinese — back=`"已返回"`, home=`"已回桌面"`, recents=`"最近任务"` (was `"navigate ${action.type}"`)
- `executeNavigate` fail: `"导航失败"` (was `"failed to navigate ${action.type}"`)
- `executeScroll` success: `"已${action.direction}"` (was `"scrolled ${action.direction}"`)
- `executeScroll` fail: `"滑动失败"` (was `"scroll cancelled"`)
- `executeType` success: `"已输入"` (was `"typed text"`)
- `executeType` fail: `"未找到输入框"` (was `"no focused input field"`)

- [ ] **Step 6: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/controlmoblie/model/Action.kt app/src/main/java/com/controlmoblie/llm/LlmEngine.kt app/src/main/java/com/controlmoblie/llm/InstructionParser.kt app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt
git commit -m "feat: add displayName to Action.OpenApp, Chinese result messages"
```

---

### Task 5: Integrate TTS into VoiceControlService

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/service/VoiceControlService.kt`

- [ ] **Step 1: Add TtsSpeaker field and lifecycle management**

Add `import com.controlmoblie.tts.TtsSpeaker` and `import com.controlmoblie.tts.TtsModelManager` to imports.

Add fields:
```kotlin
private lateinit var ttsSpeaker: TtsSpeaker
```

In `onCreate()`, add after `executionEngine = ExecutionEngine()`:
```kotlin
ttsSpeaker = TtsSpeaker(this)
```

In `initAndStart()`, add after `loadLlmModel()`:
```kotlin
loadTtsModel()
```

Add method:
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

In `onDestroy()`, add before `super.onDestroy()`:
```kotlin
ttsSpeaker.release()
```

- [ ] **Step 2: Add speakResult helper method**

Add method:
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

- [ ] **Step 3: Modify processVoiceCommand to stop ASR, speak, then restart**

In `processVoiceCommand`, change ALL three branches (success, error, null action) to stop ASR before TTS and speak the result. Replace the existing flow with:

For the **success branch** (after `executionEngine.execute(action)` callback):
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

For the **parse error branch** (`result.error != null`):
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = result.error)
speakResult(result.error ?: "解析失败")
```

For the **null action branch**:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = "无法解析指令")
speakResult("无法解析指令")
```

For the **timeout** catch:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = "推理超时")
speakResult("推理超时")
```

For the **exception** catch:
```kotlin
overlay.updateState(OverlayState.ERROR, text = userText, result = "处理失败: ${e.message}")
speakResult("处理失败")
```

**Important**: In each `speakResult()` call, `startListening()` will be restarted inside `speakResult`'s `onDone` callback (via the method above). This means we must **remove** the existing `delay(1500); if (isRunning) startListening()` calls in all error/timeout branches, since `speakResult` handles the restart.

For the `Action.Wait` success case (already silent in spec), skip TTS and restart listening directly after the delay.

- [ ] **Step 4: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/controlmoblie/service/VoiceControlService.kt
git commit -m "feat: integrate TTS into VoiceControlService"
```

---

### Task 6: Add TTS model download UI in MainActivity

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: Add TTS model download section to ControlScreen**

Add imports:
```kotlin
import com.controlmoblie.tts.TtsModelManager
```

Add state variables in `ControlScreen`:
```kotlin
var ttsModelReady by remember { mutableStateOf(TtsModelManager.isModelReady(this@MainActivity)) }
var ttsDownloadProgress by remember { mutableStateOf(-1f) }
var ttsDownloading by remember { mutableStateOf(false) }
```

Add UI section after the Vosk model section (before the "开启无障碍服务" PermissionItem):

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

- [ ] **Step 2: Verify build compiles**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/controlmoblie/MainActivity.kt
git commit -m "feat: add TTS model download UI in MainActivity"
```

---

### Task 7: Full integration test

- [ ] **Step 1: Build full debug APK**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install on device**

Run: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

- [ ] **Step 3: Test TTS model download**

Open app → tap "下载语音合成模型" → verify progress bar and completion. Check logcat for `TtsSpeaker: TTS initialized`.

- [ ] **Step 4: Test TTS voice feedback**

Enable accessibility service, start voice control, say "返回" → verify you hear "已返回" spoken aloud, and ASR doesn't pick up the TTS output as a new command.

- [ ] **Step 5: Commit if any fixes were needed**

---

## Self-Review

1. **Spec coverage**: ✅ TTS engine (Task 3), model download (Task 2), VoiceControlService integration (Task 5), MainActivity UI (Task 6), result text mapping (Task 4), dependency (Task 1), integration test (Task 7).
2. **Placeholder scan**: No TBD/TODO. All code shown in full.
3. **Type consistency**: `Action.OpenApp` now has `displayName: String = ""` parameter with default, so existing `"open_app"` JSON parsing still works. `TtsSpeaker.speak()` takes `text: String` and `onDone: () -> Unit`, matching the call sites in Task 5. `TtsModelManager.isModelReady()` returns `Boolean`, matching usage in `TtsSpeaker.init()` and `MainActivity`.
4. **One gap identified**: The spec says `Action.Wait` should be silent (no TTS). Task 5 Step 3 handles this by checking `action is Action.Wait` and skipping `speakResult`, restarting listening directly after the delay.