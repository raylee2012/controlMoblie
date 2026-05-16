package com.controlmoblie.llm

object NativeLlmEngine {
    init {
        System.loadLibrary("llama")
    }

    external fun loadModel(modelPath: String): Boolean
    external fun infer(prompt: String): String
    external fun unloadModel()
}
