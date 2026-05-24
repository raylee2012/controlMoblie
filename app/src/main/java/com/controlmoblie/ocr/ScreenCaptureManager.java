package com.controlmoblie.ocr;

import android.graphics.Bitmap;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

public class ScreenCaptureManager {
    private static final String TAG = "ScreenCaptureManager";
    private static MediaProjection mediaProjection;
    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;
    private static boolean ready = false;

    @VisibleForTesting
    public static boolean bypassInit = false;

    public static void init(MediaProjection projection, int width, int height, int density) {
        if (ready) return;
        try {
            if (bypassInit) {
                ready = true;
                Log.d(TAG, "Init bypassed (test mode)");
                return;
            }
            if (projection == null) {
                Log.e(TAG, "MediaProjection is null");
                return;
            }
            mediaProjection = projection;
            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2);
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
            );
            ready = true;
            Log.d(TAG, "ScreenCaptureManager initialized: " + width + "x" + height);
        } catch (Exception e) {
            Log.e(TAG, "ScreenCaptureManager init failed", e);
            ready = false;
        }
    }

    public static boolean isReady() {
        return ready;
    }

    public static Bitmap capture() {
        ImageReader reader = imageReader;
        if (reader == null) return null;
        Image image = reader.acquireLatestImage();
        if (image == null) return null;
        try {
            Image.Plane[] planes = image.getPlanes();
            java.nio.ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            if (rowPadding > 0) {
                return Bitmap.createBitmap(bitmap, 0, 0, width, height);
            } else {
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "Capture failed", e);
            return null;
        } finally {
            image.close();
        }
    }

    public static void release() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        ready = false;
        bypassInit = false;
    }
}
