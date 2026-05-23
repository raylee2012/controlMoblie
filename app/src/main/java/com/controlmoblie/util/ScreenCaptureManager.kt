package com.controlmoblie.util

import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import androidx.annotation.VisibleForTesting

object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    var isReady: Boolean = false
        private set

    @VisibleForTesting
    var bypassInit: Boolean = false

    fun init(projection: MediaProjection?, width: Int, height: Int, density: Int) {
        if (isReady) return
        try {
            if (bypassInit) {
                isReady = true
                Log.d(TAG, "Init bypassed (test mode)")
                return
            }
            if (projection == null) {
                Log.e(TAG, "MediaProjection is null")
                return
            }
            mediaProjection = projection
            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            isReady = true
            Log.d(TAG, "ScreenCaptureManager initialized: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "ScreenCaptureManager init failed", e)
            isReady = false
        }
    }

    fun capture(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            null
        } finally {
            image.close()
        }
    }

    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        isReady = false
        bypassInit = false
    }
}
