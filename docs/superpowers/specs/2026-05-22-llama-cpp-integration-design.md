# Llama.cpp JNI Integration Design

## Goal

Integrate llama.cpp source code into the Android project to enable on-device LLM inference with Qwen2.5-0.5B Q4 GGUF, replacing the current placeholder JNI scaffold with real inference.

## Architecture

```
app/src/main/jni/
├── CMakeLists.txt              # Main CMake: compiles llama.cpp + JNI bridge
├── llama_jni.cpp               # JNI bridge (rewrite from placeholder)
└── llama.cpp/                  # Git submodule (latest stable release)
    ├── ggml.c / ggml.h
    ├── llama.cpp / llama.h
    ├── ggml-alloc.c/h
    ├── ggml-backend.c/h
    └── ggml-cpu/
```

## Data Flow

1. `LlmEngine.loadModel()` → `NativeLlmEngine.loadModel(path)` → JNI → `llama_model_load_from_file()`
2. `LlmEngine.infer(prompt)` → `NativeLlmEngine.infer(prompt)` → JNI → tokenize → `llama_decode()` per-token generation → assemble full JSON string
3. `LlmEngine.unload()` → `NativeLlmEngine.unloadModel()` → JNI → `llama_model_free()` + `llama_free()`

## JNI Interface (unchanged from scaffold)

```kotlin
object NativeLlmEngine {
    external fun loadModel(modelPath: String): Boolean
    external fun infer(prompt: String): String
    external fun unloadModel()
}
```

C++ side maintains global `llama_model*` and `llama_context*`:

- **loadModel**: `llama_model_load_from_file()` → `llama_init_from_model()`
- **infer**: Build Qwen chat template prompt → `llama_tokenize` → token-by-token `llama_decode` with sampling → stop on `<|im_end|>` or max_tokens → return full JSON string
- **unloadModel**: Free model and context pointers

## Model Loading Strategy

- Model file (~350MB) downloaded via `LlmEngine.downloadModel()` over WiFi to `context.filesDir`
- Download uses `.tmp` intermediate file, renamed on success to prevent corruption
- `loadModel()` runs on background thread, first load ~5-10 seconds
- On load failure: `useNative = false`, fallback to `simulateInference()` (current behavior preserved)

## Build Configuration

- CMake compiles CPU backend only (`GGML_CPU=ON`), disables CUDA/OpenCL/Vulkan/Metal
- Target ABIs: `arm64-v8a` (primary), `armeabi-v7a`, `x86`, `x86_64`
- Uses NDK C++ shared library (`c++_shared`)
- `llama.cpp` added as git submodule, compiled via `add_subdirectory`

## Error Handling

- `loadModel` failure: returns `false`, LlmEngine sets `useNative=false`, falls back to simulation
- `infer` timeout (Kotlin side `withTimeout(5000)`): cancels coroutine, shows "推理超时"
- OOM during model load: caught as exception, `loadModel` returns `false`

## Prompt Format (Qwen2.5 Chat Template)

```
<|im_start|>system
你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。<|im_end|>
<|im_start|>user
{screenContext}
{userText}<|im_end|>
<|im_start|>assistant
```

## Scope Boundaries

- In-scope: llama.cpp source compilation, JNI bridge with real inference, model download UI integration
- Out-of-scope: GPU acceleration, quantization at runtime, streaming token output to UI, multi-turn conversation context