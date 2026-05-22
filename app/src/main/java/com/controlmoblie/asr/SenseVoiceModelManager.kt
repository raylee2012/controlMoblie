package com.controlmoblie.asr

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

object SenseVoiceModelManager {

    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/icefall-asr-zipformer-streaming-wenetspeech-20230615.tar.bz2"
    private const val MODEL_DIR_NAME = "wenetspeech-zipformer"
    private const val TEMP_FILE_NAME = "asr-model.tar.bz2.tmp"

    private const val TAG = "SenseVoiceModel"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val onnxFiles = modelDir.listFiles { f -> f.name.endsWith(".onnx") }
        return onnxFiles != null && onnxFiles.isNotEmpty()
    }

    fun getModelDir(context: Context): String {
        return File(context.filesDir, MODEL_DIR_NAME).absolutePath
    }

    fun getModelPath(context: Context): String = getModelDir(context)

    fun getEncoderDecoderJoiner(context: Context): Triple<String, String, String>? {
        val dir = File(context.filesDir, MODEL_DIR_NAME)
        val files = dir.listFiles { f -> f.name.endsWith(".onnx") } ?: return null
        val encoder = files.find { it.name.contains("encoder", ignoreCase = true) }?.absolutePath
        val decoder = files.find { it.name.contains("decoder", ignoreCase = true) }?.absolutePath
        val joiner = files.find { it.name.contains("joiner", ignoreCase = true) }?.absolutePath
        if (encoder != null && decoder != null && joiner != null) {
            return Triple(encoder, decoder, joiner)
        }
        val single = files.firstOrNull()
        return if (single != null) Triple(single.absolutePath, single.absolutePath, single.absolutePath) else null
    }

    suspend fun downloadAndExtract(context: Context, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val modelDir = File(context.filesDir, MODEL_DIR_NAME)
            if (isModelReady(context)) {
                withContext(Dispatchers.Main) { onProgress(1f) }
                return@withContext true
            }

            val tmpFile = File(context.cacheDir, TEMP_FILE_NAME)
            try {
                withContext(Dispatchers.Main) { onProgress(0f) }
                val url = URL(MODEL_URL)
                val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 60000
                    readTimeout = 120000
                }
                connection.connect()
                val responseCode = connection.responseCode
                Log.d(TAG, "Download response: code=$responseCode, contentLength=${connection.contentLength}")
                if (responseCode != 200) {
                    Log.e(TAG, "Download failed with HTTP $responseCode")
                    return@withContext false
                }
                val fileLength = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (fileLength > 0) {
                                withContext(Dispatchers.Main) {
                                    onProgress(totalRead.toFloat() / fileLength * 0.7f)
                                }
                            }
                        }
                    }
                }

                Log.d(TAG, "Download complete: ${tmpFile.length()} bytes")
                if (tmpFile.length() < 1000) {
                    Log.e(TAG, "Downloaded file too small, likely not a valid model")
                    return@withContext false
                }

                withContext(Dispatchers.Main) { onProgress(0.75f) }
                if (modelDir.exists()) modelDir.deleteRecursively()
                modelDir.mkdirs()

                Log.d(TAG, "Starting extraction...")
                extractTarBz2(tmpFile, modelDir) { fraction ->
                    mainHandler.post { onProgress(0.75f + fraction * 0.25f) }
                }
                Log.d(TAG, "Extraction complete")

                withContext(Dispatchers.Main) { onProgress(1f) }
                isModelReady(context)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                tmpFile.delete()
            }
        }
    }

    private fun extractTarBz2(tarBz2File: File, destDir: File, onProgress: (Float) -> Unit) {
        var entryCount = 0
        BufferedInputStream(tarBz2File.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.indexOf('/') >= 0) {
                            val relativePath = entry.name.substring(entry.name.indexOf('/') + 1)
                            if (relativePath.isNotBlank()) entryCount++
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
        Log.d(TAG, "Total entries to extract: $entryCount")

        val total = maxOf(entryCount, 1)
        var extracted = 0
        BufferedInputStream(tarBz2File.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name
                            val slashIdx = entryName.indexOf('/')
                            val relativePath = if (slashIdx >= 0) entryName.substring(slashIdx + 1) else entryName
                            if (relativePath.isNotBlank()) {
                                val destFile = File(destDir, relativePath)
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { out ->
                                    tarIn.copyTo(out)
                                }
                                extracted++
                                onProgress(extracted.toFloat() / total)
                                Log.d(TAG, "Extracted: $relativePath (${destFile.length()} bytes)")
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                    Log.d(TAG, "Extracted $extracted files to $destDir")
                }
            }
        }
    }
}
