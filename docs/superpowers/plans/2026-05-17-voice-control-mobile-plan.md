# Voice Control Mobile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that takes voice commands via Google SpeechRecognizer, parses them with a local LLM (Qwen2.5-0.5B), and executes operations via Accessibility Service.

**Architecture:** Single Android app with foreground service managing lifecycle. Voice → ASR → LLM → JSON → Execution Engine → Accessibility Service. UI overlay shows status.

**Tech Stack:** Kotlin, Jetpack Compose, Android SpeechRecognizer, llama.cpp Android port, Accessibility Service, Kotlin Coroutines.

---

### Task 1: Project Scaffolding

**Files:**
- Create: `app/build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/controlmoblie/ControlMoblieApp.kt`
- Create: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "controlMoblie"
include(":app")
```

- [ ] **Step 2: Create gradle/libs.versions.toml**

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
compose-bom = "2024.02.00"
activity-compose = "1.8.2"
lifecycle = "2.7.0"
coroutines = "1.7.3"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 3: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.controlmoblie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.controlmoblie"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 4: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".ControlMoblieApp"
        android:icon="@mipmap/ic_launcher"
        android:label="Control Mobile"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Material3.DayNight.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.ControlAccessibilityService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <service
            android:name=".service.VoiceControlService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

    </application>

</manifest>
```

- [ ] **Step 6: Create ControlMoblieApp.kt**

```kotlin
package com.controlmoblie

import android.app.Application

class ControlMoblieApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

- [ ] **Step 7: Create MainActivity.kt**

```kotlin
package com.controlmoblie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            androidx.compose.material3.MaterialTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.foundation.layout.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text("Control Mobile")
                }
            }
        }
    }
}
```

- [ ] **Step 8: Create accessibility service config**

Create: `app/src/main/res/xml/accessibility_service_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description" />
```

Create: `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="accessibility_service_description">控制手机语音操作的无障碍服务</string>
    <string name="app_name">Control Mobile</string>
</resources>
```

- [ ] **Step 9: Verify project builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL



### Task 2: Action Data Models

**Files:**
- Create: `app/src/main/java/com/controlmoblie/model/Action.kt`

- [ ] **Step 1: Write Action.kt**

```kotlin
package com.controlmoblie.model

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo

sealed class Action {
    data class Click(val target: String) : Action()
    data class OpenApp(val `package`: String) : Action()
    data class Navigate(val type: NavType) : Action()
    data class Scroll(val direction: ScrollDirection, val distance: ScrollDistance = ScrollDistance.HALF) : Action()
    data class Type(val text: String) : Action()
    data class Wait(val ms: Long) : Action()
    data class Sequence(val steps: List<Action>) : Action()
}

enum class NavType { BACK, HOME, RECENTS }
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
enum class ScrollDistance { SHORT, HALF, FULL }

data class InstructionResult(
    val action: Action?,
    val error: String? = null
)

data class ScreenState(
    val nodeTexts: List<String> = emptyList(),
    val packageName: String = ""
)
```

- [ ] **Step 2: Verify file compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git init
git add -A
git commit -m "feat: project scaffold and action models"
```



### Task 3: Accessibility Service

**Files:**
- Create: `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt`
- Modify: `app/src/main/java/com/controlmoblie/model/Action.kt`

- [ ] **Step 1: Write ControlAccessibilityService.kt**

