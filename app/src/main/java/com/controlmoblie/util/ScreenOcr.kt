package com.controlmoblie.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)

object ScreenOcr {

    private const val TAG = "ScreenOcr"
    private const val TRAINEDDATA_URL = "https://github.com/tesseract-ocr/tessdata/raw/3.04.00/chi_sim.traineddata"
    private const val TRAINEDDATA_NAME = "chi_sim.traineddata"
    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false

    val isReady: Boolean get() = isInitialized

    fun init(dataPath: String): Boolean {
        if (isInitialized) return true
        return try {
            val traineddata = File(dataPath, TRAINEDDATA_NAME)
            if (!traineddata.exists()) {
                Log.w(TAG, "Traineddata not found at ${traineddata.absolutePath}")
                return false
            }
            val api = TessBaseAPI()
            val initResult = api.init(dataPath, "chi_sim")
            Log.d(TAG, "TessBaseAPI.init returned: $initResult")
            if (!initResult) {
                Log.e(TAG, "TessBaseAPI.init failed for dataPath=$dataPath")
                return false
            }
            tessApi = api
            isInitialized = true
            Log.d(TAG, "Tesseract initialized with chi_sim")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init failed", e)
            false
        }
    }

    fun recognize(bitmap: Bitmap): List<OcrResult> {
        val api = tessApi ?: return emptyList()
        try {
            api.setImage(bitmap)
            val results = mutableListOf<OcrResult>()
            val iterator = api.resultIterator ?: return emptyList()
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                if (text != null && rect != null) {
                    results.add(
                        OcrResult(
                            text = text.trim(),
                            x = (rect.left + rect.right) / 2f,
                            y = (rect.top + rect.bottom) / 2f,
                            width = (rect.right - rect.left).toFloat(),
                            height = (rect.bottom - rect.top).toFloat()
                        )
                    )
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            api.clear()
            Log.d(TAG, "OCR found ${results.size} text blocks")
            return results
        } catch (e: Exception) {
            Log.e(TAG, "OCR recognize failed", e)
            return emptyList()
        }
    }

    fun release() {
        tessApi?.end()
        tessApi = null
        isInitialized = false
    }

    suspend fun downloadTraineddata(context: Context, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "tessdata").apply { mkdirs() }
            val dest = File(dir, TRAINEDDATA_NAME)
            if (dest.exists()) {
                withContext(Dispatchers.Main) { onProgress(1f) }
                return@withContext true
            }
            val tmpFile = File(context.cacheDir, "${TRAINEDDATA_NAME}.tmp")
            try {
                val url = URL(TRAINEDDATA_URL)
                val connection = url.openConnection().apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                }
                connection.connect()
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
                                    onProgress(totalRead.toFloat() / fileLength)
                                }
                            }
                        }
                    }
                }
                tmpFile.renameTo(dest)
                withContext(Dispatchers.Main) { onProgress(1f) }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Traineddata download failed", e)
                false
            } finally {
                tmpFile.delete()
            }
        }
    }

    fun isTraineddataReady(context: Context): Boolean {
        return File(context.filesDir, "tessdata/$TRAINEDDATA_NAME").exists()
    }
}
