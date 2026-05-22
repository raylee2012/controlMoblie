# Llama.cpp JNI Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate llama.cpp source code into the Android project to enable on-device LLM inference with Qwen2.5-0.5B Q4 GGUF, replacing the current placeholder JNI with real inference.

**Architecture:** Add llama.cpp as a git submodule under `app/src/main/jni/llama.cpp/`, compile it as a static library via CMake, and link it to the JNI bridge library. The JNI bridge (`llama_jni.cpp`) calls llama.cpp C API functions for model loading, tokenization, inference, and cleanup. Kotlin side uses existing `NativeLlmEngine` and `LlmEngine` with `useNative` flag to switch between real inference and simulateInference fallback.

**Tech Stack:** Kotlin, C++ (JNI), CMake, llama.cpp (latest stable b9279), Android NDK

---

### Task 1: Add llama.cpp as Git Submodule

**Files:**
- Modify: `.gitmodules` (new)

- [ ] **Step 1: Add llama.cpp submodule**

```bash
cd D:\code\ai\controlMoblie
git submodule add https://github.com/ggml-org/llama.cpp.git app/src/main/jni/llama.cpp
```

This clones llama.cpp into `app/src/main/jni/llama.cpp/` and registers it as a submodule.

- [ ] **Step 2: Verify submodule is present**

```bash
git submodule status
```

Expected: a line showing the llama.cpp submodule with commit hash

- [ ] **Step 3: Commit**

```bash
git add .gitmodules app/src/main/jni/llama.cpp
git commit -m "chore: add llama.cpp as git submodule"
```



### Task 2: Rewrite CMakeLists.txt to Compile llama.cpp

**Files:**
- Modify: `app/src/main/jni/CMakeLists.txt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Rewrite CMakeLists.txt**

Replace `app/src/main/jni/CMakeLists.txt` with:

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

- [ ] **Step 2: Update build.gradle.kts to add NDK filters and stl**

Modify `app/build.gradle.kts` — add `ndk` and `stl` config inside `defaultConfig`:

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
    // ... rest unchanged ...
```

And add `stl` config inside `externalNativeBuild.cmake`:

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

Wait — `defaultConfig` is already declared. Need to add `externalNativeBuild` inside the existing `defaultConfig` block. The full android block becomes:

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

- [ ] **Step 3: Verify CMake configure succeeds**

Run: `.\gradlew :app:compileDebugKotlin`

This will trigger CMake configure. The first build will take several minutes to compile llama.cpp. Expected: BUILD SUCCESSFUL (warnings are fine).

Note: If the build fails because llama.cpp submodule has too many source files or CMake option mismatches, we will fix CMake options in the next step. The key thing is CMake configure runs without errors.

- [ ] **Step 4: Fix any compilation errors**

llama.cpp is a large project. Common issues:
- Missing `ggml` subdirectory: llama.cpp now includes ggml as a subdirectory, the CMakeLists.txt uses `add_subdirectory(ggml)` which should work since we add_subdirectory the whole llama.cpp
- Option name mismatches: check the llama.cpp CMakeLists.txt for the correct option names and adjust our CMakeLists.txt accordingly
- If compilation fails with missing headers, add the correct include paths

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: configure CMake to compile llama.cpp as static library"
```



### Task 3: Rewrite llama_jni.cpp with Real llama.cpp API

**Files:**
- Modify: `app/src/main/jni/llama_jni.cpp`

- [ ] **Step 1: Rewrite llama_jni.cpp**

Replace `app/src/main/jni/llama_jni.cpp` with:

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

Note: The actual llama.cpp API names may differ between versions. If compilation fails, check the exact API in `llama.cpp/include/llama.h` and `ggml/include/ggml.h` for the correct function names. Key functions to verify:
- `llama_model_load_from_file()` vs `llama_load_model_from_file()`
- `llama_init_from_model()` vs `llama_new_context_with_model()`
- `llama_tokenize()` signature
- `llama_sampler_*` vs the old `llama_sample_*` API
- `llama_vocab_token_eos()` vs `llama_token_eos()`

- [ ] **Step 2: Fix API names if needed**

If compilation fails due to API name changes in the latest llama.cpp, check the header file at `app/src/main/jni/llama.cpp/include/llama.h` and update function names accordingly. The most common changes in recent versions:
- Token type changed from `llama_token` (int32) to `llama_pos` / new types
- `llama_context_params` field names may have changed
- Sampling API may have been restructured

- [ ] **Step 3: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL (C++ compilation happens before Kotlin)

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: implement real llama.cpp JNI bridge for model loading and inference"
```