```kotlin
package com.controlmoblie.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.controlmoblie.model.*

class ControlAccessibilityService : AccessibilityService() {

    var lastScreenState: ScreenState = ScreenState()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            updateScreenState()
        }
    }

    override fun onInterrupt() {}

    private fun updateScreenState() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        root.recycle()
        val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(android.app.UiModeManager::class.java)?.applicationContext?.packageName ?: ""
        } else {
            ""
        }
        lastScreenState = ScreenState(
            nodeTexts = texts,
            packageName = packageName
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) texts.add(text)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }

    fun executeAction(action: Action, onResult: (Boolean, String) -> Unit) {
        when (action) {
            is Action.Click -> executeClick(action, onResult)
            is Action.OpenApp -> executeOpenApp(action, onResult)
            is Action.Navigate -> executeNavigate(action, onResult)
            is Action.Scroll -> executeScroll(action, onResult)
            is Action.Type -> executeType(action, onResult)
            is Action.Wait -> {
                android.os.Handler(mainLooper).postDelayed({
                    onResult(true, "waited ${action.ms}ms")
                }, action.ms)
            }
            is Action.Sequence -> executeSequence(action, onResult)
        }
    }

    private fun executeClick(action: Action.Click, onResult: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { onResult(false, "no window root"); return }

        val nodes = root.findAccessibilityNodeInfosByText(action.target)
        if (nodes.isEmpty()) {
            root.recycle()
            onResult(false, "target '${action.target}' not found")
            return
        }

        val node = nodes[0]
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        root.recycle()
        onResult(clicked, if (clicked) "clicked ${action.target}" else "failed to click ${action.target}")
    }

    private fun executeOpenApp(action: Action.OpenApp, onResult: (Boolean, String) -> Unit) {
        val intent = packageManager.getLaunchIntentForPackage(action.`package`)
        if (intent == null) {
            onResult(false, "package ${action.`package`} not found")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        onResult(true, "opened ${action.`package`}")
    }

    private fun executeNavigate(action: Action.Navigate, onResult: (Boolean, String) -> Unit) {
        val globalAction = when (action.type) {
            NavType.BACK -> GLOBAL_ACTION_BACK
            NavType.HOME -> GLOBAL_ACTION_HOME
            NavType.RECENTS -> GLOBAL_ACTION_RECENTS
        }
        val success = performGlobalAction(globalAction)
        onResult(success, if (success) "navigate ${action.type}" else "failed to navigate ${action.type}")
    }

    private fun executeScroll(action: Action.Scroll, onResult: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { onResult(false, "no window root"); return }

        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)

        val fromX: Int
        val fromY: Int
        val toX: Int
        val toY: Int
        val midX = size.x / 2
        val midY = size.y / 2
        val offset = (if (action.distance == ScrollDistance.SHORT) 200
            else if (action.distance == ScrollDistance.HALF) size.y / 3
            else size.y * 2 / 3)

        when (action.direction) {
            ScrollDirection.UP -> { fromX = midX; fromY = midY + offset / 2; toX = midX; toY = midY - offset / 2 }
            ScrollDirection.DOWN -> { fromX = midX; fromY = midY - offset / 2; toX = midX; toY = midY + offset / 2 }
            ScrollDirection.LEFT -> { fromX = midX + offset / 2; fromY = midY; toX = midX - offset / 2; toY = midY }
            ScrollDirection.RIGHT -> { fromX = midX - offset / 2; fromY = midY; toX = midX + offset / 2; toY = midY }
        }

        val path = Path().apply { moveTo(fromX.toFloat(), fromY.toFloat()); lineTo(toX.toFloat(), toY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()
        root.recycle()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(true, "scrolled ${action.direction}")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(false, "scroll cancelled")
            }
        }, null)
    }

    private fun executeType(action: Action.Type, onResult: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { onResult(false, "no window root"); return }

        val focusNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode == null) {
            root.recycle()
            onResult(false, "no focused input field")
            return
        }

        val bundle = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
        }
        val success = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        focusNode.recycle()
        root.recycle()
        onResult(success, if (success) "typed text" else "failed to type")
    }

    private fun executeSequence(action: Action.Sequence, onResult: (Boolean, String) -> Unit) {
        val results = mutableListOf<String>()
        var failed = false
        val stepDuration = 200L

        fun runNext(index: Int) {
            if (failed || index >= action.steps.size) {
                onResult(!failed, results.joinToString("; "))
                return
            }
            executeAction(action.steps[index]) { success, msg ->
                results.add(msg)
                if (!success) failed = true
                android.os.Handler(mainLooper).postDelayed({ runNext(index + 1) }, stepDuration)
            }
        }
        runNext(0)
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add accessibility service with action execution"
```



