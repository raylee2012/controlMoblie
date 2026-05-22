# Llama.cpp JNI 集成实施计划

> **面向 agentic 工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐步实施此计划。各步骤使用 checkbox（`- [ ]`）语法进行追踪。

**目标：** 将 llama.cpp 源码集成到 Android 项目中，实现在设备端使用 Qwen2.5-0.5B Q4 GGUF 进行 LLM 推理，用真实推理替换当前的占位 JNI。

**架构：** 将 llama.cpp 作为 git 子模块添加到 `app/src/main/jni/llama.cpp/`，通过 CMake 编译为静态库，并链接到 JNI 桥接库。JNI 桥接层（`llama_jni.cpp`）调用 llama.cpp C API 函数进行模型加载、分词、推理和清理。Kotlin 侧使用现有的 `NativeLlmEngine` 和 `LlmEngine`，通过 `useNative` 标志在真实推理和 simulateInference 回退之间切换。

**技术栈：** Kotlin、C++（JNI）、CMake、llama.cpp（最新稳定版 b9279）、Android NDK

---

### 任务 1：将 llama.cpp 添加为 Git 子模块

**涉及文件：**
- 修改：`.gitmodules`（新建）

- [ ] **步骤 1：添加 llama.cpp 子模块**

```bash
cd D:\code\ai\controlMoblie
git submodule add https://github.com/ggml-org/llama.cpp.git app/src/main/jni/llama.cpp
```

此操作将 llama.cpp 克隆到 `app/src/main/jni/llama.cpp/` 并注册为子模块。

- [ ] **步骤 2：验证子模块存在**

```bash
git submodule status
```

预期结果：显示一行包含 llama.cpp 子模块及其 commit hash

- [ ] **步骤 3：提交**

```bash
git add .gitmodules app/src/main/jni/llama.cpp
git commit -m "chore: add llama.cpp as git submodule"
```



### 任务 2：重写 CMakeLists.txt 以编译 llama.cpp

**涉及文件：**
- 修改：`app/src/main/jni/CMakeLists.txt`
- 修改：`app/build.gradle.kts`

- [ ] **步骤 1：重写 CMakeLists.txt**

替换 `app/src/main/jni/CMakeLists.txt` 为：

```cmake
cmake_minimum_required(VERSION 3.18)
project("llama")

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENSYCOFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp_build)

add_library(llama_jni SHARED llama_jni.cpp)
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)
target_link_libraries(llama_jni llama ggml log)
```

- [ ] **步骤 2：更新 build.gradle.kts 添加 NDK 过滤器和 stl**

修改 `app/build.gradle.kts`——在 `defaultConfig` 内添加 `ndk` 和 `stl` 配置：

```kotlin
android {
    namespace = "com.controlmoblie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.controlmoblie"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }
    // ... 其余部分保持不变 ...
```

并在 `externalNativeBuild.cmake` 内添加 `stl` 配置：

```kotlin
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }
```

注意——`defaultConfig` 已经声明过了。需要将 `externalNativeBuild` 添加到已有的 `defaultConfig` 块内。完整的 android 块如下：

```kotlin
android {
    namespace = "com.controlmoblie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.controlmoblie"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

- [ ] **步骤 3：验证 CMake 配置成功**

运行：`.\gradlew :app:compileDebugKotlin`

这将触发 CMake 配置。首次构建需要几分钟来编译 llama.cpp。预期结果：BUILD SUCCESSFUL（警告可忽略）。

注意：如果因为 llama.cpp 子模块源文件过多或 CMake 选项不匹配而导致构建失败，我们将在下一步修复 CMake 选项。关键是要确保 CMake 配置运行无错误。

- [ ] **步骤 4：修复编译错误**

llama.cpp 是一个大型项目。常见问题：
- 缺少 `ggml` 子目录：llama.cpp 现在包含 ggml 作为子目录，CMakeLists.txt 使用 `add_subdirectory(ggml)`，由于我们对整个 llama.cpp 使用了 add_subdirectory，这应该可以正常工作
- 选项名称不匹配：检查 llama.cpp 的 CMakeLists.txt 获取正确的选项名称，并相应调整我们的 CMakeLists.txt
- 如果编译因缺少头文件而失败，添加正确的包含路径

- [ ] **步骤 5：提交**

```bash
git add -A
git commit -m "feat: configure CMake to compile llama.cpp as static library"
```



### 任务 3：使用真实 llama.cpp API 重写 llama_jni.cpp

**涉及文件：**
- 修改：`app/src/main/jni/llama_jni.cpp`

- [ ] **步骤 1：重写 llama_jni.cpp**

替换 `app/src/main/jni/llama_jni.cpp` 为：

```cpp
#include <jni.h>
#include <string>
#include <cstring>
#include <vector>
#include <android/log.h>

