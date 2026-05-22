package com.controlmoblie.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

object VoskModelManager {

    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
    private const val ZIP_TEMP_NAME = "vosk-model.zip.tmp"

    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val confFile = File(modelDir, "conf")
        val amDir = File(modelDir, "am")
        return confFile.exists() && amDir.exists() && amDir.isDirectory
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

            val tmpZip = File(context.cacheDir, ZIP_TEMP_NAME)
            try {
                withContext(Dispatchers.Main) { onProgress(0f) }
                val url = URL(MODEL_URL)
                val connection = url.openConnection().apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                }
                connection.connect()
                val fileLength = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    tmpZip.outputStream().use { output ->
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

                withContext(Dispatchers.Main) { onProgress(0.75f) }
                if (modelDir.exists()) modelDir.deleteRecursively()

                ZipInputStream(tmpZip.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName.startsWith("$MODEL_DIR_NAME/")) {
                            val relativePath = entryName.substringAfter('/')
                            if (relativePath.isBlank()) {
                                entry = zis.nextEntry
                                continue
                            }
                            val destFile = File(context.filesDir, "$MODEL_DIR_NAME/$relativePath")
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                withContext(Dispatchers.Main) { onProgress(1f) }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                tmpZip.delete()
            }
        }
    }
}