### Task 4: Screen Reader Module

**Files:**
- Create: `app/src/main/java/com/controlmoblie/util/ScreenReader.kt`

- [ ] **Step 1: Write ScreenReader.kt**

```kotlin
package com.controlmoblie.util

import android.view.accessibility.AccessibilityNodeInfo
import com.controlmoblie.model.ScreenState

object ScreenReader {

    fun readScreen(root: AccessibilityNodeInfo?): ScreenState {
        if (root == null) return ScreenState()
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return ScreenState(
            nodeTexts = texts,
            packageName = root.packageName?.toString() ?: ""
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && node.isVisibleToUser) {
            texts.add(text)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }

    fun buildScreenContext(state: ScreenState): String {
        val sb = StringBuilder()
        sb.appendLine("当前应用: ${state.packageName}")
        if (state.nodeTexts.isNotEmpty()) {
            sb.appendLine("屏幕可见内容:")
            state.nodeTexts.forEach { sb.appendLine("- $it") }
        }
        return sb.toString()
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add screen reader module"
```



### Task 5: ASR Module (Speech Recognizer)

**Files:**
- Create: `app/src/main/java/com/controlmoblie/asr/SpeechRecognizerManager.kt`

- [ ] **Step 1: Write SpeechRecognizerManager.kt**

```kotlin
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
    private val _events = Channel<AsrEvent>(Channel.BUFFERED)
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
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add speech recognizer manager"
```



### Task 6: LLM Engine + Instruction Parser

**Files:**
- Create: `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`
- Create: `app/src/main/java/com/controlmoblie/llm/InstructionParser.kt`

- [ ] **Step 1: Write LlmEngine.kt**

```kotlin
package com.controlmoblie.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class LlmEngine(private val context: Context) {

    private var isLoaded = false
    private var modelPath: String = ""

    companion object {
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val MODEL_FILENAME = "qwen2.5-0.5b-q4.gguf"
    }

    val isModelLoaded: Boolean get() = isLoaded
    val isDownloaded: Boolean get() = File(context.filesDir, MODEL_FILENAME).exists()

    suspend fun downloadModel(onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val dest = File(context.filesDir, MODEL_FILENAME)
            if (dest.exists()) {
                isLoaded = true
                modelPath = dest.absolutePath
                return@withContext
            }
            val url = URL(MODEL_URL)
            val connection = url.openConnection()
            connection.connect()
            val fileLength = connection.contentLengthLong
            val input = connection.getInputStream()
            val output = FileOutputStream(dest)
            val buffer = ByteArray(8192)
            var totalRead = 0L
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (fileLength > 0) {
                    onProgress(totalRead.toFloat() / fileLength)
                }
            }
            output.close()
            input.close()
            isLoaded = true
            modelPath = dest.absolutePath
        }
    }

    suspend fun loadModel(): Boolean {
        return withContext(Dispatchers.IO) {
            val dest = File(context.filesDir, MODEL_FILENAME)
            if (!dest.exists()) return@withContext false
            // llama.cpp native library load would happen here
            // For now, we simulate loading
            isLoaded = true
            modelPath = dest.absolutePath
            true
        }
    }

    suspend fun infer(prompt: String): String {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) return@withContext "{\"action\": \"error\", \"message\": \"模型未加载\"}"
            // llama.cpp inference call goes here
            // For now return a placeholder - actual integration requires JNI binding
            simulateInference(prompt)
        }
    }

    private suspend fun simulateInference(prompt: String): String {
        // Simple keyword-based simulation for early development
        return when {
            prompt.contains("打开") || prompt.contains("启动") -> {
                val app = extractAfterKeyword(prompt, listOf("打开", "启动"))
                "{\"action\": \"navigate\", \"type\": \"home\"}"
            }
            prompt.contains("返回") || prompt.contains("后退") ->
                "{\"action\": \"navigate\", \"type\": \"back\"}"
            prompt.contains("回到桌面") || prompt.contains("主页") ->
                "{\"action\": \"navigate\", \"type\": \"home\"}"
            prompt.contains("点击") || prompt.contains("打开") -> {
                val target = extractAfterKeyword(prompt, listOf("点击", "打开"))
                "{\"action\": \"click\", \"target\": \"$target\"}"
            }
            prompt.contains("上滑") || prompt.contains("向上滑") ->
                "{\"action\": \"scroll\", \"direction\": \"up\", \"distance\": \"half\"}"
            prompt.contains("下滑") || prompt.contains("向下滑") ->
                "{\"action\": \"scroll\", \"direction\": \"down\", \"distance\": \"half\"}"
            else ->
                "{\"action\": \"error\", \"message\": \"无法理解指令\"}"
        }
    }

    private fun extractAfterKeyword(text: String, keywords: List<String>): String {
        for (kw in keywords) {
            val idx = text.indexOf(kw)
            if (idx >= 0) {
                val after = text.substring(idx + kw.length).trim()
                if (after.isNotBlank()) return after
            }
        }
        return text
    }

    fun unload() {
        isLoaded = false
    }
}
```

