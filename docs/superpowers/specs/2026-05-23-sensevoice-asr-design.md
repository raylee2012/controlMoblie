# SenseVoiceSmall ASR — 设计规范

日期: 2026-05-23

## 概述

将 Vosk ASR（`vosk-model-small-cn-0.22`，42MB）替换为 sherpa-onnx + SenseVoiceSmall 模型（~230MB），显著提升带口音普通话的识别效果。除 ASR 引擎层外，不做任何架构变更 — 现有的事件流、悬浮窗集成和 TTS 管线保持不变。

## 动机

- 当前 Vosk 小型中文模型对口音/方言普通话的识别准确率较差
- SenseVoiceSmall 由阿里达摩院专门针对多样化中文语音（包括地方口音）训练
- sherpa-onnx 已在项目中集成（用于 TTS）— 无需额外引入原生依赖

## 模型

| 属性 | 值 |
|------|-----|
| 名称 | SenseVoiceSmall |
| 引擎 | sherpa-onnx OnlineRecognizer |
| 大小 | ~230MB（ONNX 格式） |
| 语言 | zh, en, yue, ko, ja |
| 流式 | 是（chunk-based，延迟约 400ms） |
| 特性 | 标点符号、逆文本归一化（ITN） |
| 下载 | GitHub Releases / HuggingFace 镜像 |

模型文件：
- `model.onnx` — encoder + decoder + joiner（~230MB）
- `tokens.txt` — tokenizer 词表

## 架构

```
                         ┌─────────────────┐
                         │ VoiceControlService │  ← 不变
                         └────────┬────────┘
                                  │ AsrEvent（sealed class，不变）
                         ┌────────▼────────┐
                         │ SpeechRecognizerManager │  ← 内部重写
                         └────────┬────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │                           │
            ┌───────▼──────┐           ┌───────▼──────┐
            │ SenseVoiceModelManager │  │ OnlineRecognizer │
            │  - 下载                   │   │ (sherpa-onnx)    │
            │  - 解压                   │   │  - createStream  │
            │  - 模型目录               │   │  - acceptWaveform│
            └─────────────────────────┘   │  - callbacks     │
                                          └──────────────────┘
```

## 文件

| 文件 | 操作 | 职责 |
|------|------|------|
| `asr/SenseVoiceModelManager.kt` | 新建 | 下载并解压 SenseVoiceSmall 模型 |
| `asr/SpeechRecognizerManager.kt` | 重写 | Vosk → sherpa-onnx OnlineRecognizer |
| `service/VoiceControlService.kt` | 小幅修改 | 将模型初始化指向 SenseVoiceModelManager |
| `MainActivity.kt` | 小幅修改 | UI：SenseVoice 下载按钮 + 进度条 |
| `asr/VoskModelManager.kt` | 保留（已弃用） | 保留用于参考，默认不再使用 |

## SpeechRecognizerManager（重写的核心）

```kotlin
class SpeechRecognizerManager(private val modelPath: String) {
    // 相同的事件类型：AsrEvent（不变）
    // 使用 sherpa-onnx OnlineRecognizer

    fun init(): Boolean          // 加载模型，创建识别器
    fun startListening()        // 创建 stream，开始接收音频
    fun stopListening()         // 销毁 stream
    fun release()               // 释放识别器

    // 内部：AudioRecord 采集 PCM 16kHz 单声道音频，
    // 通过 recognizer.acceptWaveform() 送入音频块，
    // callbacks 映射到 AsrEvent.FinalResult / PartialResult
}
```

### Callback 映射

| sherpa-onnx callback | AsrEvent |
|----------------------|----------|
| `onResult(text, isFinal=true)` | `AsrEvent.FinalResult(text)` |
| `onResult(text, isFinal=false)` | `AsrEvent.PartialResult(text)` |

## SenseVoiceModelManager

模式参照 `VoskModelManager`：
- `isModelReady(context): Boolean` — 检查 `model.onnx` 是否存在
- `getModelPath(context): String` — 返回模型目录路径
- `downloadAndExtract(context, onProgress): Boolean` — 下载 tar.bz2，解压至 `filesDir/sense-voice-small-onnx/`

模型下载地址：`https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-small-zh-en-ja-ko-yue-2025-03-27.tar.bz2`

## VoiceControlService 变更

最小化 — 将 Vosk 引用替换为 SenseVoice：

```kotlin
// initAndStart()
if (!SenseVoiceModelManager.isModelReady(this)) { ... 报错 ... }
val modelPath = SenseVoiceModelManager.getModelPath(this)
val manager = SpeechRecognizerManager(modelPath)
// ...其余不变
```

## MainActivity 变更

新增 SenseVoice 下载 UI 区域（参照现有 Vosk UI 模式）：
- "下载语音识别模型 (~230MB)" 按钮
- 下载进度条
- "语音识别 ✓" 状态指示

现有 Vosk UI 区域保留但作为次要选项（用户可保留两者或手动删除旧模型）。

## 错误处理

- 模型未下载 → 跳过 ASR 初始化，显示"语音模型未下载"
- 模型加载失败 → 在悬浮窗中显示错误，降级提示
- 识别错误 → AsrEvent.Error，由 VoiceControlService 处理（沿用现有流程）
- 所有错误均为非致命性；悬浮窗仍显示状态，TTS 仍可正常使用

## 并发

- 识别器运行在独立线程中（sherpa-onnx 内部处理）
- AudioRecord 在后台线程中送入 PCM 音频块
- Callbacks 将结果发送到 AsrEvent channel（CONFLATED，与 Vosk 一致）
- 不改变 VoiceControlService 的协程模型

## 迁移路径

1. Vosk .so 库（`libvosk.so`）保留在 jniLibs 中 — 无冲突、无副作用
2. Vosk 模型文件保留在设备上 — 用户可手动删除以释放空间
3. `VoskModelManager.kt` 保留在代码库中 — 不会被活跃代码引用
4. 如需切换回 Vosk，只需切换 `initAndStart()` 中使用的 model manager 即可

## 非目标

- 多模型热切换（保持简单：同一时间只用一个 ASR 模型）
- Vosk 模型自动清理（用户自行管理磁盘空间）
- GPU/CUDA 加速（仅 CPU，与 TTS 方案一致）