#include "llama.h"

#define TAG "LlamaJni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_loadModel(
    JNIEnv *env,
    jobject /*this*/,
    jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("loadModel: null path");
        return JNI_FALSE;
    }
    LOGD("loadModel: %s", path);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.use_mlock = false;

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("loadModel: failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_batch = 256;
    ctx_params.n_threads = 2;
    ctx_params.n_threads_batch = 2;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("loadModel: failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGD("loadModel: success");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_infer(
    JNIEnv *env,
    jobject /*this*/,
    jstring prompt) {
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        return env->NewStringUTF("{\"action\": \"error\", \"message\": \"null prompt\"}");
    }

    if (!g_model || !g_ctx) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("{\"action\": \"error\", \"message\": \"model not loaded\"}");
    }

    LOGD("infer: prompt len=%zu", strlen(prompt_str));

    const llama_vocab *vocab = llama_model_get_vocab(g_model);

    std::string prompt_std(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    std::vector<llama_token> tokens = llama_tokenize(vocab, prompt_std, true, true);
    tokens.push_back(llama_vocab_token_eos(vocab));

    llama_kv_cache_clear(g_ctx);

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("infer: decode failed");
        return env->NewStringUTF("{\"action\": \"error\", \"message\": \"decode failed\"}");
    }

    std::string result;
    int n_decode = 0;
    const int max_tokens = 128;

    while (n_decode < max_tokens) {
        llama_token new_token = llama_sampler_sample_none(g_ctx, -1);
        const char *token_str = llama_vocab_token_get_text(vocab, new_token);

        if (new_token == llama_vocab_token_eos(vocab) ||
            strstr(token_str, "<|im_end|>") != nullptr) {
            break;
        }

        if (token_str) {
            result += token_str;
        }

        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("infer: decode continuation failed");
            break;
        }
        n_decode++;
    }

    LOGD("infer: generated %d tokens, %zu bytes", n_decode, result.size());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_unloadModel(
    JNIEnv *env,
    jobject /*this*/) {
    LOGD("unloadModel");
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}
```

注意：实际的 llama.cpp API 名称可能因版本而异。如果编译失败，请检查 `llama.cpp/include/llama.h` 和 `ggml/include/ggml.h` 中的确切函数名称。需要验证的关键函数：
- `llama_model_load_from_file()` vs `llama_load_model_from_file()`
- `llama_init_from_model()` vs `llama_new_context_with_model()`
- `llama_tokenize()` 的函数签名
- `llama_sampler_*` vs 旧的 `llama_sample_*` API
- `llama_vocab_token_eos()` vs `llama_token_eos()`

- [ ] **步骤 2：必要时修复 API 名称**

如果因最新版 llama.cpp 的 API 名称变更导致编译失败，请检查 `app/src/main/jni/llama.cpp/include/llama.h` 头文件并相应更新函数名称。近期版本中最常见的变更：
- Token 类型从 `llama_token`（int32）变更为 `llama_pos`/新类型
- `llama_context_params` 的字段名称可能已变更
- 采样 API 可能已重构

- [ ] **步骤 3：验证编译**

运行：`.\gradlew :app:compileDebugKotlin`

预期结果：BUILD SUCCESSFUL（C++ 编译在 Kotlin 之前进行）

- [ ] **步骤 4：提交**

```bash
git add -A
git commit -m "feat: implement real llama.cpp JNI bridge for model loading and inference"
```



### 任务 4：更新 LlmEngine 以使用真实推理，并为 UI 添加模型下载

**涉及文件：**
- 修改：`app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`
- 修改：`app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **步骤 1：更新 LlmEngine.kt 的 downloadModel 使用 IO 线程进行进度回调**

在 `LlmEngine.kt` 中，更新 `downloadModel()` 在主线程上分发进度（与 VoskModelManager 修复相同）：

