# PaddleOCR 屏幕识别 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 PaddleOCR ONNX 模型替代 Tesseract，实现屏幕文字检测+识别，接入 executeClick OCR 兜底。

**Architecture:** ONNX Runtime Java API 跑 det.onnx + rec.onnx，Kotlin 后处理，接口兼容现有 ScreenOcr。

**Tech Stack:** ONNX Runtime 1.18.0, PaddleOCR DB+CRNN, Android Bitmap, AccessibilityService.takeScreenshot()

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `gradle/libs.versions.toml` | 修改 | onnxruntime 版本 |
| `app/build.gradle.kts` | 修改 | 依赖 + .so 冲突处理 |
| `util/ScreenOcr.kt` | 重写 | PaddleOCR ONNX 引擎 |
| `util/OcrDict.kt` | 创建 | 中文字典加载（rec 模型词汇表） |

---

### Task 1: 添加 ONNX Runtime 依赖并处理 .so 冲突

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 添加版本和依赖**

在 `gradle/libs.versions.toml`：
```toml
onnxruntime = "1.18.0"
```

```toml
onnxruntime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxruntime" }
```

在 `app/build.gradle.kts` 的 `android` 块中添加 packaking 配置：
```kotlin
android {
    ...
    packagingOptions {
        resources {
            pickFirsts += setOf("lib/arm64-v8a/libonnxruntime.so", "lib/armeabi-v7a/libonnxruntime.so", "lib/x86/libonnxruntime.so", "lib/x86_64/libonnxruntime.so")
        }
    }
}
```

在 `dependencies` 中添加：
```kotlin
implementation(libs.onnxruntime.android)
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add ONNX Runtime Android dependency with .so conflict resolution"
```

---

### Task 2: 创建中文字典加载（OcrDict）

**Files:**
- Create: `app/src/main/java/com/controlmoblie/util/OcrDict.kt`

- [ ] **Step 1: 创建 OcrDict**

Create `app/src/main/java/com/controlmoblie/util/OcrDict.kt`:

```kotlin
package com.controlmoblie.util

import java.io.File

object OcrDict {
    private var chars: List<String> = emptyList()

    fun load(path: String) {
        val lines = File(path).readLines()
        chars = lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) trimmed else null
        }
    }

    fun decode(indices: IntArray): String {
        if (chars.isEmpty()) return ""
        val sb = StringBuilder()
        var lastIdx = -1
        for (idx in indices) {
            if (idx in chars.indices && idx != lastIdx) {
                sb.append(chars[idx])
            }
            lastIdx = idx
        }
        return sb.toString()
    }

    val size: Int get() = chars.size
    val isLoaded: Boolean get() = chars.isNotEmpty()
}
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add app/src/main/java/com/controlmoblie/util/OcrDict.kt
git commit -m "feat: add OcrDict for PaddleOCR character vocabulary"
```

---

### Task 3: 重写 ScreenOcr 为 PaddleOCR ONNX 引擎

**Files:**
- Rewrite: `app/src/main/java/com/controlmoblie/util/ScreenOcr.kt`

- [ ] **Step 1: 用 PaddleOCR ONNX 重写 ScreenOcr**

Replace `app/src/main/java/com/controlmoblie/util/ScreenOcr.kt`:

