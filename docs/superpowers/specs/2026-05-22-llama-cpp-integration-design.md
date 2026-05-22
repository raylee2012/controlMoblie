# Llama.cpp JNI 集成设计

## 目标

将 llama.cpp 源码集成到 Android 项目中，实现在设备端使用 Qwen2.5-0.5B Q4 GGUF 进行 LLM 推理，用真实推理替换当前的占位 JNI 脚手架。

## 架构

```
app/src/main/jni/
├── CMakeLists.txt              # 主 CMake：编译 llama.cpp + JNI 桥接层
├── llama_jni.cpp               # JNI 桥接层（从占位代码重写）
└── llama.cpp/                  # Git 子模块（最新稳定版）
    ├── ggml.c / ggml.h
    ├── llama.cpp / llama.h
    ├── ggml-alloc.c/h
    ├── ggml-backend.c/h
    └── ggml-cpu/
```

## 数据流

1. `LlmEngine.loadModel()` → `NativeLlmEngine.loadModel(path)` → JNI → `llama_model_load_from_file()`
2. `LlmEngine.infer(prompt)` → `NativeLlmEngine.infer(prompt)` → JNI → 分词 → 逐 token `llama_decode()` 生成 → 组装完整 JSON 字符串
3. `LlmEngine.unload()` → `NativeLlmEngine.unloadModel()` → JNI → `llama_model_free()` + `llama_free()`

## JNI 接口（与脚手架保持一致）

```kotlin
object NativeLlmEngine {
    external fun loadModel(modelPath: String): Boolean
    external fun infer(prompt: String): String
    external fun unloadModel()
}
```

C++ 侧维护全局 `llama_model*` 和 `llama_context*`：

- **loadModel**：`llama_model_load_from_file()` → `llama_init_from_model()`
- **infer**：构建 Qwen 聊天模板提示词 → `llama_tokenize` → 逐 token `llama_decode` 并采样 → 遇到 `<|im_end|>` 或达到 max_tokens 时停止 → 返回完整 JSON 字符串
- **unloadModel**：释放 model 和 context 指针

## 模型加载策略

- 模型文件（约 350MB）通过 `LlmEngine.downloadModel()` 经 WiFi 下载至 `context.filesDir`
- 下载使用 `.tmp` 临时文件，成功后重命名以防止文件损坏
- `loadModel()` 在后台线程运行，首次加载约 5-10 秒
- 加载失败时：`useNative = false`，回退到 `simulateInference()`（保留当前行为）

## 构建配置

- CMake 仅编译 CPU 后端（`GGML_CPU=ON`），禁用 CUDA/OpenCL/Vulkan/Metal
- 目标 ABI：`arm64-v8a`（主要）、`armeabi-v7a`、`x86`、`x86_64`
- 使用 NDK C++ 共享库（`c++_shared`）
- `llama.cpp` 作为 git 子模块添加，通过 `add_subdirectory` 编译

## 错误处理

- `loadModel` 失败：返回 `false`，LlmEngine 设置 `useNative=false`，回退到模拟推断
- `infer` 超时（Kotlin 侧 `withTimeout(5000)`）：取消协程，显示"推理超时"
- 模型加载期间 OOM：作为异常捕获，`loadModel` 返回 `false`

## 提示词格式（Qwen2.5 聊天模板）

```
<|im_start|>system
你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。<|im_end|>
<|im_start|>user
{screenContext}
{userText}<|im_end|>
<|im_start|>assistant
```

## 范围边界

- 范围内：llama.cpp 源码编译、带真实推理的 JNI 桥接层、模型下载 UI 集成
- 范围外：GPU 加速、运行时量化、流式 token 输出到 UI、多轮对话上下文
