# SenseVoiceSmall ASR — Design Spec

Date: 2026-05-23

## Summary

Replace Vosk ASR (`vosk-model-small-cn-0.22`, 42MB) with sherpa-onnx + SenseVoiceSmall model (~230MB) for significantly better recognition of accented Mandarin speech. No architecture changes beyond the ASR engine layer — the existing event flow, overlay integration, and TTS pipeline remain untouched.

## Motivation

- Current Vosk small Chinese model has poor accuracy on accented/regional Mandarin
- SenseVoiceSmall was specifically trained by Alibaba DAMO Academy on diverse Chinese speech including regional accents
- sherpa-onnx is already integrated in the project (used for TTS) — zero additional native dependency

## Model

| Property | Value |
|----------|-------|
| Name | SenseVoiceSmall |
| Engine | sherpa-onnx OnlineRecognizer |
| Size | ~230MB (ONNX format) |
| Languages | zh, en, yue, ko, ja |
| Streaming | Yes (chunk-based, ~400ms latency) |
| Features | Punctuation, inverse text normalization (ITN) |
| Download | GitHub releases / HuggingFace mirror |

Model files:
- `model.onnx` — encoder + decoder + joiner (~230MB)
- `tokens.txt` — tokenizer vocabulary

## Architecture

```
                         ┌─────────────────┐
                         │ VoiceControlService │  ← unchanged
                         └────────┬────────┘
                                  │ AsrEvent (sealed class, unchanged)
                         ┌────────▼────────┐
                         │ SpeechRecognizerManager │  ← REWRITTEN internals
                         └────────┬────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │                            │
            ┌───────▼──────┐            ┌───────▼──────┐
            │ SenseVoiceModelManager │   │ OnlineRecognizer │
            │  - download              │   │ (sherpa-onnx)    │
            │  - extract               │   │  - createStream  │
            │  - model dir             │   │  - acceptWaveform│
            └──────────────────────────┘   │  - callbacks     │
                                           └──────────────────┘
```

## Files

| File | Action | Responsibility |
|------|--------|---------------|
| `asr/SenseVoiceModelManager.kt` | Create | Download & extract SenseVoiceSmall model |
| `asr/SpeechRecognizerManager.kt` | Rewrite | Vosk → sherpa-onnx OnlineRecognizer |
| `service/VoiceControlService.kt` | Minor edit | Point model init to SenseVoiceModelManager |
| `MainActivity.kt` | Minor edit | UI: SenseVoice download button + progress |
| `asr/VoskModelManager.kt` | Keep (deprecated) | Retain for reference, not used by default |

## SpeechRecognizerManager (rewritten core)

```kotlin
class SpeechRecognizerManager(private val modelPath: String) {
    // Same event types: AsrEvent (unchanged)
    // Uses sherpa-onnx OnlineRecognizer

    fun init(): Boolean          // Load model, create recognizer
    fun startListening()        // Create stream, start accepting audio
    fun stopListening()         // Destroy stream
    fun release()               // Free recognizer

    // Internal: AudioRecord captures PCM 16kHz mono,
    // feeds chunks via recognizer.acceptWaveform()
    // callbacks map to AsrEvent.FinalResult / PartialResult
}
```

### Callback mapping

| sherpa-onnx callback | AsrEvent |
|---------------------|----------|
| `onResult(text, isFinal=true)` | `AsrEvent.FinalResult(text)` |
| `onResult(text, isFinal=false)` | `AsrEvent.PartialResult(text)` |

## SenseVoiceModelManager

Pattern mirrors `VoskModelManager`:
- `isModelReady(context): Boolean` — checks `model.onnx` exists
- `getModelPath(context): String` — returns model directory
- `downloadAndExtract(context, onProgress): Boolean` — download tar.bz2, extract to `filesDir/sense-voice-small-onnx/`

Model download URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-small-zh-en-ja-ko-yue-2025-03-27.tar.bz2`

## VoiceControlService changes

Minimal — replace Vosk references with SenseVoice:

```kotlin
// initAndStart()
if (!SenseVoiceModelManager.isModelReady(this)) { ... error ... }
val modelPath = SenseVoiceModelManager.getModelPath(this)
val manager = SpeechRecognizerManager(modelPath)
// ...rest unchanged
```

## MainActivity changes

Add SenseVoice download UI section (mirrors existing Vosk UI pattern):
- "下载语音识别模型 (~230MB)" button
- Progress bar during download
- "语音识别 ✓" status indicator

Existing Vosk UI section stays but is secondary (user may keep both or delete old model manually).

## Error Handling

- Model not downloaded → skip ASR init, show "语音模型未下载"
- Model load fails → show error in overlay, fallback message
- Recognition error → AsrEvent.Error, handled by VoiceControlService (existing flow)
- All errors are non-fatal; overlay still shows status, TTS still works

## Concurrency

- Recognizer runs in its own thread (sherpa-onnx handles internally)
- AudioRecord feeds PCM chunks on a background thread
- Callbacks post results to AsrEvent channel (CONFLATED, same as Vosk)
- No change to VoiceControlService coroutine model

## Migration Path

1. Vosk .so libs (`libvosk.so`) stay in jniLibs — harmless, no conflict
2. Vosk model files stay on device — user can delete manually to free space
3. `VoskModelManager.kt` stays in codebase — not imported by active code
4. If user wants to switch back, trivial to toggle which model manager `initAndStart()` uses

## Non-Goals

- Multi-model hot-swap (keep it simple: one ASR model at a time)
- Vosk model removal automation (user manages disk space manually)
- GPU/CUDA acceleration (CPU-only, consistent with TTS approach)
