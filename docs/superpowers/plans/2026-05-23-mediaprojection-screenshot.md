# MediaProjection 屏幕截图替换实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 MediaProjection + ImageReader 替换 AccessibilityService.takeScreenshot()，使 OCR 截图兼容 Android 13（API 33）及更低版本。

**Architecture：** `ScreenCaptureManager` 单例持有 `MediaProjection` 和 `ImageReader`，`MainActivity` 负责用户授权，`ControlAccessibilityService` 在 OCR fallback 中调用 `capture()` 获取 Bitmap。移除 `Build.VERSION.SDK_INT >= 34` 版本限制。

**Tech Stack：** MediaProjection API、ImageReader、VirtualDisplay、ActivityResultContracts

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 创建 | `app/src/main/java/com/controlmoblie/util/ScreenCaptureManager.kt` | MediaProjection + ImageReader 包装 |
| 创建 | `app/src/test/java/com/controlmoblie/util/ScreenCaptureManagerTest.kt` | 单元测试（状态管理） |
| 修改 | `app/src/main/AndroidManifest.xml` | 添加 FOREGROUND_SERVICE 权限 |
| 修改 | `app/src/main/java/com/controlmoblie/MainActivity.kt` | 请求屏幕录制权限并初始化 |
| 修改 | `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt` | OCR fallback 改用 ScreenCaptureManager |

---

### Task 1: 添加权限声明

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在 `AndroidManifest.xml` 的 `<manifest>` 标签内添加权限**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

（已有 `FOREGROUND_SERVICE_MICROPHONE`，再添加通用 `FOREGROUND_SERVICE` 权限。MediaProjection 在 Android 10+ 后台捕获时需要。）

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "build: add FOREGROUND_SERVICE permission for MediaProjection"
```

---

### Task 2: 创建 ScreenCaptureManagerTest（RED）

**Files:**
- Create: `app/src/test/java/com/controlmoblie/util/ScreenCaptureManagerTest.kt`

- [ ] **Step 1: 写入失败测试**

```kotlin
package com.controlmoblie.util