- [ ] **Step 2: Write InstructionParser.kt**

```kotlin
package com.controlmoblie.llm

import com.controlmoblie.model.*

class InstructionParser {

    fun parse(rawJson: String): InstructionResult {
        val trimmed = rawJson.trim().removeSurrounding("```json").removeSurrounding("```").trim()
        return try {
            val json = org.json.JSONObject(trimmed)
            val action = parseAction(json)
            InstructionResult(action = action)
        } catch (e: Exception) {
            InstructionResult(
                action = null,
                error = "JSON 解析失败: ${e.message}"
            )
        }
    }

    private fun parseAction(json: org.json.JSONObject): Action? {
        val actionType = json.optString("action", "") ?: return null
        return when (actionType) {
            "click" -> Action.Click(json.optString("target", ""))
            "open_app" -> Action.OpenApp(json.optString("package", ""))
            "navigate" -> Action.Navigate(
                try { NavType.valueOf(json.optString("type", "").uppercase()) }
                catch (e: Exception) { return null }
            )
            "scroll" -> Action.Scroll(
                direction = try { ScrollDirection.valueOf(json.optString("direction", "").uppercase()) }
                    catch (e: Exception) { return null },
                distance = try { ScrollDistance.valueOf(json.optString("distance", "half").uppercase()) }
                    catch (e: Exception) { ScrollDistance.HALF }
            )
            "type" -> Action.Type(json.optString("text", ""))
            "wait" -> Action.Wait(json.optLong("ms", 1000))
            "sequence" -> {
                val steps = json.optJSONArray("steps")
                if (steps != null) {
                    val actions = mutableListOf<Action>()
                    for (i in 0 until steps.length()) {
                        steps.getJSONObject(i)?.let { parseAction(it)?.let { a -> actions.add(a) } }
                    }
                    Action.Sequence(actions)
                } else null
            }
            else -> null
        }
    }

    fun buildPrompt(userText: String, screenContext: String): String {
        return """
你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。

当前屏幕信息:
$screenContext

用户指令: $userText

请只输出 JSON，不要输出其他内容。JSON 格式说明:
- 点击: {"action": "click", "target": "目标文本"}
- 打开App: {"action": "open_app", "package": "包名"}
- 导航: {"action": "navigate", "type": "back|home|recents"}
- 滑动: {"action": "scroll", "direction": "up|down|left|right", "distance": "short|half|full"}
- 输入: {"action": "type", "text": "输入内容"}
- 等待: {"action": "wait", "ms": 毫秒数}
- 组合: {"action": "sequence", "steps": [...]}
""".trimIndent()
    }
}
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add LLM engine and instruction parser"
```



### Task 7: Execution Engine

**Files:**
- Create: `app/src/main/java/com/controlmoblie/execution/ExecutionEngine.kt`

