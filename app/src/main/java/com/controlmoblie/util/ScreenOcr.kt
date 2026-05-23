package com.controlmoblie.util

import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.net.URL

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)

object ScreenOcr {

    private const val TAG = "ScreenOcr"
    private const val TRAINEDDATA_URL = "https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata"
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
            api.init(dataPath, "chi_sim")
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
}
