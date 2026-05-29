package com.controlmoblie.llm;

public class NativeLlmEngine {
    static {
        try {
            System.loadLibrary("llama_jni");
        } catch (UnsatisfiedLinkError e) {
            // JNI library may not be available in test environments
        }
    }

    public static native boolean loadModel(String modelPath);
    public static native String infer(String prompt);
    public static native void unloadModel();
}
