package com.controlmoblie.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

import com.controlmoblie.util.AppResolver

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
                isLoaded = false
                modelPath = dest.absolutePath
                return@withContext
            }
            // Download to temp file to avoid corrupt files on failure
            val tmp = File(context.filesDir, MODEL_TEMP_FILENAME)
            tmp.delete()
            val url = URL(MODEL_URL)
            val connection = url.openConnection().apply {
                connectTimeout = 30000
                readTimeout = 60000
            }
            connection.connect()
            val fileLength = connection.contentLengthLong
            val input = connection.getInputStream()
            tmp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (fileLength > 0) {
                        onProgress(totalRead.toFloat() / fileLength)
                    }
                }
            }
            input.close()
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

    suspend fun infer(prompt: String, userText: String): String {
        return withContext(Dispatchers.IO) {
            if (!isLoaded) return@withContext simulateInference(userText)
            if (useNative) {
                NativeLlmEngine.infer(prompt)
            } else {
                simulateInference(userText)
            }
        }
    }

    private fun simulateInference(userText: String): String {
        return when {
            userText.contains("返回") || userText.contains("后退") ->
                "{\"action\": \"navigate\", \"type\": \"back\"}"
            userText.contains("主页") || userText.contains("桌面") || userText.contains("主界面") ->
                "{\"action\": \"navigate\", \"type\": \"home\"}"
            userText.contains("最近") || userText.contains("任务") ->
                "{\"action\": \"navigate\", \"type\": \"recents\"}"
            userText.contains("点击") -> {
                val target = extractAfterKeyword(userText, listOf("点击"))
                "{\"action\": \"click\", \"target\": \"$target\"}"
            }
            userText.contains("打开") -> {
                val target = extractAfterKeyword(userText, listOf("打开"))
                val pkg = AppResolver.resolve(target)
                "{\"action\": \"open_app\", \"package\": \"$pkg\", \"displayName\": \"$target\"}"
            }
            userText.contains("上滑") || userText.contains("向上滑") ->
                "{\"action\": \"scroll\", \"direction\": \"up\", \"distance\": \"half\"}"
            userText.contains("下滑") || userText.contains("向下滑") ->
                "{\"action\": \"scroll\", \"direction\": \"down\", \"distance\": \"half\"}"
            userText.contains("左滑") ->
                "{\"action\": \"scroll\", \"direction\": \"left\", \"distance\": \"half\"}"
            userText.contains("右滑") ->
                "{\"action\": \"scroll\", \"direction\": \"right\", \"distance\": \"half\"}"
            userText.contains("输入") -> {
                val text = extractAfterKeyword(userText, listOf("输入"))
                "{\"action\": \"type\", \"text\": \"$text\"}"
            }
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
