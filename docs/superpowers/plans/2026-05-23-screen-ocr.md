# 屏幕 OCR 兜底识别 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 executeClick 中增加 OCR 兜底：无障碍找不到 → 坐标映射 → 截图 OCR 识别 → 坐标点击。

**Architecture:** 新增 `ScreenOcr`（Tesseract 引擎），修改 `executeClick` 为三级串联。

**Tech Stack:** Tesseract4Android, AccessibilityService.takeScreenshot()

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `util/ScreenOcr.kt` | 创建 | Tesseract 初始化、截图识别、文字匹配 |
| `service/ControlAccessibilityService.kt` | 修改 | executeClick 增加 OCR 兜底 |
| `gradle/libs.versions.toml` | 修改 | 添加 tesseract4android 版本 |
| `app/build.gradle.kts` | 修改 | 添加 tesseract4android 依赖 |

---

### Task 1: 添加 Tesseract 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 添加版本和依赖**

在 `gradle/libs.versions.toml` 的 `[versions]` 中添加：
```toml
tesseract = "4.9.0"
```

在 `[libraries]` 中添加：
```toml
tesseract-android = { group = "cz.adaptech", name = "tesseract4android", version.ref = "tesseract" }
```

在 `app/build.gradle.kts` 的 `dependencies` 中添加：
```kotlin
implementation(libs.tesseract.android)
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add Tesseract4Android OCR dependency"
```

---

### Task 2: 创建 ScreenOcr

**Files:**
- Create: `app/src/main/java/com/controlmoblie/util/ScreenOcr.kt`

- [ ] **Step 1: 创建 ScreenOcr**

Create `app/src/main/java/com/controlmoblie/util/ScreenOcr.kt`:

```kotlin
package com.controlmoblie.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)

object ScreenOcr {

    private const val TAG = "ScreenOcr"
    private const val TRAINEDDATA_URL = "https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata"
    private const val TRAINEDDATA_NAME = "chi_sim.traineddata"
    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false

    val isReady: Boolean get() = isInitialized

    fun init(context: Context, dataPath: String): Boolean {
        if (isInitialized) return true
        return try {
            val traineddata = File(dataPath, TRAINEDDATA_NAME)
            if (!traineddata.exists()) {
                Log.w(TAG, "Traineddata not found at ${traineddata.absolutePath}")
                return false
            }
            val api = TessBaseAPI()
            api.init(dataPath, "chi_sim")
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "")
            tessApi = api
            isInitialized = true
            Log.d(TAG, "Tesseract initialized with chi_sim")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init failed", e)
            false
        }
    }

    fun recognize(bitmap: Bitmap): List<OcrResult> {
        val api = tessApi ?: return emptyList()
        try {
            api.setImage(bitmap)
            val results = mutableListOf<OcrResult>()
            val words = api.words
            for (word in words) {
                val rect = word.boundingBox
                results.add(
                    OcrResult(
                        text = word.text.trim(),
                        x = (rect.left + rect.right) / 2f,
                        y = (rect.top + rect.bottom) / 2f,
                        width = (rect.right - rect.left).toFloat(),
                        height = (rect.bottom - rect.top).toFloat()
                    )
                )
            }
            api.clear()
            Log.d(TAG, "OCR found ${results.size} text blocks")
            return results
        } catch (e: Exception) {
            Log.e(TAG, "OCR recognize failed", e)
            return emptyList()
        }
    }

    fun release() {
        tessApi?.recycle()
        tessApi = null
        isInitialized = false
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add app/src/main/java/com/controlmoblie/util/ScreenOcr.kt
git commit -m "feat: add ScreenOcr with Tesseract4Android engine"
```

---

### Task 3: 集成 OCR 到 executeClick

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt`

- [ ] **Step 1: 在 onCreate/onServiceConnected 中初始化 OCR**

在 `onServiceConnected` 中添加（`instance = this` 之后）：

```kotlin
val traineddataDir = File(filesDir, "tessdata").apply { mkdirs() }
ScreenOcr.init(this, traineddataDir.absolutePath)
```

添加 import：
```kotlin
import com.controlmoblie.util.ScreenOcr
import java.io.File
```

- [ ] **Step 2: 在 executeClick 坐标映射之后加 OCR 兜底**

找到 executeClick 中坐标映射兜底后面的 `onResult(false, "未找到 ${action.target}")`，替换为 OCR 兜底：

```kotlin
        // fallback 3: OCR
        if (ScreenOcr.isReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "executeClick: trying OCR fallback for '${action.target}'")
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer, screenshot.colorSpace
                        )
                        val results = ScreenOcr.recognize(bitmap ?: return)
                        val match = results.find { it.text.contains(action.target) }
                        if (match != null) {
                            Log.d(TAG, "executeClick: OCR match '${action.target}' at (${match.x}, ${match.y})")
                            performCoordinateClick(match.x.toInt(), match.y.toInt()) { clicked ->
                                onResult(clicked, if (clicked) "已点击 ${action.target}" else "无法点击 ${action.target}")
                            }
                        } else {
                            Log.w(TAG, "executeClick: OCR found no match for '${action.target}'")
                            onResult(false, "未找到 ${action.target}")
                        }
                        screenshot.hardwareBuffer.close()
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "executeClick: screenshot failed, errorCode=$errorCode")
                        onResult(false, "截屏失败")
                    }
                }
            )
            return
        }
```

添加 import：
```kotlin
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityService.TakeScreenshotCallback
import android.view.accessibility.AccessibilityService.ScreenshotResult
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 构建安装**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```
git add app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt
git commit -m "feat: add OCR fallback to executeClick (Tesseract screenshot recognition)"
```

---

### Task 4: 添加训练数据下载

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: 在 initAndStart 中下载训练数据**

VoiceControlService 或 ScreenOcr 中添加下载逻辑。在 `ScreenOcr` 中增加：

```kotlin
suspend fun downloadTraineddata(context: Context, onProgress: (Float) -> Unit): Boolean {
    return withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "tessdata").apply { mkdirs() }
        val dest = File(dir, TRAINEDDATA_NAME)
        if (dest.exists()) return@withContext true

        val tmpFile = File(context.cacheDir, "${TRAINEDDATA_NAME}.tmp")
        try {
            val url = URL(TRAINEDDATA_URL)
            val connection = url.openConnection().apply {
                connectTimeout = 30000
                readTimeout = 60000
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
                            onProgress(totalRead.toFloat() / fileLength)
                        }
                    }
                }
            }
            tmpFile.renameTo(dest)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Traineddata download failed", e)
            false
        } finally {
            tmpFile.delete()
        }
    }
}
```

添加 import：
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
```

- [ ] **Step 2: 编译安装**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add app/src/main/java/com/controlmoblie/util/ScreenOcr.kt
git commit -m "feat: add Tesseract chi_sim traineddata download"
```

---

### Task 5: 全量集成测试

- [ ] **Step 1: 构建**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装测试**

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

打开微信 → 说"点击张三" → 验证 OCR 兜底识别并点击。

---

## 自审

1. **Spec 覆盖:** 三级兜底流程（Task 3）、ScreenOcr 接口（Task 2）、Tesseract 依赖（Task 1）、训练数据下载（Task 4）、集成测试（Task 5）。
2. **无占位符:** 所有代码完整给出。
3. **类型一致:** `OcrResult` 定义在 Task 2，Task 3 使用一致。`ScreenOcr.recognize(Bitmap)` 返回 `List<OcrResult>`。