### Task 4: Update LlmEngine to Use Real Inference and Add Model Download to UI

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`
- Modify: `app/src/main/java/com/controlmoblie/MainActivity.kt`

- [ ] **Step 1: Update LlmEngine.kt downloadModel to use IO thread for progress callback**

In `LlmEngine.kt`, update `downloadModel()` to dispatch progress on Main thread (same fix as VoskModelManager):

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

- [ ] **Step 2: Add LLM model load and download to VoiceControlService**

In `VoiceControlService.kt`, add model loading after service starts. Add this after `overlay.setOnStopListener`:

```kotlin
// In onCreate(), after overlay setup:
serviceScope.launch {
    val llm = LlmEngine(this@VoiceControlService)
    if (llm.isDownloaded && !llm.isModelLoaded) {
        llm.loadModel()
    }
}
```

Actually, looking at the current code, `llmEngine` is already created in `onCreate()` but never loaded. We need to add loading. Modify `VoiceControlService.kt` to load the model on service start:

Find the `onStartCommand` method and add model loading:

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

Wait — looking at its actual onCreate more carefully:

```kotlin
llmEngine = LlmEngine(this)
```

And llmEngine is used in processVoiceCommand. The model needs to be loaded before inference. Let's add a `loadLlmModel()` method and call it from `initAndStart()`.

- [ ] **Step 3: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Full build**

Run: `.\gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL (full native compilation may take 5-10 minutes for the first build)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: wire up LLM model loading and fix download progress thread safety"
```



### Task 5: Update InstructionParser for Qwen Chat Template

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/llm/InstructionParser.kt`

- [ ] **Step 1: Update buildPrompt to use Qwen2.5 chat template**

The prompt sent to the LLM must follow Qwen2.5's chat template format (`<|im_start|>` / `<|im_end|>`) so the model generates structured JSON output. The current `buildPrompt()` just uses plain text — update it to include the system instruction and the Qwen template markers, since the JNI layer also wraps with Qwen template:

Actually, looking at the design spec, the JNI `infer()` function receives the full prompt and the tokenizer will process it. But the Qwen template wrapping should happen at the Kotlin level so the simulateInference path still works. The JNI `infer` function receives a prompt string — we should send the full chat-formatted prompt.

Replace `InstructionParser.buildPrompt()`:

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

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: update InstructionParser prompt to Qwen2.5 chat template format"
```



### Task 6: End-to-End Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Clean build**

```bash
.\gradlew clean :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify APK contains native libraries**

```bash
.\gradlew :app:assembleDebug
# Then check the APK for libllama_jni.so and libllama.so
python3 -m zipfile -l app/build/outputs/apk/debug/app-debug.apk | findstr ".so"
```

Or on Windows PowerShell:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem; $apk = [System.IO.Compression.ZipFile]::OpenRead("D:\code\ai\controlMoblie\app\build\outputs\apk\debug\app-debug.apk"); $apk.Entries | Where-Object { $_.FullName -like "lib/*" } | ForEach-Object { $_.FullName }
```

Expected: Files like `lib/arm64-v8a/libllama_jni.so`, `lib/arm64-v8a/libllama.so`, etc.

- [ ] **Step 3: Commit final state**

```bash
git add -A
git commit -m "chore: verify end-to-end build with llama.cpp native integration"
```



## Self-Review Checklist

- [ ] Spec coverage: every section in the design spec has a corresponding task
  - JNI bridge with real llama.cpp API → Task 3
  - Git submodule → Task 1
  - CMake build configuration → Task 2
  - Model download (already exists, fixed thread safety) → Task 4
  - InstructionParser Qwen template → Task 5
  - End-to-end verification → Task 6
- [ ] No placeholder code, TBD, or TODO
- [ ] Type consistency across tasks (NativeLlmEngine methods match JNI names)
- [ ] Each step has complete code
- [ ] Each step has a verification command