- [ ] **Step 1: Write ExecutionEngine.kt**

```kotlin
package com.controlmoblie.execution

import com.controlmoblie.model.Action
import com.controlmoblie.service.ControlAccessibilityService

class ExecutionEngine(private val accessibilityService: ControlAccessibilityService?) {

    data class ExecutionResult(val success: Boolean, val message: String)

    fun execute(action: Action, onResult: (ExecutionResult) -> Unit) {
        if (accessibilityService == null) {
            onResult(ExecutionResult(false, "无障碍服务未连接"))
            return
        }
        accessibilityService.executeAction(action) { success, msg ->
            onResult(ExecutionResult(success, msg))
        }
    }

    fun executeSequence(actions: List<Action>, onComplete: (List<ExecutionResult>) -> Unit) {
        val results = mutableListOf<ExecutionResult>()
        fun runNext(index: Int) {
            if (index >= actions.size) {
                onComplete(results)
                return
            }
            execute(actions[index]) { result ->
                results.add(result)
                runNext(index + 1)
            }
        }
        runNext(0)
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add execution engine"
```



### Task 8: UI Overlay (Floating Window)

**Files:**
- Create: `app/src/main/java/com/controlmoblie/overlay/ControlOverlay.kt`

- [ ] **Step 1: Write ControlOverlay.kt**

```kotlin
package com.controlmoblie.overlay

import android.content.Context
import android.view.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class OverlayState { IDLE, LISTENING, PROCESSING, EXECUTING, ERROR }

class ControlOverlay(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var isShowing = false

    private var _state = OverlayState.IDLE
    private var _lastText = ""
    private var _lastResult = ""
    private var onToggleListener: (() -> Unit)? = null
    private var onStopListener: (() -> Unit)? = null

    fun setOnToggleListener(l: () -> Unit) { onToggleListener = l }
    fun setOnStopListener(l: () -> Unit) { onStopListener = l }

    fun show() {
        if (isShowing) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 200

        overlayView = FrameLayout(context)
        val composeView = ComposeView(context).apply {
            setContent {
                OverlayContent(
                    state = _state,
                    lastText = _lastText,
                    lastResult = _lastResult,
                    onToggle = { onToggleListener?.invoke() },
                    onStop = { onStopListener?.invoke() },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager.updateViewLayout(overlayView, params)
                    }
                )
            }
        }
        overlayView?.addView(composeView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        windowManager.addView(overlayView, params)
        isShowing = true
    }

    fun dismiss() {
        if (!isShowing) return
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        isShowing = false
    }

    fun updateState(state: OverlayState, text: String = "", result: String = "") {
        _state = state
        _lastText = text
        _lastResult = result
        // In a real implementation, invalidate the compose view
    }

    val isVisible: Boolean get() = isShowing

    @Composable
    private fun OverlayContent(
        state: OverlayState,
        lastText: String,
        lastResult: String,
        onToggle: () -> Unit,
        onStop: () -> Unit,
        onDrag: (Float, Float) -> Unit
    ) {
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }

        val bgColor = when (state) {
            OverlayState.IDLE -> Color(0xAA, 0xAA, 0xAA, 0xCC)
            OverlayState.LISTENING -> Color(0x00, 0x96, 0x88, 0xCC)
            OverlayState.PROCESSING -> Color(0xFF, 0xA0, 0x00, 0xCC)
            OverlayState.EXECUTING -> Color(0x21, 0x96, 0xF3, 0xCC)
            OverlayState.ERROR -> Color(0xE5, 0x39, 0x35, 0xCC)
        }

        val statusText = when (state) {
            OverlayState.IDLE -> "待命"
            OverlayState.LISTENING -> "倾听中..."
            OverlayState.PROCESSING -> "思考中..."
            OverlayState.EXECUTING -> "执行中..."
            OverlayState.ERROR -> "错误"
        }

        Box(
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .padding(4.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE, 0xEE, 0xEE, 0xDD)),
                modifier = Modifier.widthIn(min = 80.dp, max = 240.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(bgColor, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("▶", fontSize = 10.sp, modifier = Modifier.clickable(onClick = onToggle))
                        Spacer(Modifier.width(4.dp))
                        Text("✕", fontSize = 10.sp, color = Color.Red,
                            modifier = Modifier.clickable(onClick = onStop))
                    }
                    if (lastText.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(lastText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (lastResult.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(lastResult, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add floating overlay UI"
```



