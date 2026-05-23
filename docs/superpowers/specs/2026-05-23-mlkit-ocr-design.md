# ML Kit 中文 OCR 替换方案

## 背景
- Tesseract（tess-two）因 traineddata 版本不兼容失败
- PaddleOCR ONNX 方案代码已完成，但模型下载 URL 不存在（sherpa-onnx `ocr-models` release tag 404）
- MIUI 屏蔽微信无障碍文字，OCR 是唯一可用的屏幕识别路径

## 核心决策
用 Google ML Kit 独立包 `text-recognition-chinese` 替换 ONNX Runtime 手写推理，模型随 APK 下发，完全离线。

## 架构

三段式 `executeClick` 不变：
1. Accessibility 文本搜索
2. 微信坐标映射
3. **OCR 截图识别** ← 本方案改动范围

OCR 层接口不变，对上透明：
```kotlin
object ScreenOcr {
    val isReady: Boolean
    fun prepare(context: Context)
    suspend fun recognize(bitmap: Bitmap): List<OcrResult>
    fun release()
}
```

## 依赖变更

新增：
```toml
mlkit-text-recognition-chinese = { group = "com.google.mlkit", name = "text-recognition-chinese", version = "16.0.1" }
```

可删除（当前未被使用）：
```toml
tesseract-android = { group = "com.rmtheis", name = "tess-two", version = "9.1.0" }
```

ONNX Runtime 保留不动（sherpa-onnx ASR/TTS 依赖）。

## 识别流程

1. `init()` → `TextRecognizer.getClient(ChineseTextRecognizerOptions.Builder().build())`
2. `recognize(bitmap)`:
   - `InputImage.fromBitmap(bitmap, 0)`
   - `recognizer.process(image)` → `Task<VisionText>`
   - `VisionText.textBlocks → lines → elements`，提取 `text + boundingBox` → `OcrResult`
3. 返回 `List<OcrResult>`，空列表 = 未找到文字

首次调用时 ML Kit 自动解压模型到 `filesDir/com.google.mlkit/`，后续纯本地推理。

## 错误处理

- `recognize()` 用 `suspendCancellableCoroutine` 桥接回调式 API
- `boundingBox` null-guard
- `addOnFailureListener` → `resume(emptyList())`
- 上层 `executeClick` 收到空列表走已有"未找到"反馈

## 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 重写 | `util/ScreenOcr.kt` | ONNX 代码替换为 ML Kit |
| 删除 | `util/OcrDict.kt` | 不再需要 |
| 修改 | `app/build.gradle.kts` | 加 ML Kit 依赖 |
| 修改 | `gradle/libs.versions.toml` | 加 ML Kit 版本 |
| 修改 | `service/ControlAccessibilityService.kt` | `init(modelDir)` → `init(context)` |
| 修改 | `MainActivity.kt` | 下载按钮改为初始化按钮 |
| 不改 | `model/OcrResult.kt`（如果有） | 数据结构不变 |
| 不改 | `executeClick` 三段逻辑 | 接口兼容 |