```kotlin
package com.controlmoblie.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.FloatBuffer

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)

object ScreenOcr {

    private const val TAG = "ScreenOcr"
    private const val DET_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/det.onnx"
    private const val REC_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/rec.onnx"
    private const val DICT_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/dict.txt"
    private const val MODEL_DIR = "paddle-ocr"

    private var env: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var isInitialized = false

    val isReady: Boolean get() = isInitialized

    fun init(modelDir: String): Boolean {
        if (isInitialized) return true
        return try {
            val detFile = File(modelDir, "det.onnx")
            val recFile = File(modelDir, "rec.onnx")
            val dictFile = File(modelDir, "dict.txt")
            if (!detFile.exists() || !recFile.exists() || !dictFile.exists()) {
                Log.w(TAG, "Model files not found")
                return false
            }
            OcrDict.load(dictFile.absolutePath)
            env = OrtEnvironment.getEnvironment()
            detSession = env!!.createSession(detFile.absolutePath)
            recSession = env!!.createSession(recFile.absolutePath)
            isInitialized = true
            Log.d(TAG, "PaddleOCR initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR init failed", e)
            false
        }
    }

    fun recognize(bitmap: Bitmap): List<OcrResult> {
        if (!isInitialized) return emptyList()
        return try {
            // detect text regions
            val boxes = detect(bitmap)
            if (boxes.isEmpty()) return emptyList()
            // recognize text in each region
            recognizeText(bitmap, boxes)
        } catch (e: Exception) {
            Log.e(TAG, "OCR recognize failed", e)
            emptyList()
        }
    }

    private fun detect(bitmap: Bitmap): List<FloatArray> {
        val det = detSession ?: return emptyList()
        val scaled = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val input = preprocessDet(scaled)
        val result = det.run(mapOf("x" to input))
        val output = result[0].value as Array<FloatArray>
        result.close()
        input.close()
        return postprocessDet(output[0], bitmap.width, bitmap.height)
    }

    private fun preprocessDet(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val data = FloatArray(1 * 3 * h * w)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            data[i * 3] = r
            data[i * 3 + 1] = g
            data[i * 3 + 2] = b
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    private fun postprocessDet(probMap: FloatArray, origW: Int, origH: Int): List<FloatArray> {
        val h = 640
        val w = 640
        val mask = BooleanArray(h * w)
        for (i in probMap.indices) {
            mask[i] = probMap[i] > 0.3f
        }
        // find connected components (simple BFS)
        val visited = BooleanArray(h * w)
        val boxes = mutableListOf<FloatArray>()
        val ratioX = origW.toFloat() / w
        val ratioY = origH.toFloat() / h

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (mask[idx] && !visited[idx]) {
                    // BFS to find component bounds
                    val queue = ArrayDeque<Int>()
                    queue.add(idx)
                    visited[idx] = true
                    var minX = x; var maxX = x
                    var minY = y; var maxY = y

                    while (queue.isNotEmpty()) {
                        val cur = queue.removeFirst()
                        val cx = cur % w
                        val cy = cur / w
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy
                        for ((dx, dy) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)) {
                            val nx = cx + dx; val ny = cy + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                val ni = ny * w + nx
                                if (mask[ni] && !visited[ni]) {
                                    visited[ni] = true
                                    queue.add(ni)
                                }
                            }
                        }
                    }

                    if ((maxX - minX) * (maxY - minY) >= 20) {
                        boxes.add(floatArrayOf(
                            minX * ratioX, minY * ratioY,
                            maxX * ratioX, maxY * ratioY
                        ))
                    }
                }
            }
        }
        return boxes
    }

    private fun recognizeText(bitmap: Bitmap, boxes: List<FloatArray>): List<OcrResult> {
        val rec = recSession ?: return emptyList()
        val results = mutableListOf<OcrResult>()

        for (box in boxes) {
            val left = maxOf(0f, box[0] - 2f).toInt()
            val top = maxOf(0f, box[1] - 2f).toInt()
            val right = minOf(bitmap.width.toFloat(), box[2] + 2f).toInt()
            val bottom = minOf(bitmap.height.toFloat(), box[3] + 2f).toInt()
            if (right <= left || bottom <= top) continue

            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            val input = preprocessRec(crop)
            val result = rec.run(mapOf("x" to input))
            val output = result[0].value as Array<FloatArray>
            result.close()
            input.close()
            crop.recycle()

            val text = decodeRecOutput(output)
            if (text.isNotBlank()) {
                results.add(OcrResult(
                    text = text,
                    x = (left + right) / 2f,
                    y = (top + bottom) / 2f,
                    width = (right - left).toFloat(),
                    height = (bottom - top).toFloat()
                ))
            }
        }
        return results
    }

    private fun preprocessRec(bitmap: Bitmap): OnnxTensor {
        val targetH = 32
        val ratio = targetH.toFloat() / bitmap.height
        val targetW = maxOf(8, (bitmap.width * ratio).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val pixels = IntArray(targetW * targetH)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        scaled.recycle()
        val data = FloatArray(1 * 3 * targetH * targetW)
        for (i in pixels.indices) {
            val px = pixels[i]
            data[i * 3] = ((px shr 16) and 0xFF) / 255f
            data[i * 3 + 1] = ((px shr 8) and 0xFF) / 255f
            data[i * 3 + 2] = (px and 0xFF) / 255f
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))
    }

    private fun decodeRecOutput(output: Array<FloatArray>): String {
        if (output.isEmpty()) return ""
        val t = output.size       // time steps
        val c = output[0].size    // vocab size
        val indices = IntArray(t)
        for (i in 0 until t) {
            var maxIdx = 0
            var maxVal = Float.MIN_VALUE
            for (j in 0 until c) {
                if (output[i][j] > maxVal) {
                    maxVal = output[i][j]
                    maxIdx = j
                }
            }
            indices[i] = maxIdx
        }
        return OcrDict.decode(indices)
    }

    fun release() {
        detSession?.close()
        recSession?.close()
        env?.close()
        detSession = null
        recSession = null
        env = null
        isInitialized = false
    }

    suspend fun downloadModels(context: Context, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
            val files = listOf(
                "det.onnx" to DET_URL,
                "rec.onnx" to REC_URL,
                "dict.txt" to DICT_URL,
            )
            var allOk = true
            var downloaded = 0
            for ((name, urlStr) in files) {
                val dest = File(dir, name)
                if (dest.exists()) { downloaded++; continue }
                val tmp = File(context.cacheDir, "$name.tmp")
                try {
                    val url = URL(urlStr)
                    val conn = url.openConnection().apply { connectTimeout = 30000; readTimeout = 60000 }
                    conn.connect()
                    conn.getInputStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(8192)
                            var total = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                output.write(buf, 0, n)
                                total += n
                            }
                        }
                    }
                    tmp.renameTo(dest)
                    downloaded++
                    withContext(Dispatchers.Main) { onProgress(downloaded.toFloat() / files.size) }
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: $name", e)
                    tmp.delete()
                    allOk = false
                }
            }
            allOk
        }
    }

    fun isModelReady(context: Context): Boolean {
        val dir = File(context.filesDir, MODEL_DIR)
        return File(dir, "det.onnx").exists() &&
               File(dir, "rec.onnx").exists() &&
               File(dir, "dict.txt").exists()
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add app/src/main/java/com/controlmoblie/util/ScreenOcr.kt
git commit -m "feat: rewrite ScreenOcr with PaddleOCR ONNX Runtime engine"
```

