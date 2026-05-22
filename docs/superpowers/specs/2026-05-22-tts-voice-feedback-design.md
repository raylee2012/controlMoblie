# TTS 语音反馈设计

日期: 2026-05-22

## 概述

为语音控制应用添加文字转语音（TTS）反馈功能。当操作执行完成（成功或失败）后，系统使用 sherpa-onnx 离线 TTS 和 vits-zh-hf-echo 中文模型将结果朗读出来。

## 需求

- **TTS 引擎**: sherpa-onnx 离线 TTS（vits-zh-hf-echo 模型）
- **触发时机**: 操作执行完成后（成功或失败）
- **成功时**: 简短的口语化结果（例如"已打开微信"、"已返回"）
- **失败时**: 详细的口语化错误信息（例如"未找到应用：微信"）
- **ASR 静音**: TTS 输出不能被 ASR 识别为新的语音指令
- **模型分发**: 运行时从 GitHub Releases 下载（约 120MB），与 Vosk 模型下载模式一致
- **降级策略**: 如果 TTS 模型未下载/未加载，则静默跳过（悬浮窗仍然显示结果）

## 架构

### 新增组件: `TtsSpeaker`

包路径: `com.controlmoblie.tts`

```
TtsSpeaker(context: Context)
  - init(): Boolean                    // 加载模型，返回是否成功
  - speak(text: String, onDone: () -> Unit)  // 生成并播放音频
  - release()                         // 释放原生资源
  - isModelReady: Boolean              // 检查模型文件是否存在
```
实现细节:
- 内部使用 sherpa-onnx 的 `OfflineTts` 实例
- 内部使用 `AudioTrack` 进行 22050Hz 单声道 PCM float 播放
- `speak()` 在 `Dispatchers.IO` 上运行生成，通过 `generateWithCallback` 流式传输音频块
- 回调实时将每个音频块写入 `AudioTrack`
- `speak()` 是**顺序的**: 如果正在播报，新的调用会排队或丢弃

### 更新组件: `VoiceControlService`

流程变更:

```
ASR 识别 → LLM 解析 → 执行操作 → 结果
                                      ↓
                               停止 ASR 监听
                               TTS 播报结果文本
                               等待 TTS onDone 回调
                               延迟 500ms
                               重新启动 ASR 监听
```

**关键点**: ASR 必须在 TTS 播报前停止，否则麦克风会将 TTS 音频识别为语音指令。

### 更新组件: `MainActivity`

添加 TTS 模型下载 UI（参照 Vosk 模型下载模式）:
- "下载语音合成模型 (~120MB)" 按钮
- 下载过程中的进度条
- 就绪时显示"语音合成 ✓"状态指示

### 模型管理: `TtsModelManager`

包路径: `com.controlmoblie.tts`

```
TtsModelManager
  - isModelReady(context: Context): Boolean
  - getModelDir(context: Context): String   // 返回 filesDir/vits-zh-hf-echo
  - downloadAndExtract(context: Context, onProgress: (Float) -> Unit): Boolean
```

下载地址: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-echo.tar.bz2`

解压到 `context.filesDir/vits-zh-hf-echo/` 的文件:
- `echo.onnx` (~116MB)
- `lexicon.txt`
- `tokens.txt`
- `phone.fst`
- `number.fst`
- `date.fst`
- `new_heteronym.fst`
- `dict/` 目录

**不包含**（过大，约 172MB）: `rule.far`。较小的 FST 文件（`phone.fst`、`number.fst`、`date.fst`）足以处理数字/日期标准化。

### sherpa-onnx 集成

**方案: 复制 Kotlin API 源文件 + 预编译 JNI 库**

1. 添加 sherpa-onnx AAR 或将 `kotlin-api/Tts.kt` 及相关文件复制到项目中
2. 将各 ABI 的预编译 `libsherpa-onnx-jni.so` 放入 `jniLibs/`
3. 使用 `OfflineTts` 配合指向 `filesDir` 中模型文件的 `OfflineTtsConfig`

配置:
```kotlin
OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        vits = OfflineTtsVitsModelConfig(
            model = "${modelDir}/echo.onnx",
            lexicon = "${modelDir}/lexicon.txt",
            tokens = "${modelDir}/tokens.txt",
        ),
        numThreads = 2,
        debug = false,
        provider = "cpu",
    ),
    ruleFsts = "${modelDir}/phone.fst,${modelDir}/date.fst,${modelDir}/number.fst",
)
```

### 结果文本映射

| 操作 | 成功文本 | 失败文本 |
|--------|-------------|-------------|
| Click | "已点击{target}" | "未找到{target}" |
| OpenApp | "已打开{name}" | "未找到应用{name}" |
| Navigate back | "已返回" | "返回失败" |
| Navigate home | "已回桌面" | "回桌面失败" |
| Navigate recents | "最近任务" | "切换失败" |
| Scroll | "已滑动" | "滑动失败" |
| Type | "已输入" | "未找到输入框" |
| Wait | （静默，无 TTS） | — |
| Sequence | 最后一步的结果 | 第一条失败信息 |
| Error (解析/LLM) | — | 原始错误信息 |

对于 `OpenApp`，`{name}` 使用语音指令中的原始中文名称（例如"微信"），而非解析后的包名。`ExecutionEngine` 必须将原始名称传递给结果回调。由于 `Action.OpenApp.package` 在 `AppResolver.resolve()` 之后存储的是解析后的包名，原始名称必须单独保留——最简单的方案是将解析前的名称存储为额外字段，或在结果文本中反向解析。

**实现说明**: `LlmEngine.simulateInference()` 在 `AppResolver` 解析之前已经输出 `"package": "微信"`。`ControlAccessibilityService.executeOpenApp()` 调用 `AppResolver.resolve(action.package)` 并使用解析后的名称进行 `getLaunchIntentForPackage()`。对于 TTS 文本，结果消息应使用原始的 `action.package`（例如"微信"），而非解析后的包名。

### 依赖添加

`libs.versions.toml`:
```toml
sherpa-onnx = "1.13.2"
```

`build.gradle.kts`:
```kotlin
implementation(files("libs/sherpa-onnx-1.13.2.aar"))
```

将 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 的预编译 `.so` 文件放入 `jniLibs/<abi>/libsherpa-onnx-jni.so`。

## 包结构（新增）

```
com.controlmoblie/
  tts/
    TtsSpeaker.kt          // TTS 引擎封装 + AudioTrack 播放
    TtsModelManager.kt     // 模型下载/解压管理
  ...已有包...
```

## 错误处理

- TTS 模型未下载 → 跳过 TTS，仅悬浮窗反馈
- TTS 模型加载失败 → 记录警告日志，跳过 TTS
- TTS 生成失败 → 记录错误日志，跳过 TTS
- AudioTrack 初始化失败 → 记录错误日志，跳过 TTS
- 所有错误均降级为仅悬浮窗反馈（当前行为）

## 并发

- `TtsSpeaker.speak()` 必须从 `Dispatchers.IO` 上的协程中调用
- 同一时间只能有一个 TTS 播报；如果 `speak()` 被调用时正在播报，则丢弃新的调用
- ASR 必须在 TTS 之前停止，在 TTS 完成后重新启动
- `VoiceControlService.processVoiceCommand()` 已在协程作用域中运行
