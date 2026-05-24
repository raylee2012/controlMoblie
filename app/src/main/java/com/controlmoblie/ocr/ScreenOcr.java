package com.controlmoblie.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

public class ScreenOcr {
    private static final String TAG = "ScreenOcr";
    private static TextRecognizer recognizer;
    private static boolean ready = false;

    @VisibleForTesting
    public static boolean bypassMlKitInit = false;

    public static void init() {
        init(null);
    }

    public static void init(Context context) {
        if (ready) return;
        try {
            if (bypassMlKitInit) {
                ready = true;
                Log.d(TAG, "ML Kit init bypassed (test mode)");
            } else {
                recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                ready = true;
                Log.d(TAG, "ML Kit TextRecognizer initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "ML Kit init failed", e);
            ready = false;
        }
    }

    public static boolean isReady() {
        return ready;
    }

    public static void recognize(Bitmap bitmap, OnOcrResultCallback callback) {
        TextRecognizer rec = recognizer;
        if (rec == null) {
            if (callback != null) callback.onResult(new ArrayList<>());
            return;
        }
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        rec.process(image)
            .addOnSuccessListener(visionText -> {
                List<OcrTextBlock> blocks = new ArrayList<>();
                for (com.google.mlkit.vision.text.Text.TextBlock block : visionText.getTextBlocks()) {
                    for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                        for (com.google.mlkit.vision.text.Text.Element elem : line.getElements()) {
                            android.graphics.Rect box = elem.getBoundingBox();
                            if (box != null) {
                                blocks.add(new OcrTextBlock(
                                    elem.getText(),
                                    box.centerX(),
                                    box.centerY(),
                                    box.width(),
                                    box.height()
                                ));
                            }
                        }
                    }
                }
                if (callback != null) callback.onResult(blocks);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "OCR recognize failed", e);
                if (callback != null) callback.onResult(new ArrayList<>());
            });
    }

    public static void release() {
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        ready = false;
        bypassMlKitInit = false;
    }

    public static class OcrTextBlock {
        public final String text;
        public final float x;
        public final float y;
        public final float width;
        public final float height;

        public OcrTextBlock(String text, float x, float y, float width, float height) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public interface OnOcrResultCallback {
        void onResult(List<OcrTextBlock> results);
    }
}