import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureManagerTest {

    @Test
    fun `isReady returns false before init`() {
        assertFalse("should not be ready before init", ScreenCaptureManager.isReady)
    }

    @Test
    fun `isReady true after init with bypass`() {
        ScreenCaptureManager.release()
        ScreenCaptureManager.bypassInit = true
        ScreenCaptureManager.init(null, 1080, 2400, 480)
        assertTrue("isReady should be true after init", ScreenCaptureManager.isReady)
    }

    @Test
    fun `release clears state`() {
        ScreenCaptureManager.bypassInit = true
        ScreenCaptureManager.init(null, 1080, 2400, 480)
        ScreenCaptureManager.release()
        assertFalse("isReady should be false after release", ScreenCaptureManager.isReady)
    }

    @Test
    fun `capture returns null when not ready`() {
        ScreenCaptureManager.release()
        assertNull("capture should return null when not ready", ScreenCaptureManager.capture())
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
.\gradlew :app:testDebugUnitTest --tests com.controlmoblie.util.ScreenCaptureManagerTest
```
Expected: COMPILE ERROR（ScreenCaptureManager 不存在）或运行时错误。

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/controlmoblie/util/ScreenCaptureManagerTest.kt
git commit -m "test: add ScreenCaptureManagerTest (RED)"
```

---

### Task 3: 实现 ScreenCaptureManager（GREEN）

**Files:**
- Create: `app/src/main/java/com/controlmoblie/util/ScreenCaptureManager.kt`

- [ ] **Step 1: 实现最小代码**

```kotlin
package com.controlmoblie.util

import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import androidx.annotation.VisibleForTesting

object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    var isReady: Boolean = false
        private set

    @VisibleForTesting
    var bypassInit: Boolean = false

    fun init(projection: MediaProjection?, width: Int, height: Int, density: Int) {
        if (isReady) return
        try {
            if (bypassInit) {
                isReady = true
                Log.d(TAG, "Init bypassed (test mode)")
                return
            }
            if (projection == null) {
                Log.e(TAG, "MediaProjection is null")
                return
            }
            mediaProjection = projection
            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            isReady = true
            Log.d(TAG, "ScreenCaptureManager initialized: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "ScreenCaptureManager init failed", e)
            isReady = false
        }
    }

    fun capture(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            null
        } finally {
            image.close()
        }
    }

    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        isReady = false
        bypassInit = false
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

```bash
.\gradlew :app:testDebugUnitTest --tests com.controlmoblie.util.ScreenCaptureManagerTest
```
Expected: 4/4 PASS。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/controlmoblie/util/ScreenCaptureManager.kt
git commit -m "feat: add ScreenCaptureManager with MediaProjection (GREEN)"
```

---

### Task 4: 更新 MainActivity 请求屏幕录制权限

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: 添加导入**

```kotlin
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContracts
```

- [ ] **Step 2: 注册屏幕录制权限 launcher**

在 `MainActivity` 类中，`overlayLauncher` 之后添加：

```kotlin
val projectionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == RESULT_OK && result.data != null) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
        if (projection != null) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            ScreenCaptureManager.init(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            ScreenOcr.init()
            ocrReady = ScreenOcr.isReady && ScreenCaptureManager.isReady
        }
    }
}
```

- [ ] **Step 3: 修改「初始化 OCR 识别」按钮逻辑**

将现有的 `if (!ocrReady) { Button(...) { ScreenOcr.init(); ocrReady = ScreenOcr.isReady } }` 替换为：

```kotlin
if (!ocrReady) {
    Button(
        onClick = {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("初始化 OCR 识别")
    }
}
```

- [ ] **Step 4: 编译检查**

```bash
.\gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/controlmoblie/MainActivity.kt
git commit -m "feat: request MediaProjection permission in MainActivity"
```

---

### Task 5: 更新 ControlAccessibilityService OCR fallback

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt`

- [ ] **Step 1: 移除 API 34 版本限制，改用 ScreenCaptureManager**

将 OCR fallback 代码块（第 217-258 行）：

```kotlin
// fallback 3: OCR screenshot
root.recycle()
if (!ScreenOcr.isReady) {
    ScreenOcr.init()
    Log.d(TAG, "OCR lazy init: isReady=${ScreenOcr.isReady}")
}
if (ScreenOcr.isReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    Log.d(TAG, "executeClick: trying OCR fallback for '${action.target}'")
    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
        override fun onSuccess(screenshot: ScreenshotResult) {
            val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
            if (bitmap == null) {
                screenshot.hardwareBuffer.close()
                onResult(false, "截屏失败")
                return
            }
            CoroutineScope(Dispatchers.Main).launch {
                val results = ScreenOcr.recognize(bitmap)
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
        }
        override fun onFailure(errorCode: Int) {
            Log.e(TAG, "executeClick: screenshot failed, errorCode=$errorCode")
            onResult(false, "截屏失败")
        }
    })
    return
}
```

替换为：

```kotlin
// fallback 3: OCR screenshot
root.recycle()
if (!ScreenOcr.isReady) {
    ScreenOcr.init()
    Log.d(TAG, "OCR lazy init: isReady=${ScreenOcr.isReady}")
}
if (!ScreenCaptureManager.isReady) {
    Log.w(TAG, "ScreenCaptureManager not ready, skipping OCR fallback")
}
if (ScreenOcr.isReady && ScreenCaptureManager.isReady) {
    Log.d(TAG, "executeClick: trying OCR fallback for '${action.target}'")
    val bitmap = ScreenCaptureManager.capture()
    if (bitmap == null) {
        onResult(false, "截屏失败")
        return
    }
    CoroutineScope(Dispatchers.Main).launch {
        val results = ScreenOcr.recognize(bitmap)
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
    }
    return
}
```

- [ ] **Step 2: 删除已不需要的 import**

删除：
```kotlin
import android.hardware.HardwareBuffer
import android.view.Display
```

添加：
```kotlin
import com.controlmoblie.util.ScreenCaptureManager
```

- [ ] **Step 3: 编译检查**

```bash
.\gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/controlmoblie/service/ControlAccessibilityService.kt
git commit -m "refactor: replace takeScreenshot with ScreenCaptureManager for API 21+ compatibility"
```

---

### Task 6: 构建验证

- [ ] **Step 1: 完整编译**

```bash
.\gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 运行单元测试**

```bash
.\gradlew :app:testDebugUnitTest
```
Expected: ScreenOcrTest 4/4 PASS，ScreenCaptureManagerTest 4/4 PASS。

- [ ] **Step 3: Commit（如有额外修改）**

```bash
git add -A
git commit -m "chore: final build verification after MediaProjection integration"
```

---

## 自检

- **Spec 覆盖**：MediaProjection 权限、ImageReader 捕获、MainActivity 授权、Service 集成、版本限制移除 —— 全部覆盖。
- **Placeholder 扫描**：无 TBD/TODO。
- **类型一致性**：`ScreenCaptureManager.isReady`、`init()`、`capture()`、`release()` 在所有任务中签名一致。
