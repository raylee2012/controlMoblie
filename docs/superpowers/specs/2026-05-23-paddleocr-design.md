# PaddleOCR 屏幕识别 — 设计方案

日期: 2026-05-23

## 概述

用 PaddleOCR 替换 Tesseract 做屏幕文字识别。PaddleOCR 是中文 OCR 领域准确率最高的方案，通过 ONNX Runtime 运行，彻底跳过文本无障碍限制。

## 模型

| 文件 | 大小 | 用途 |
|------|------|------|
| `det.onnx` | ~5MB | 文字检测（DB 算法） |
| `rec.onnx` | ~10MB | 文字识别（CRNN + CTC） |
| `dict.txt` | ~1MB | 中文字典（6000+ 字符） |

下载源：`https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/`

模型库存放：`context.filesDir/paddle-ocr/`

## 架构

```
ScreenOcr (重写)
  ├── OrtSession (det) — 文字检测
  ├── OrtSession (rec) — 文字识别
  ├── detect(bitmap) → List<Rect>
  ├── recognize(bitmap, rects) → List<OcrResult>
  └── recognize(bitmap) → List<OcrResult>  // 对外接口
```

## 新增依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.18.0 | ONNX Runtime Java API |

需处理 libonnxruntime.so 冲突：sherpa-onnx AAR 自带一份，onnxruntime-android 也带一份。通过 Gradle `packagingOptions { pickFirst }` 解决。

## 后处理

- **文字检测**：DB 后处理 — 阈值二值化 → 找连通域 → 收缩为矩形框
- **文字识别**：CRNN CTC 贪婪解码 — argmax 取最高概率字符 → 映射到字典

后处理代码约 200 行 Kotlin，参考 PaddleOCR 官方实现简化。

## 修改文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `gradle/libs.versions.toml` | 修改 | 新增 onnxruntime 版本 |
| `app/build.gradle.kts` | 修改 | 新增依赖 + packaging 冲突处理 |
| `util/ScreenOcr.kt` | 重写 | Tesseract → PaddleOCR ONNX |
| `service/ControlAccessibilityService.kt` | 不变 | 接口兼容 |

## 性能预估

| 步骤 | 耗时 |
|------|------|
| 检测推理 | ~100ms |
| 识别推理（10 个文字块）| ~300ms |
| 后处理 | ~50ms |
| 总计 | ~450ms |

加上截图 ~50ms，单次 OCR 约 0.5 秒。比 Tesseract 快 2-3 倍。

## 错误处理

- 模型未下载 → `isReady = false`，OCR 跳过
- ONNX 推理异常 → 返回空列表，不影响主流程
- 检测到 0 个文字块 → 返回空列表

## 不在范围内

- GPU/OpenCL 加速（CPU 已够快）
- 多角度文字检测（手机屏幕都是正向）
- 实时 OCR（按需单帧）
