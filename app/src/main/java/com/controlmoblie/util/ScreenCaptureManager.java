package com.controlmoblie.util;

import android.graphics.Bitmap;
import android.media.projection.MediaProjection;

/**
 * Compatibility stub delegating to {@link com.controlmoblie.ocr.ScreenCaptureManager}.
 * Will be removed after ControlAccessibilityService is converted to Java.
 */
public class ScreenCaptureManager {
    public static void init(MediaProjection projection, int width, int height, int density) {
        com.controlmoblie.ocr.ScreenCaptureManager.init(projection, width, height, density);
    }

    public static boolean isReady() {
        return com.controlmoblie.ocr.ScreenCaptureManager.isReady();
    }

    public static Bitmap capture() {
        return com.controlmoblie.ocr.ScreenCaptureManager.capture();
    }

    public static void release() {
        com.controlmoblie.ocr.ScreenCaptureManager.release();
    }
}
