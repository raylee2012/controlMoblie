package com.controlmoblie.asr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

object SenseVoiceModelManager {

    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
    private const val MODEL_DIR_NAME = "paraformer-zh-small"
    private const val TEMP_FILE_NAME = "sense-voice-small.tar.bz2.tmp"
    private const val MODEL_FILENAME = "model.onnx"

    private const val TAG = "SenseVoiceModel"

    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        val modelFile = File(modelDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun getModelPath(context: Context): String {
        return File(context.filesDir, MODEL_DIR_NAME).absolutePath
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
                extractTarBz2(tmpFile, modelDir)
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

    private fun extractTarBz2(tarBz2File: File, destDir: File) {
        BufferedInputStream(tarBz2File.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    var entryCount = 0
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name
                            val slashIdx = entryName.indexOf('/')
                            val relativePath = if (slashIdx >= 0) entryName.substring(slashIdx + 1) else entryName
                            if (relativePath.isBlank()) {
                                entry = tarIn.nextTarEntry
                                continue
                            }
                            val destFile = File(destDir, relativePath)
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                            entryCount++
                            Log.d(TAG, "Extracted: $relativePath (${destFile.length()} bytes)")
                        }
                        entry = tarIn.nextTarEntry
                    }
                    Log.d(TAG, "Extracted $entryCount files to $destDir")
                }
            }
        }
    }
}
