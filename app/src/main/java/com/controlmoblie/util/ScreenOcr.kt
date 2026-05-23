package com.controlmoblie.util

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)

object ScreenOcr {

    private const val TAG = "ScreenOcr"
    private var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    var isReady: Boolean = false
        private set

    @VisibleForTesting
    var bypassMlKitInit: Boolean = false

    fun init() {
        if (isReady) return
        try {
            if (bypassMlKitInit) {
                isReady = true
                Log.d(TAG, "ML Kit init bypassed (test mode)")
            } else {
                recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                isReady = true
                Log.d(TAG, "ML Kit TextRecognizer initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit init failed", e)
            isReady = false
        }
    }

    suspend fun recognize(bitmap: Bitmap): List<OcrResult> = suspendCancellableCoroutine { cont ->
        val rec = recognizer
        if (rec == null) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        rec.process(image)
            .addOnSuccessListener { visionText ->
                val results = visionText.textBlocks.flatMap { block ->
                    block.lines.flatMap { line ->
                        line.elements.mapNotNull { elem ->
                            val box = elem.boundingBox ?: return@mapNotNull null
                            OcrResult(
                                text = elem.text,
                                x = box.centerX().toFloat(),
                                y = box.centerY().toFloat(),
                                width = box.width().toFloat(),
                                height = box.height().toFloat()
                            )
                        }
                    }
                }
                cont.resume(results.filter { it.text.isNotBlank() })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR recognize failed", e)
                cont.resume(emptyList())
            }
    }

    fun release() {
        recognizer?.close()
        recognizer = null
        isReady = false
        bypassMlKitInit = false
    }
}
