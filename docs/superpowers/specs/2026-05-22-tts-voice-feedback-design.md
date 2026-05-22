# TTS Voice Feedback Design

Date: 2026-05-22

## Summary

Add text-to-speech voice feedback to the voice control app. When an action completes (success or failure), the system speaks the result aloud using sherpa-onnx offline TTS with the vits-zh-hf-echo Chinese model.

## Requirements

- **TTS engine**: sherpa-onnx offline TTS (vits-zh-hf-echo model)
- **Trigger**: After action execution completes (success or failure)
- **Success**: Brief spoken result (e.g. "已打开微信", "已返回")
- **Failure**: Detailed spoken error message (e.g. "未找到应用：微信")
- **ASR muting**: TTS output must NOT be picked up by ASR as a new command
- **Model delivery**: Download at runtime from GitHub releases (~120MB), same pattern as Vosk model
- **Fallback**: If TTS model not downloaded/loaded, silently skip (overlay still shows results)

## Architecture

### New Component: `TtsSpeaker`

Package: `com.controlmoblie.tts`

```
TtsSpeaker(context: Context)
  - init(): Boolean                    // Load model, return success
  - speak(text: String, onDone: () -> Unit)  // Generate + play audio
  - release()                         // Free native resources
  - isModelReady: Boolean              // Check if model files exist
```
实现细节:
- Internal `OfflineTts` instance from sherpa-onnx
- Internal `AudioTrack` for PCM float playback at 22050Hz mono
- `speak()` runs generation on `Dispatchers.IO`, streams audio chunks via `generateWithCallback`
- Callback writes each chunk to `AudioTrack` in real-time
- `speak()` is **sequential**: if already speaking, new call queues or drops

### Updated Component: `VoiceControlService`

Changes to flow:

```
ASR recognize → LLM parse → execute action → result
                                                    ↓
                                              stop ASR listening
                                              TTS speak result text
                                              wait for TTS onDone callback
                                              delay 500ms
                                              restart ASR listening
```

**Critical**: ASR must be stopped before TTS speaks, otherwise microphone picks up TTS audio as a command.

### Updated Component: `MainActivity`

Add UI for TTS model download (mirrors Vosk model download pattern):
- "下载语音合成模型 (~120MB)" button
- Progress bar during download
- "语音合成 ✓" status indicator when ready

### Model Management: `TtsModelManager`

Package: `com.controlmoblie.tts`

```
TtsModelManager
  - isModelReady(context: Context): Boolean
  - getModelDir(context: Context): String   // returns filesDir/vits-zh-hf-echo
  - downloadAndExtract(context: Context, onProgress: (Float) -> Unit): Boolean
```

Download source: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-echo.tar.bz2`

Files extracted to `context.filesDir/vits-zh-hf-echo/`:
- `echo.onnx` (~116MB)
- `lexicon.txt`
- `tokens.txt`
- `phone.fst`
- `number.fst`
- `date.fst`
- `new_heteronym.fst`
- `dict/` directory

**NOT included** (too large, ~172MB): `rule.far`. The small FST files (`phone.fst`, `number.fst`, `date.fst`) are sufficient for number/date normalization.

### sherpa-onnx Integration

**Approach: Copy Kotlin API source files + pre-built JNI libs**

1. Add sherpa-onnx AAR or copy `kotlin-api/Tts.kt` and related files into project
2. Add pre-built `libsherpa-onnx-jni.so` for each ABI to `jniLibs/`
3. Use `OfflineTts` with `OfflineTtsConfig` pointing to model files in `filesDir`

Config:
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

### Result Text Mapping

| Action | Success Text | Failure Text |
|--------|-------------|-------------|
| Click | "已点击{target}" | "未找到{target}" |
| OpenApp | "已打开{name}" | "未找到应用{name}" |
| Navigate back | "已返回" | "返回失败" |
| Navigate home | "已回桌面" | "回桌面失败" |
| Navigate recents | "最近任务" | "切换失败" |
| Scroll | "已滑动" | "滑动失败" |
| Type | "已输入" | "未找到输入框" |
| Wait | (silent, no TTS) | — |
| Sequence | Last step's result | First failure message |
| Error (parse/LLM) | — | Original error message |

For `OpenApp`, `{name}` is the original Chinese name from the voice command (e.g. "微信"), NOT the resolved package name. The `ExecutionEngine` must pass the original name through to the result callback. Since `Action.OpenApp.package` stores the resolved package name after `AppResolver.resolve()`, the original name must be preserved separately — the simplest approach is to store the pre-resolution name as an additional field or to resolve back in the result text.

**Implementation note**: `LlmEngine.simulateInference()` already outputs `"package": "微信"` before `AppResolver` resolves it. `ControlAccessibilityService.executeOpenApp()` calls `AppResolver.resolve(action.package)` and uses the resolved name for `getLaunchIntentForPackage()`. For TTS text, the result message should use the original `action.package` (e.g. "微信") not the resolved package.

### Dependency Addition

`libs.versions.toml`:
```toml
sherpa-onnx = "1.13.2"
```

`build.gradle.kts`:
```kotlin
implementation(files("libs/sherpa-onnx-1.13.2.aar"))
```

Pre-built `.so` files for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` placed in `jniLibs/<abi>/libsherpa-onnx-jni.so`.

## Package Layout (additions)

```
com.controlmoblie/
  tts/
    TtsSpeaker.kt          // TTS engine wrapper + AudioTrack playback
    TtsModelManager.kt     // Model download/extract management
  ...existing packages...
```

## Error Handling

- TTS model not downloaded → skip TTS, overlay-only feedback
- TTS model load fails → log warning, skip TTS
- TTS generation fails → log error, skip TTS
- AudioTrack init fails → log error, skip TTS
- All errors fall back to overlay-only feedback (current behavior)

## Concurrency

- `TtsSpeaker.speak()` must be called from a coroutine on `Dispatchers.IO`
- Only one TTS utterance at a time; if `speak()` is called while speaking, drop the new one
- ASR must be stopped before TTS, restarted after TTS completes
- `VoiceControlService.processVoiceCommand()` already runs in a coroutine scope