# controlMoblie

ControlMoblie is an Android voice-control experiment for operating a phone with spoken commands. It records speech, recognizes command text, converts the text into structured actions, and executes those actions through Android Accessibility Service.

> The repository keeps the original project spelling: `controlMoblie`.

## Features

- Voice-command pipeline: ASR -> instruction parsing -> action execution.
- Local LLM support with Qwen2.5-0.5B GGUF through llama.cpp JNI.
- Rule/template fallback when the LLM model or native runtime is unavailable.
- Android Accessibility actions: click, scroll, back, home, recents, open app, type text, wait, and action sequences.
- Floating Compose overlay for voice-control status.
- WeChat command templates for common flows such as Moments, sending messages, public-account search, and mini-program entry.
- OCR fallback with MediaProjection screenshot + ML Kit Chinese text recognition.
- Offline TTS feedback with sherpa-onnx.

## Tech Stack

- Kotlin + Jetpack Compose
- Android Foreground Service
- Android Accessibility Service
- llama.cpp JNI
- sherpa-onnx / ONNX Runtime
- Google ML Kit Chinese Text Recognition
- Gradle + Android Gradle Plugin

## Architecture

```text
VoiceControlService
  -> SpeechRecognizerManager        # ASR events
  -> LlmEngine + InstructionParser  # JSON action parsing
  -> ExecutionEngine                # execution facade
  -> ControlAccessibilityService    # accessibility actions
  -> ControlOverlay                 # floating status UI
  -> TtsSpeaker                     # voice feedback
```

`ControlAccessibilityService.instance` is the bridge between the foreground voice service and the accessibility service. Do not pass the accessibility service manually.

## Project Layout

```text
app/src/main/java/com/controlmoblie/
|-- asr/          # speech recognition and ASR model management
|-- execution/    # ExecutionEngine facade
|-- llm/          # LlmEngine, InstructionParser, command templates, JNI wrapper
|-- model/        # Action sealed class and shared models
|-- overlay/      # Compose floating overlay and permission helpers
|-- service/      # foreground voice service and accessibility service
|-- tts/          # sherpa-onnx TTS model and speaker
`-- util/         # app resolver, OCR, screen capture, screen reader
```

Design and implementation notes are in `docs/superpowers/`.

## Build

Requirements:

- Android Studio / Android SDK
- JDK 17
- Android NDK + CMake for llama.cpp JNI

Commands:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Runtime Setup

On first launch, enable or grant:

1. Microphone permission.
2. Overlay permission.
3. Accessibility Service for ControlMoblie.
4. MediaProjection/screen-capture permission if OCR fallback is needed.
5. ASR, LLM, and TTS model downloads as needed.

## Model Files

The app stores downloaded models in its private files directory:

- LLM: `qwen2.5-0.5b-q4.gguf`
- ASR/TTS: model directories managed by their corresponding model managers

If the LLM model is missing or llama.cpp JNI fails to load, `LlmEngine` uses `simulateInference()` as a keyword/template fallback.

## Example Commands

```text
打开微信
点击发送
输入你好
返回
回桌面
最近任务
上滑
下滑
看朋友圈
发消息给张三说你好
发朋友圈说今天天气不错
打开小程序腾讯文档
搜索公众号人民日报
```

## Notes

- Android 14+ foreground service permissions are required for microphone and MediaProjection flows.
- Accessibility node objects returned from Android APIs must be recycled to avoid native memory leaks.
- OCR fallback only works after screen-capture permission is granted.
- `app/libs/sherpa-onnx-1.13.2.aar` is larger than GitHub's recommended 50 MB file size; Git LFS is recommended for long-term maintenance.
- This project is intended for personal automation experiments. Use Accessibility permissions carefully.