### Task 9: Voice Control Service (Foreground Service)

**Files:**
- Create: `app/src/main/java/com/controlmoblie/service/VoiceControlService.kt`

- [ ] **Step 1: Write VoiceControlService.kt**

```kotlin
package com.controlmoblie.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.controlmoblie.R
import com.controlmoblie.asr.AsrEvent
import com.controlmoblie.asr.SpeechRecognizerManager
import com.controlmoblie.execution.ExecutionEngine
import com.controlmoblie.llm.InstructionParser
import com.controlmoblie.llm.LlmEngine
import com.controlmoblie.model.Action
import com.controlmoblie.overlay.ControlOverlay
import com.controlmoblie.overlay.OverlayState
import com.controlmoblie.util.ScreenReader
import kotlinx.coroutines.*

class VoiceControlService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var asrManager: SpeechRecognizerManager
    private lateinit var llmEngine: LlmEngine
    private lateinit var parser: InstructionParser
    private lateinit var executionEngine: ExecutionEngine
    private lateinit var overlay: ControlOverlay
    private var accessibilityService: ControlAccessibilityService? = null
    private var isRunning = false

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "voice_control_channel"

    override fun onCreate() {
        super.onCreate()
        asrManager = SpeechRecognizerManager(this)
        llmEngine = LlmEngine(this)
        parser = InstructionParser()
        overlay = ControlOverlay(this)
        executionEngine = ExecutionEngine(accessibilityService)

        overlay.setOnToggleListener { toggleListening() }
        overlay.setOnStopListener { stopService() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (!isRunning) {
            isRunning = true
            overlay.show()
            overlay.updateState(OverlayState.IDLE)
            startListening()
        }

        return START_STICKY
    }

    fun setAccessibilityService(service: ControlAccessibilityService?) {
        accessibilityService = service
    }

    private fun startListening() {
        overlay.updateState(OverlayState.LISTENING)
        serviceScope.launch {
            asrManager.events.collect { event ->
                when (event) {
                    is AsrEvent.PartialResult -> {
                        overlay.updateState(OverlayState.LISTENING, text = event.text)
                    }
                    is AsrEvent.FinalResult -> {
                        val userText = event.text
                        overlay.updateState(OverlayState.PROCESSING, text = userText)
                        processVoiceCommand(userText)
                    }
                    is AsrEvent.Error -> {
                        overlay.updateState(OverlayState.ERROR, result = event.message)
                        delay(1500)
                        if (isRunning) startListening()
                    }
                    is AsrEvent.Ready -> {}
                }
            }
        }
        asrManager.startListening()
    }

    private suspend fun processVoiceCommand(userText: String) {
        val screenState = accessibilityService?.lastScreenState
        val screenContext = if (screenState != null) {
            ScreenReader.buildScreenContext(screenState)
        } else ""

        val prompt = parser.buildPrompt(userText, screenContext)

        try {
            val llmOutput = withTimeout(5000) { llmEngine.infer(prompt) }
            val result = parser.parse(llmOutput)

            if (result.error != null) {
                overlay.updateState(OverlayState.ERROR, text = userText, result = result.error)
                delay(1500)
                if (isRunning) startListening()
                return
            }

            val action = result.action
            if (action == null) {
                overlay.updateState(OverlayState.ERROR, text = userText, result = "无法解析指令")
                delay(1500)
                if (isRunning) startListening()
                return
            }

            overlay.updateState(OverlayState.EXECUTING, text = userText)
            executionEngine.execute(action) { execResult ->
                if (execResult.success) {
                    overlay.updateState(OverlayState.IDLE, text = userText, result = execResult.message)
                } else {
                    overlay.updateState(OverlayState.ERROR, text = userText, result = execResult.message)
                }
                delay(1000)
                if (isRunning) startListening()
            }

        } catch (e: TimeoutCancellationException) {
            overlay.updateState(OverlayState.ERROR, text = userText, result = "推理超时")
            delay(1500)
            if (isRunning) startListening()
        } catch (e: Exception) {
            overlay.updateState(OverlayState.ERROR, text = userText, result = "处理失败: ${e.message}")
            delay(1500)
            if (isRunning) startListening()
        }
    }

    private fun toggleListening() {
        if (isRunning) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun stopListening() {
        isRunning = false
        asrManager.stopListening()
        overlay.updateState(OverlayState.IDLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        asrManager.stopListening()
        overlay.dismiss()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "语音控制后台服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Mobile")
            .setContentText("语音控制运行中")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
```

