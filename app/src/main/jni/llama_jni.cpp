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
static const llama_vocab *g_vocab = nullptr;
static llama_sampler *g_sampler = nullptr;

static std::string g_generated_text;

static const int CTX_SIZE = 512;
static const int MAX_TOKENS = 128;

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

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
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

    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = CTX_SIZE;
    ctx_params.n_batch = 256;
    ctx_params.n_threads = 2;
    ctx_params.n_threads_batch = 2;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("loadModel: failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

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

    if (!g_model || !g_ctx || !g_vocab || !g_sampler) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("{\"action\": \"error\", \"message\": \"model not loaded\"}");
    }

    LOGD("infer: prompt len=%zu", strlen(prompt_str));

    std::string prompt_std(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    std::vector<llama_token> tokens;
    tokens.reserve(CTX_SIZE);

    int n_tokens = llama_tokenize(
        g_vocab,
        prompt_std.c_str(),
        (int32_t)prompt_std.size(),
        nullptr,
        0,
        true,
        true
    );

    if (n_tokens < 0) {
        n_tokens = -n_tokens;
    }

    tokens.resize(n_tokens);

    n_tokens = llama_tokenize(
        g_vocab,
        prompt_std.c_str(),
        (int32_t)prompt_std.size(),
        tokens.data(),
        n_tokens,
        true,
        true
    );

    if (n_tokens < 0) {
        LOGE("infer: tokenization failed (buffer too small)");
        return env->NewStringUTF("{\"action\": \"error\", \"message\": \"tokenization failed\"}");
    }
    tokens.resize(n_tokens);

    llama_sampler_reset(g_sampler);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("infer: initial decode failed");
        return env->NewStringUTF("{\"action\": \"error\", \"message\": \"decode failed\"}");
    }

    std::string result;
    int n_decode = 0;

    while (n_decode < MAX_TOKENS) {
        llama_token new_token = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (llama_vocab_is_eog(g_vocab, new_token)) {
            break;
        }

        char token_buf[256];
        int token_len = llama_token_to_piece(g_vocab, new_token, token_buf, sizeof(token_buf), 0, true);
        if (token_len > 0) {
            result.append(token_buf, token_len);
        }

        if (result.find("<|im_end|>") != std::string::npos) {
            size_t end_pos = result.find("<|im_end|>");
            result = result.substr(0, end_pos);
            break;
        }

        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("infer: decode continuation failed at token %d", n_decode);
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
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}