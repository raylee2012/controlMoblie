# controlMoblie

[中文](README.md) | [English](README.en.md)

ControlMoblie 是一个 Android 语音控制实验项目：用户说出自然语言指令，应用将语音转成文本，再解析为结构化操作，并通过 Android Accessibility Service 执行点击、滑动、返回、打开应用、输入文本等动作。

> 仓库名沿用项目初始拼写：`controlMoblie`。

## 功能特性

- 语音控制链路：ASR -> 指令解析 -> 操作执行。
- 支持 Qwen2.5-0.5B GGUF + llama.cpp JNI 本地推理。
- 当 LLM 模型或 native 运行时不可用时，自动降级到规则/模板解析。
- 支持常用无障碍操作：点击、滑动、返回、回桌面、最近任务、打开应用、输入文本、等待和顺序动作。
- 使用 Jetpack Compose 悬浮窗显示语音控制状态。
- 内置微信指令模板：看朋友圈、发朋友圈、发消息、搜索公众号、打开小程序等。
- 支持 MediaProjection 截屏 + ML Kit 中文 OCR 兜底识别。
- 支持 sherpa-onnx 离线 TTS 语音反馈。

## 技术栈

- Kotlin + Jetpack Compose
- Android Foreground Service
- Android Accessibility Service
- llama.cpp JNI
- sherpa-onnx / ONNX Runtime
- Google ML Kit Chinese Text Recognition
- Gradle + Android Gradle Plugin

## 架构

```text
VoiceControlService
  -> SpeechRecognizerManager        # ASR 事件
  -> LlmEngine + InstructionParser  # JSON 指令解析
  -> ExecutionEngine                # 执行门面
  -> ControlAccessibilityService    # 无障碍动作
  -> ControlOverlay                 # 悬浮窗状态
  -> TtsSpeaker                     # 语音反馈
```

`ControlAccessibilityService.instance` 是前台语音服务和无障碍服务之间的桥接点。不要手动传递 AccessibilityService 实例。

## 目录结构

```text
app/src/main/java/com/controlmoblie/
|-- asr/          # 语音识别和 ASR 模型管理
|-- execution/    # ExecutionEngine 执行门面
|-- llm/          # LlmEngine、InstructionParser、命令模板、JNI 封装
|-- model/        # Action sealed class 和共享模型
|-- overlay/      # Compose 悬浮窗和权限工具
|-- service/      # 前台语音服务和无障碍服务
|-- tts/          # sherpa-onnx TTS 模型和播报器
`-- util/         # 应用解析、OCR、截屏、屏幕读取工具
```

设计与实现文档位于 `docs/superpowers/`。

## 构建

环境要求：

- Android Studio / Android SDK
- JDK 17
- Android NDK + CMake，用于构建 llama.cpp JNI

常用命令：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 运行前准备

首次运行后，请在主界面完成以下权限或模型准备：

1. 授予录音权限。
2. 开启悬浮窗权限。
3. 开启 ControlMoblie 无障碍服务。
4. 如需 OCR 兜底，授予 MediaProjection / 屏幕录制权限。
5. 根据需要下载 ASR、LLM 和 TTS 模型。

## 模型文件

应用会将下载的模型保存到 app 私有 files 目录：

- LLM：`qwen2.5-0.5b-q4.gguf`
- ASR / TTS：由对应的模型管理器维护模型目录

如果 LLM 模型缺失，或 llama.cpp JNI 加载失败，`LlmEngine` 会使用 `simulateInference()` 作为关键词/模板兜底解析。

## 指令示例

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

## 注意事项

- Android 14+ 的麦克风和 MediaProjection 前台服务流程需要对应权限声明。
- Android API 返回的 AccessibilityNodeInfo 需要及时 recycle，避免 native 内存泄漏。
- OCR 兜底需要先授予屏幕录制权限。
- `app/libs/sherpa-onnx-1.13.2.aar` 超过 GitHub 推荐的 50 MB 文件大小，长期维护建议迁移到 Git LFS。
- 本项目面向个人设备自动化实验，请谨慎授予无障碍权限。