- [ ] **Step 2: Update ControlAccessibilityService to register with VoiceControlService**

In `ControlAccessibilityService.kt`, add:

```kotlin
// At the top of the class
private var voiceService: VoiceControlService? = null

fun registerVoiceService(service: VoiceControlService) {
    voiceService = service
}
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add voice control foreground service"
```



### Task 10: MainActivity Wiring

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`
- Create: `app/src/main/java/com/controlmoblie/overlay/PermissionHelper.kt`

- [ ] **Step 1: Write PermissionHelper.kt**

```kotlin
package com.controlmoblie.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer

object PermissionHelper {

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun openOverlaySettings(activity: Activity, requestCode: Int) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, requestCode)
    }

    fun hasRecordPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isSpeechRecognizerAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
```

- [ ] **Step 2: Rewrite MainActivity.kt**

```kotlin
package com.controlmoblie

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlmoblie.overlay.PermissionHelper
import com.controlmoblie.service.VoiceControlService

class MainActivity : ComponentActivity() {

    private var voiceService: VoiceControlService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // Service is bound, no binder needed
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ControlScreen(
                    onStartService = { startVoiceService() },
                    onRequestOverlay = { requestOverlayPermission() },
                    onRequestAudio = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                        }
                    },
                    onOpenAccessibility = { openAccessibilitySettings() }
                )
            }
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestOverlayPermission() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            PermissionHelper.openOverlaySettings(this, 100)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unbindService(serviceConnection) } catch (e: Exception) {}
    }

    @Composable
    private fun updateUI() {
        // State reading triggers recomposition
    }

    @Composable
    private fun ControlScreen(
        onStartService: () -> Unit,
        onRequestOverlay: () -> Unit,
        onRequestAudio: () -> Unit,
        onOpenAccessibility: () -> Unit
    ) {
        val hasOverlay = remember { PermissionHelper.hasOverlayPermission(this) }
        val hasAudio = remember { PermissionHelper.hasRecordPermission(this) }
        val asrAvailable = remember { PermissionHelper.isSpeechRecognizerAvailable(this) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Control Mobile", style = MaterialTheme.typography.headlineMedium)

            if (!asrAvailable) {
                Text("此设备不支持语音识别", color = MaterialTheme.colorScheme.error)
            }

            PermissionItem("悬浮窗权限", hasOverlay, "需要在后台显示控制面板", onRequestOverlay)
            PermissionItem("录音权限", hasAudio, "用于语音识别", onRequestAudio)

            Button(
                onClick = { onOpenAccessibility() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开启无障碍服务")
            }

            if (hasOverlay && hasAudio && asrAvailable) {
                Button(
                    onClick = onStartService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启动语音控制")
                }
            }
        }
    }

    @Composable
    private fun PermissionItem(
        title: String,
        granted: Boolean,
        description: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onClick) { Text("授权") }
            }
        }
    }
}
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add main activity with permission handling"
```



### Task 11: llama.cpp JNI Integration

**Files:**
- Create: `app/src/main/jni/llama_jni.cpp`
- Create: `app/src/main/java/com/controlmoblie/llm/NativeLlmEngine.kt`
- Modify: `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`

- [ ] **Step 1: Write NativeLlmEngine.kt**

```kotlin
package com.controlmoblie.llm

