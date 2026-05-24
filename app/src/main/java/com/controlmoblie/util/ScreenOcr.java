package com.controlmoblie.util;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Compatibility stub delegating to {@link com.controlmoblie.ocr.ScreenOcr}.
 * Will be removed after ControlAccessibilityService is converted to Java.
 */
public class ScreenOcr {
    public static void init() {
        com.controlmoblie.ocr.ScreenOcr.init();
    }

    public static void init(Context context) {
        com.controlmoblie.ocr.ScreenOcr.init(context);
    }

    public static boolean isReady() {
        return com.controlmoblie.ocr.ScreenOcr.isReady();
    }

    public static void release() {
        com.controlmoblie.ocr.ScreenOcr.release();
    }
}
