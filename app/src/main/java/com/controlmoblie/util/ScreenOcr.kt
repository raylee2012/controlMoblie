package com.controlmoblie.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.FloatBuffer

data class OcrResult(val text: String, val x: Float, val y: Float, val width: Float, val height: Float)

object ScreenOcr {

    private const val TAG = "ScreenOcr"
    private const val DET_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/det.onnx"
    private const val REC_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/rec.onnx"
    private const val DICT_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/ocr-models/dict.txt"
    private const val MODEL_DIR = "paddle-ocr"

    private var env: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var isInitialized = false

    val isReady: Boolean get() = isInitialized

    fun init(modelDir: String): Boolean {
        if (isInitialized) return true
        return try {
            val detFile = File(modelDir, "det.onnx")
            val recFile = File(modelDir, "rec.onnx")
            val dictFile = File(modelDir, "dict.txt")
            if (!detFile.exists() || !recFile.exists() || !dictFile.exists()) {
                Log.w(TAG, "Model files not found")
                return false
            }
            OcrDict.load(dictFile.absolutePath)
            env = OrtEnvironment.getEnvironment()
            detSession = env!!.createSession(detFile.absolutePath)
            recSession = env!!.createSession(recFile.absolutePath)
            isInitialized = true
            Log.d(TAG, "PaddleOCR initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR init failed", e)
            false
        }
    }

    fun recognize(bitmap: Bitmap): List<OcrResult> {
        if (!isInitialized) return emptyList()
        return try {
            val boxes = detect(bitmap)
            if (boxes.isEmpty()) return emptyList()
            recognizeText(bitmap, boxes)
        } catch (e: Exception) {
            Log.e(TAG, "OCR recognize failed", e)
            emptyList()
        }
    }

    private fun detect(bitmap: Bitmap): List<FloatArray> {
        val det = detSession ?: return emptyList()
        val scaled = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val input = preprocessDet(scaled)
        val result = det.run(mapOf("x" to input))
        val output = result[0].value as Array<FloatArray>
        result.close()
        input.close()
        scaled.recycle()
        return postprocessDet(output[0], bitmap.width, bitmap.height)
    }

    private fun preprocessDet(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val data = FloatArray(1 * 3 * h * w)
        for (i in pixels.indices) {
            val px = pixels[i]
            data[i * 3] = ((px shr 16) and 0xFF) / 255f
            data[i * 3 + 1] = ((px shr 8) and 0xFF) / 255f
            data[i * 3 + 2] = (px and 0xFF) / 255f
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    private fun postprocessDet(probMap: FloatArray, origW: Int, origH: Int): List<FloatArray> {
        val h = 640
        val w = 640
        val mask = BooleanArray(h * w)
        for (i in probMap.indices) {
            mask[i] = probMap[i] > 0.3f
        }
        val visited = BooleanArray(h * w)
        val boxes = mutableListOf<FloatArray>()
        val ratioX = origW.toFloat() / w
        val ratioY = origH.toFloat() / h

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (mask[idx] && !visited[idx]) {
                    val queue = ArrayDeque<Int>()
                    queue.add(idx)
                    visited[idx] = true
                    var minX = x; var maxX = x
                    var minY = y; var maxY = y

                    while (queue.isNotEmpty()) {
                        val cur = queue.removeFirst()
                        val cx = cur % w; val cy = cur / w
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy
                        for ((dx, dy) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)) {
                            val nx = cx + dx; val ny = cy + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                val ni = ny * w + nx
                                if (mask[ni] && !visited[ni]) {
                                    visited[ni] = true
                                    queue.add(ni)
                                }
                            }
                        }
                    }

                    if ((maxX - minX) * (maxY - minY) >= 20) {
                        boxes.add(floatArrayOf(
                            minX * ratioX, minY * ratioY,
                            maxX * ratioX, maxY * ratioY
                        ))
                    }
                }
            }
        }
        return boxes
    }

    private fun recognizeText(bitmap: Bitmap, boxes: List<FloatArray>): List<OcrResult> {
        val rec = recSession ?: return emptyList()
        val results = mutableListOf<OcrResult>()

        for (box in boxes) {
            val left = maxOf(0f, box[0] - 2f).toInt()
            val top = maxOf(0f, box[1] - 2f).toInt()
            val right = minOf(bitmap.width.toFloat(), box[2] + 2f).toInt()
            val bottom = minOf(bitmap.height.toFloat(), box[3] + 2f).toInt()
            if (right <= left || bottom <= top) continue

            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            val input = preprocessRec(crop)
            val result = rec.run(mapOf("x" to input))
            val output = result[0].value as Array<FloatArray>
            result.close()
            input.close()
            crop.recycle()

            val text = decodeRecOutput(output)
            if (text.isNotBlank()) {
                results.add(OcrResult(
                    text = text,
                    x = (left + right) / 2f,
                    y = (top + bottom) / 2f,
                    width = (right - left).toFloat(),
                    height = (bottom - top).toFloat()
                ))
            }
        }
        return results
    }

    private fun preprocessRec(bitmap: Bitmap): OnnxTensor {
        val targetH = 32
        val ratio = targetH.toFloat() / bitmap.height
        val targetW = maxOf(8, (bitmap.width * ratio).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val pixels = IntArray(targetW * targetH)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        scaled.recycle()
        val data = FloatArray(1 * 3 * targetH * targetW)
        for (i in pixels.indices) {
            val px = pixels[i]
            data[i * 3] = ((px shr 16) and 0xFF) / 255f
            data[i * 3 + 1] = ((px shr 8) and 0xFF) / 255f
            data[i * 3 + 2] = (px and 0xFF) / 255f
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))
    }

    private fun decodeRecOutput(output: Array<FloatArray>): String {
        if (output.isEmpty()) return ""
        val t = output.size
        val c = output[0].size
        val indices = IntArray(t)
        for (i in 0 until t) {
            var maxIdx = 0
            var maxVal = Float.MIN_VALUE
            for (j in 0 until c) {
                if (output[i][j] > maxVal) {
                    maxVal = output[i][j]
                    maxIdx = j
                }
            }
            indices[i] = maxIdx
        }
        return OcrDict.decode(indices)
    }

    fun release() {
        detSession?.close()
        recSession?.close()
        env?.close()
        detSession = null
        recSession = null
        env = null
        isInitialized = false
    }

    suspend fun downloadModels(context: Context, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
            val files = listOf(
                "det.onnx" to DET_URL,
                "rec.onnx" to REC_URL,
                "dict.txt" to DICT_URL,
            )
            var allOk = true
            var downloaded = 0
            for ((name, urlStr) in files) {
                val dest = File(dir, name)
                if (dest.exists()) { downloaded++; continue }
                val tmp = File(context.cacheDir, "$name.tmp")
                try {
                    val url = URL(urlStr)
                    val conn = url.openConnection().apply { connectTimeout = 30000; readTimeout = 60000 }
                    conn.connect()
                    conn.getInputStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(8192)
                            var total = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                output.write(buf, 0, n)
                                total += n
                            }
                        }
                    }
                    tmp.renameTo(dest)
                    downloaded++
                    withContext(Dispatchers.Main) { onProgress(downloaded.toFloat() / files.size) }
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: $name", e)
                    tmp.delete()
                    allOk = false
                }
            }
            allOk
        }
    }

    fun isModelReady(context: Context): Boolean {
        val dir = File(context.filesDir, MODEL_DIR)
        return File(dir, "det.onnx").exists() &&
               File(dir, "rec.onnx").exists() &&
               File(dir, "dict.txt").exists()
    }
}