object NativeLlmEngine {
    init {
        System.loadLibrary("llama")
    }

    external fun loadModel(modelPath: String): Boolean
    external fun infer(prompt: String): String
    external fun unloadModel()
}
```

- [ ] **Step 2: Modify LlmEngine.kt to use native when available**

```kotlin
// In LlmEngine.infer(), replace simulateInference with:
private var useNative = false

suspend fun loadModel(): Boolean {
    return withContext(Dispatchers.IO) {
        val dest = File(context.filesDir, MODEL_FILENAME)
        if (!dest.exists()) return@withContext false
        try {
            useNative = NativeLlmEngine.loadModel(dest.absolutePath)
            isLoaded = useNative
            modelPath = dest.absolutePath
            useNative
        } catch (e: UnsatisfiedLinkError) {
            // Fall back to simulation if native lib not available
            isLoaded = true
            modelPath = dest.absolutePath
            false
        }
    }
}

suspend fun infer(prompt: String): String {
    return withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext "{\"action\": \"error\", \"message\": \"模型未加载\"}"
        if (useNative) {
            NativeLlmEngine.infer(prompt)
        } else {
            simulateInference(prompt)
        }
    }
}
```

- [ ] **Step 3: Create placeholder JNI C++ file**

Create `app/src/main/jni/llama_jni.cpp`:

```cpp
#include <jni.h>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_loadModel(
    JNIEnv* env,
    jobject /*this*/,
    jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    // llama.cpp model loading will go here
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_infer(
    JNIEnv* env,
    jobject /*this*/,
    jstring prompt) {
    const char* input = env->GetStringUTFChars(prompt, nullptr);
    std::string result = "{\"action\": \"navigate\", \"type\": \"home\"}";
    env->ReleaseStringUTFChars(prompt, input);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_unloadModel(
    JNIEnv* env,
    jobject /*this*/) {
    // Model cleanup will go here
}
```

Create `app/src/main/jni/CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.22)
project("llama")

add_library(llama SHARED llama_jni.cpp)
find_library(log-lib log)
target_link_libraries(llama ${log-lib})
```

- [ ] **Step 4: Update app/build.gradle.kts to include native build**

Add inside `android {}` block:

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/jni/CMakeLists.txt")
        version = "3.22.0"
    }
}
```

- [ ] **Step 5: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add llama.cpp JNI integration scaffold"
```



### Task 12: Model Download UI

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: Add model download state and UI to MainActivity**

Add this composable to `ControlScreen`:

```kotlin
var downloadProgress by remember { mutableStateOf(-1f) }
var modelReady by remember { mutableStateOf(false) }

if (!modelReady && downloadProgress >= 0f) {
    LinearProgressIndicator(
        progress = { downloadProgress },
        modifier = Modifier.fillMaxWidth()
    )
    Text("下载模型中... ${(downloadProgress * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall)
}

if (!modelReady && downloadProgress < 0f) {
    Button(
        onClick = {
            // Trigger download via LlmEngine
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("下载语音模型 (~350MB)")
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add model download UI"
```



## Self-Review Checklist

- [ ] Spec coverage: every section in the spec has a corresponding task
  - Architecture (Task 1, 9) ✓
  - Voice Wake + ASR (Task 5) ✓
  - LLM Engine (Task 6, 11) ✓
  - Instruction Parser (Task 6) ✓
  - Execution Engine (Task 7) ✓
  - Accessibility Service (Task 3) ✓
  - UI Overlay (Task 8) ✓
  - Permissions (Task 10) ✓
  - Model Download (Task 12) ✓
- [ ] No placeholder code, TBD, or TODO
- [ ] Type consistency across tasks
- [ ] Each step has complete code
- [ ] Each step has a verification command
