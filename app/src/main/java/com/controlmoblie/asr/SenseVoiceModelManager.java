package com.controlmoblie.asr;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SenseVoiceModelManager {
    private static final String MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/icefall-asr-zipformer-streaming-wenetspeech-20230615.tar.bz2";
    private static final String MODEL_DIR_NAME = "wenetspeech-zipformer";
    private static final String TEMP_FILE_NAME = "asr-model.tar.bz2.tmp";
    private static final String TAG = "SenseVoiceModel";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface DownloadProgressCallback {
        void onProgress(float progress);
    }

    public static boolean isModelReady(Context context) {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);
        if (!modelDir.exists() || !modelDir.isDirectory()) return false;
        List<File> onnxFiles = findRecursive(modelDir, name -> name.endsWith(".onnx"));
        return !onnxFiles.isEmpty();
    }

    public static String getModelDir(Context context) {
        return new File(context.getFilesDir(), MODEL_DIR_NAME).getAbsolutePath();
    }

    public static String getModelPath(Context context) {
        return getModelDir(context);
    }

    private static List<File> findRecursive(File dir, java.util.function.Predicate<String> predicate) {
        List<File> result = new ArrayList<>();
        collectFiles(dir, predicate, result);
        return result;
    }

    private static void collectFiles(File dir, java.util.function.Predicate<String> predicate, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, predicate, result);
            } else if (predicate.test(f.getName())) {
                result.add(f);
            }
        }
    }

    public static String[] getEncoderDecoderJoiner(Context context) {
        File dir = new File(context.getFilesDir(), MODEL_DIR_NAME);
        List<File> files = findRecursive(dir, name -> name.endsWith(".onnx"));
        if (files.isEmpty()) return null;
        String encoder = null, decoder = null, joiner = null;
        for (File f : files) {
            if (f.getName().toLowerCase().contains("encoder")) encoder = f.getAbsolutePath();
            else if (f.getName().toLowerCase().contains("decoder")) decoder = f.getAbsolutePath();
            else if (f.getName().toLowerCase().contains("joiner")) joiner = f.getAbsolutePath();
        }
        if (encoder != null && decoder != null && joiner != null) {
            return new String[]{encoder, decoder, joiner};
        }
        String single = files.get(0).getAbsolutePath();
        return new String[]{single, single, single};
    }

    public static String findTokensFile(Context context) {
        File dir = new File(context.getFilesDir(), MODEL_DIR_NAME);
        List<File> files = findRecursive(dir, name -> name.equals("tokens.txt"));
        return files.isEmpty() ? null : files.get(0).getAbsolutePath();
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
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(60000);
                connection.setReadTimeout(120000);
                connection.connect();
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Download response: code=" + responseCode + ", contentLength=" + connection.getContentLength());
                if (responseCode != 200) {
                    Log.e(TAG, "Download failed with HTTP " + responseCode);
                    return;
                }
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

                Log.d(TAG, "Download complete: " + tmpFile.length() + " bytes");
                if (tmpFile.length() < 1000) {
                    Log.e(TAG, "Downloaded file too small, likely not a valid model");
                    return;
                }

                mainHandler.post(() -> callback.onProgress(0.75f));
                if (modelDir.exists()) deleteDir(modelDir);
                modelDir.mkdirs();

                Log.d(TAG, "Starting extraction...");
                extractTarBz2(tmpFile, modelDir, fraction -> {
                    mainHandler.post(() -> callback.onProgress(0.75f + fraction * 0.25f));
                });
                Log.d(TAG, "Extraction complete");

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

    private static void extractTarBz2(File tarBz2File, File destDir, java.util.function.Consumer<Float> onProgress) {
        int entryCount = 0;
        try (BufferedInputStream bis = new BufferedInputStream(new java.io.FileInputStream(tarBz2File));
             BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(bzIn)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().indexOf('/') >= 0) {
                    String relativePath = entry.getName().substring(entry.getName().indexOf('/') + 1);
                    if (!relativePath.isEmpty()) entryCount++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to count entries", e);
        }

        Log.d(TAG, "Total entries to extract: " + entryCount);
        int total = Math.max(entryCount, 1);
        final int[] extracted = {0};
        try (BufferedInputStream bis = new BufferedInputStream(new java.io.FileInputStream(tarBz2File));
             BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(bzIn)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    int slashIdx = entryName.indexOf('/');
                    String relativePath = slashIdx >= 0 ? entryName.substring(slashIdx + 1) : entryName;
                    if (!relativePath.isEmpty()) {
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
                        extracted[0]++;
                        onProgress.accept(extracted[0] / (float) total);
                        Log.d(TAG, "Extracted: " + relativePath + " (" + destFile.length() + " bytes)");
                    }
                }
            }
            Log.d(TAG, "Extracted " + extracted[0] + " files to " + destDir);
        } catch (Exception e) {
            Log.e(TAG, "Extraction failed", e);
        }
    }
}