```kotlin
package com.controlmoblie.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class LlmEngine(private val context: Context) {

    private var isLoaded = false
    private var modelPath: String = ""
    private var useNative = false

    companion object {
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val MODEL_FILENAME = "qwen2.5-0.5b-q4.gguf"
        private const val MODEL_TEMP_FILENAME = "qwen2.5-0.5b-q4.gguf.tmp"
    }

    val isModelLoaded: Boolean get() = isLoaded
    val isDownloaded: Boolean get() = File(context.filesDir, MODEL_FILENAME).exists()

    suspend fun downloadModel(onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val dest = File(context.filesDir, MODEL_FILENAME)
            if (dest.exists()) {
                withContext(Dispatchers.Main) { onProgress(1f) }
                isLoaded = false
                modelPath = dest.absolutePath
                return@withContext
            }
            val tmp = File(context.filesDir, MODEL_TEMP_FILENAME)
            tmp.delete()
            val url = URL(MODEL_URL)
            val connection = url.openConnection().apply {
                connectTimeout = 30000
                readTimeout = 60000
            }
            connection.connect()
            val fileLength = connection.contentLengthLong
            connection.getInputStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (fileLength > 0) {
                            withContext(Dispatchers.Main) {
                                onProgress(totalRead.toFloat() / fileLength)
                            }
                        }
                    }
                }
            }
            tmp.renameTo(dest)
            isLoaded = false
            modelPath = dest.absolutePath
        }
    }

    suspend fun loadModel(): Boolean {
        return withContext(Dispatchers.IO) {
            val dest = File(context.filesDir, MODEL_FILENAME)
            if (!dest.exists()) return@withContext false
            try {
                useNative = NativeLlmEngine.loadModel(dest.absolutePath)
                isLoaded = useNative
                modelPath = dest.absolutePath
                useNative
            } catch (e: UnsatisfiedLinkError) {
                isLoaded = true
                modelPath = dest.absolutePath
                false
            }
        }
    }

    suspend fun infer(prompt: String): String {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) return@withContext "{\"action\": \"error\", \"message\": \"模型未加载\"}"
            if (useNative) {
                NativeLlmEngine.infer(prompt)
            } else {
                simulateInference(prompt)
            }
        }
    }

    private fun simulateInference(prompt: String): String {
        return when {
            prompt.contains("返回") || prompt.contains("后退") ->
                "{\"action\": \"navigate\", \"type\": \"back\"}"
            prompt.contains("回到桌面") || prompt.contains("主页") ->
                "{\"action\": \"navigate\", \"type\": \"home\"}"
            prompt.contains("点击") -> {
                val target = extractAfterKeyword(prompt, listOf("点击"))
                "{\"action\": \"click\", \"target\": \"$target\"}"
            }
            prompt.contains("打开") -> {
                val target = extractAfterKeyword(prompt, listOf("打开"))
                "{\"action\": \"click\", \"target\": \"$target\"}"
            }
            prompt.contains("上滑") || prompt.contains("向上滑") ->
                "{\"action\": \"scroll\", \"direction\": \"up\", \"distance\": \"half\"}"
            prompt.contains("下滑") || prompt.contains("向下滑") ->
                "{\"action\": \"scroll\", \"direction\": \"down\", \"distance\": \"half\"}"
            else ->
                "{\"action\": \"error\", \"message\": \"无法理解指令\"}"
        }
    }

    private fun extractAfterKeyword(text: String, keywords: List<String>): String {
        for (kw in keywords) {
            val idx = text.indexOf(kw)
            if (idx >= 0) {
                val after = text.substring(idx + kw.length).trim()
                if (after.isNotBlank()) return after
            }
        }
        return text
    }

    fun unload() {
        isLoaded = false
        if (useNative) {
            try { NativeLlmEngine.unloadModel() } catch (_: Exception) {}
            useNative = false
        }
    }
}
```

- [ ] **步骤 2：在 VoiceControlService 中添加 LLM 模型加载和下载**

在 `VoiceControlService.kt` 中，在服务启动后添加模型加载。在 `overlay.setOnStopListener` 之后添加：

```kotlin
// 在 onCreate() 中，overlay 设置之后：
serviceScope.launch {
    val llm = LlmEngine(this@VoiceControlService)
    if (llm.isDownloaded && !llm.isModelLoaded) {
        llm.loadModel()
    }
}
```

实际上，查看当前代码，`llmEngine` 已在 `onCreate()` 中创建但从未加载。我们需要添加加载逻辑。修改 `VoiceControlService.kt` 以在服务启动时加载模型：

找到 `onStartCommand` 方法并添加模型加载：

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    createNotificationChannel()
    val notification = buildNotification()
    startForeground(NOTIFICATION_ID, notification)

    if (!isRunning) {
        isRunning = true
        overlay.show()
        overlay.updateState(OverlayState.IDLE)
        initAndStart()
        loadLlmModel()
    }

    return START_STICKY
}

