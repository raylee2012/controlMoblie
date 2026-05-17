# ControlMoblie — Voice-Controlled Android Operations

## Project Overview
Single Android app (Kotlin + Jetpack Compose) that takes voice commands via Google SpeechRecognizer, parses with an on-device LLM (Qwen2.5-0.5B GGUF via llama.cpp JNI scaffold), and executes via Accessibility Service.

## Architecture
`VoiceControlService` (foreground) → `SpeechRecognizerManager` (ASR) → `LlmEngine` + `InstructionParser` (JSON) → `ExecutionEngine` → `ControlAccessibilityService` (actions). `ControlOverlay` shows status as a floating window.

**Key wiring:** `ControlAccessibilityService.instance` companion object bridges Accessibility Service with `VoiceControlService` — never pass it manually.

## Package Layout
- `model/` — `Action` sealed class, `InstructionResult`, `ScreenState`
- `service/` — `VoiceControlService` (foreground), `ControlAccessibilityService` (actions)
- `asr/` — `SpeechRecognizerManager` (Google SpeechRecognizer, Flow-based events)
- `llm/` — `LlmEngine` (download/load/infer), `InstructionParser` (JSON→Action), `NativeLlmEngine` (JNI scaffold)
- `execution/` — `ExecutionEngine` (facade over AccessibilityService)
- `overlay/` — `ControlOverlay` (Compose floating window), `PermissionHelper`
- `util/` — `ScreenReader` (collects visible node texts)

## Dev Commands
- Build: `.\gradlew :app:assembleDebug`
- Compile check: `.\gradlew :app:compileDebugKotlin`
- Run on device from Android Studio: normal `app` run config

## Gotchas
- **AccessibilityService bridge:** `ControlAccessibilityService.instance` is set on `onServiceConnected`, cleared on `onDestroy`. `VoiceControlService.executionEngine` reads this lazily.
- **Overlay state:** `ControlOverlay` fields use `mutableStateOf` — recomposition is triggered automatically. Do NOT replace with plain `var`.
- **LLM inference:** `LlmEngine.infer()` has a `simulateInference` keyword fallback when native JNI is unavailable. The download uses a `.tmp` file renamed on success to prevent corruption.
- **ASR events channel:** Uses `Channel(CONFLATED)` to discard stale events on restart.
- **Android 14+:** Requires `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />` in manifest.
- **NodeInfo recycling:** Every `AccessibilityNodeInfo` from `getChild()`, `findAccessibilityNodeInfosByText()`, etc. must be recycled — this is a common source of native memory leaks.

## Design Reference
Full spec at `docs/superpowers/specs/2026-05-17-voice-control-mobile-design.md`
Implementation plan at `docs/superpowers/plans/2026-05-17-voice-control-mobile-plan.md`
