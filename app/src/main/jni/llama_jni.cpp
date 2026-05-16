#include <jni.h>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_loadModel(
    JNIEnv* env,
    jobject /*this*/,
    jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    // llama.cpp model loading will go here
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_infer(
    JNIEnv* env,
    jobject /*this*/,
    jstring prompt) {
    const char* input = env->GetStringUTFChars(prompt, nullptr);
    std::string result = "{\"action\": \"navigate\", \"type\": \"home\"}";
    env->ReleaseStringUTFChars(prompt, input);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_controlmoblie_llm_NativeLlmEngine_unloadModel(
    JNIEnv* env,
    jobject /*this*/) {
    // Model cleanup will go here
}