private fun loadLlmModel() {
    serviceScope.launch {
        if (llmEngine.isDownloaded) {
            overlay.updateState(OverlayState.PROCESSING, result = "加载推理模型...")
            val success = llmEngine.loadModel()
            if (!success && llmEngine.isModelLoaded) {
                LOGD("VoiceControlService", "Using simulateInference fallback")
            }
        }
    }
}
```

注意——更仔细地查看其实际 onCreate：

```kotlin
llmEngine = LlmEngine(this)
```

而 llmEngine 在 processVoiceCommand 中被使用。模型需要在推理之前加载。让我们添加一个 `loadLlmModel()` 方法并从 `initAndStart()` 中调用它。

- [ ] **步骤 3：验证编译**

运行：`.\gradlew :app:compileDebugKotlin`

预期结果：BUILD SUCCESSFUL

- [ ] **步骤 4：完整构建**

运行：`.\gradlew :app:assembleDebug`

预期结果：BUILD SUCCESSFUL（首次构建的完整原生编译可能需要 5-10 分钟）

- [ ] **步骤 5：提交**

```bash
git add -A
git commit -m "feat: wire up LLM model loading and fix download progress thread safety"
```



### 任务 5：更新 InstructionParser 以适配 Qwen 聊天模板

**涉及文件：**
- 修改：`app/src/main/java/com/controlmoblie/llm/InstructionParser.kt`

- [ ] **步骤 1：更新 buildPrompt 以使用 Qwen2.5 聊天模板**

发送给 LLM 的提示词必须遵循 Qwen2.5 的聊天模板格式（`<|im_start|>` / `<|im_end|>`），以便模型生成结构化的 JSON 输出。当前的 `buildPrompt()` 仅使用纯文本——更新它以包含系统指令和 Qwen 模板标记，因为 JNI 层也使用 Qwen 模板进行包装：

实际上，查看设计规范，JNI 的 `infer()` 函数接收完整提示词，分词器会处理它。但 Qwen 模板的包装应该在 Kotlin 层进行，以便 simulateInference 路径仍能正常工作。JNI 的 `infer` 函数接收一个提示词字符串——我们应该发送完整的聊天格式化提示词。

替换 `InstructionParser.buildPrompt()`：

```kotlin
fun buildPrompt(userText: String, screenContext: String): String {
    val systemMsg = if (screenContext.isNotBlank()) {
        "你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。\n\n当前屏幕信息:\n$screenContext"
    } else {
        "你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。"
    }
    return "<|im_start|>system\n$systemMsg<|im_end|>\n<|im_start|>user\n$userText<|im_end|>\n<|im_start|>assistant\n"
}
```

- [ ] **步骤 2：验证编译**

运行：`.\gradlew :app:compileDebugKotlin`

预期结果：BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```bash
git add -A
git commit -m "feat: update InstructionParser prompt to Qwen2.5 chat template format"
```



### 任务 6：端到端构建验证

**涉及文件：** 无（仅验证）

- [ ] **步骤 1：清理构建**

```bash
.\gradlew clean :app:assembleDebug
```

预期结果：BUILD SUCCESSFUL

- [ ] **步骤 2：验证 APK 包含原生库**

```bash
.\gradlew :app:assembleDebug
# 然后检查 APK 中是否有 libllama_jni.so 和 libllama.so
python3 -m zipfile -l app/build/outputs/apk/debug/app-debug.apk | findstr ".so"
```

或在 Windows PowerShell 中：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem; $apk = [System.IO.Compression.ZipFile]::OpenRead("D:\code\ai\controlMoblie\app\build\outputs\apk\debug\app-debug.apk"); $apk.Entries | Where-Object { $_.FullName -like "lib/*" } | ForEach-Object { $_.FullName }
```

预期结果：显示如 `lib/arm64-v8a/libllama_jni.so`、`lib/arm64-v8a/libllama.so` 等文件。

- [ ] **步骤 3：提交最终状态**

```bash
git add -A
git commit -m "chore: verify end-to-end build with llama.cpp native integration"
```



## 自检清单

- [ ] 规范覆盖：设计规范中的每个章节都有对应任务
  - 带真实 llama.cpp API 的 JNI 桥接层 → 任务 3
  - Git 子模块 → 任务 1
  - CMake 构建配置 → 任务 2
  - 模型下载（已存在，修复线程安全）→ 任务 4
  - InstructionParser Qwen 模板 → 任务 5
  - 端到端验证 → 任务 6
- [ ] 无占位代码、TBD 或 TODO
- [ ] 各任务间类型一致性（NativeLlmEngine 方法名与 JNI 名称匹配）
- [ ] 每个步骤都有完整代码
- [ ] 每个步骤都有验证命令