---

### Task 4: 更新初始化路径和下载按钮

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt`
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: 更新 ControlAccessibilityService 初始化**

在 `onServiceConnected` 中修改：
```kotlin
val modelDir = File(filesDir, "paddle-ocr").apply { mkdirs() }
val ok = ScreenOcr.init(modelDir.absolutePath)
Log.d(TAG, "OCR init result=$ok, isReady=${ScreenOcr.isReady}")
```

executeClick 中的 lazy init 也改为：
```kotlin
if (!ScreenOcr.isReady) {
    ScreenOcr.init(File(filesDir, "paddle-ocr").absolutePath)
    Log.d(TAG, "OCR lazy init: isReady=${ScreenOcr.isReady}")
}
```

- [ ] **Step 2: 更新 MainActivity 下载按钮**

将 `ScreenOcr.downloadTraineddata` 改为 `ScreenOcr.downloadModels`：
```kotlin
var ocrModelReady by remember { mutableStateOf(ScreenOcr.isModelReady(this@MainActivity)) }
// ...
val success = ScreenOcr.downloadModels(this@MainActivity) { progress ->
    ocrDownloadProgress = progress
}
```

按钮文字改为 `"下载OCR识别模型 (~16MB)"`

- [ ] **Step 3: 编译构建**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```
git add app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt app/src/main/java/com/controlmoblie/MainActivity.kt
git commit -m "feat: update ScreenOcr init and download for PaddleOCR models"
```

---

### Task 5: 全量集成测试

- [ ] **Step 1: 构建**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 卸载旧版安装**

```
adb uninstall com.controlmoblie
adb install app\build\outputs\apk\debug\app-debug.apk
```

- [ ] **Step 3: 设备测试**

下载 OCR 模型 → 打开微信 → 说"点击公众号" → 验证 OCR 识别并点击

---

## 自审

1. **Spec 覆盖:** ONNX Runtime 依赖（Task 1）、OcrDict 字典（Task 2）、ScreenOcr 引擎（Task 3）、集成（Task 4）、测试（Task 5）
2. **无占位符:** 所有模型 URL 和代码完整
3. **类型一致:** OcrResult、ScreenOcr 接口名不变，executeClick 调用兼容
