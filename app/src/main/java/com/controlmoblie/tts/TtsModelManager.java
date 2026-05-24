package com.controlmoblie.tts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TtsModelManager {
    private static final String MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-echo.tar.bz2";
    private static final String MODEL_DIR_NAME = "vits-zh-hf-echo";
    private static final String TEMP_FILE_NAME = "vits-zh-hf-echo.tar.bz2.tmp";
    private static final String MODEL_FILENAME = "echo.onnx";
    private static final String TAG = "TtsModelManager";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface DownloadProgressCallback {
        void onProgress(float progress);
    }

    public static boolean isModelReady(Context context) {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);
        File modelFile = new File(modelDir, MODEL_FILENAME);
        return modelFile.exists() && modelFile.length() > 0;
    }

    public static String getModelDir(Context context) {
        return new File(context.getFilesDir(), MODEL_DIR_NAME).getAbsolutePath();
    }

    public static void downloadAndExtract(Context context, DownloadProgressCallback callback) {
        executor.execute(() -> {
            File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);
            if (isModelReady(context)) {
                mainHandler.post(() -> callback.onProgress(1f));
                return;
            }

            File tmpFile = new File(context.getCacheDir(), TEMP_FILE_NAME);
            try {
                mainHandler.post(() -> callback.onProgress(0f));
                URL url = new URL(MODEL_URL);
                java.net.URLConnection connection = url.openConnection();
                connection.setConnectTimeout(60000);
                connection.setReadTimeout(120000);
                connection.connect();
                long fileLength = connection.getContentLengthLong();
                java.io.InputStream input = connection.getInputStream();
                java.io.FileOutputStream output = new java.io.FileOutputStream(tmpFile);
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (fileLength > 0) {
                        final float progress = totalRead / (float) fileLength * 0.7f;
                        mainHandler.post(() -> callback.onProgress(progress));
                    }
                }
                output.close();
                input.close();

                mainHandler.post(() -> callback.onProgress(0.75f));
                if (modelDir.exists()) deleteDir(modelDir);
                modelDir.mkdirs();

                extractTarBz2(tmpFile, modelDir);

                mainHandler.post(() -> callback.onProgress(1f));
            } catch (Exception e) {
                Log.e(TAG, "Download/extract failed", e);
            } finally {
                tmpFile.delete();
            }
        });
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static void extractTarBz2(File tarBz2File, File destDir) {
        try (BufferedInputStream bis = new BufferedInputStream(new java.io.FileInputStream(tarBz2File));
             BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(bzIn)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    int slashIdx = entryName.indexOf('/');
                    String relativePath = slashIdx >= 0 ? entryName.substring(slashIdx + 1) : entryName;
                    if (relativePath.isEmpty() || relativePath.equals("rule.far")) {
                        continue;
                    }
                    File destFile = new File(destDir, relativePath);
                    File parent = destFile.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = tarIn.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Extraction failed", e);
        }
    }
}
