# 屏幕 OCR 兜底识别 — 设计方案

日期: 2026-05-23

## 概述

在 MIUI 屏蔽微信无障碍文字的情况下，增加屏幕 OCR 作为兜底方案。执行流程为：无障碍查找 → 坐标映射 → OCR 识别，三级串联，确保微信界面操作可用。

## 动机

- MIUI 系统级屏蔽微信的无障碍节点树，findAccessibilityNodeInfosByText 永远返回空
- 坐标映射只覆盖了 4 个底部 Tab，无法处理聊天列表、朋友圈内容等动态页面
- 需要一种通用的"看屏幕"能力，不依赖节点树

## 架构

```
用户语音指令 → Click(target)
        ↓
① 无障碍查找: findAccessibilityNodeInfosByText(target)
        ↓ 找到？YES → 无障碍点击 → 结束
        ↓ NO
② 坐标映射: wechatTabPositions[target]
        ↓ 找到？YES → dispatchGesture(x, y) → 结束
        ↓ NO
③ OCR 兜底: takeScreenshot() → ScreenOcr.recognize(bitmap)
        ↓ 找到？YES → dispatchGesture(x, y) → 结束
        ↓ NO → 返回"未找到 xxx"
```

## 新增文件

| 文件 | 职责 |
|------|------|
| `util/ScreenOcr.kt` | PaddleOCR 模型管理、截屏处理、文字识别、坐标匹配 |

## 修改文件

| 文件 | 改动 |
|------|------|
| `service/ControlAccessibilityService.kt` | executeClick 增加 OCR 兜底分支 |
| MainActivity | （可选）OCR 模型下载按钮 |

## PaddleOCR 模型

使用 sherpa-onnx 的 ONNX Runtime 加载，两个文件：

| 文件 | 大小 | 用途 |
|------|------|------|
| `det.onnx` | ~5MB | 文字检测 |
| `rec.onnx` | ~10MB | 文字识别 |

下载 URL：https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/

模型库存放位置：`context.filesDir/paddle-ocr/`

## ScreenOcr 接口

```kotlin
object ScreenOcr {
    fun init(modelDir: String): Boolean
    fun recognize(screenshot: Bitmap): List<OcrResult>
    fun isReady(): Boolean
    fun release()
}

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)
```

recognize 内部流程：
1. 传入 Bitmap → 缩放到模型输入尺寸
2. 文字检测 ONNX 推理 → 输出文字块边界框
3. 对每个文字块 → 裁剪 → 文字识别 ONNX 推理 → 输出文字
4. 返回所有 OcrResult

## 截图方式

使用 `AccessibilityService.takeScreenshot()`（API 34+），无障碍服务自带截屏能力，无需额外权限。

```kotlin
takeScreenshot(displayId, executor, callback) {
    val bitmap = it // Bitmap with hardware buffer
    val results = ScreenOcr.recognize(bitmap)
    // ...
}
```

## 执行流程变化

executeClick 改造为三阶段兜底：

```kotlin
fun executeClick(action: Action.Click, onResult: (Boolean, String) -> Unit) {
    // Stage 1: Accessibility text search
    val clickable = findClickableByText(root, action.target)
    if (clickable != null) { /* click and return */ }

    // Stage 2: WeChat tab coordinate mapping
    val tabIndex = wechatTabPositions[action.target]
    if (tabIndex != null) { /* coordinate click and return */ }

    // Stage 3: OCR fallback
    if (ScreenOcr.isReady()) {
        takeScreenshot(displayId, executor) { bitmap ->
            val results = ScreenOcr.recognize(bitmap)
            val match = results.find { it.text.contains(action.target) }
            if (match != null) {
                performCoordinateClick(match.x, match.y) { clicked ->
                    onResult(clicked, ...)
                }
            } else {
                onResult(false, "未找到 ${action.target}")
            }
        }
    } else {
        onResult(false, "未找到 ${action.target}")
    }
}
```

## 性能

| 步骤 | 耗时 |
|------|------|
| 截屏 | ~50ms |
| 文字检测 | ~100ms |
| 文字识别（10-20 块）| ~500ms |
| **总计** | **~650ms** |

加上语音识别和指令匹配，整体响应在 1.5-2 秒内。

## 错误处理

- OCR 模型未下载 → 跳过 OCR，仅走前两级兜底
- 截屏失败 → 降级到坐标映射
- OCR 识别无匹配 → 返回"未找到 xxx"
- 所有错误不影响语音控制主流程

## 不在范围内

- 实时 OCR 流（每帧都识别）
- 截屏保存到相册
- 多语言 OCR（仅中文）
- 复杂手势（长按、双击）
