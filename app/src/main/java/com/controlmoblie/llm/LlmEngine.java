package com.controlmoblie.llm;

import android.content.Context;
import android.util.Log;

import com.controlmoblie.model.Action;
import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.resolver.AppResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LlmEngine {
    private static final String TAG = "LlmEngine";
    private static final String MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";
    private static final String MODEL_FILENAME = "qwen2.5-0.5b-q4.gguf";
    private static final String MODEL_TEMP_FILENAME = "qwen2.5-0.5b-q4.gguf.tmp";

    private final Context context;
    private boolean isLoaded = false;
    private String modelPath = "";
    private boolean useNative = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LlmEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isModelLoaded() {
        return isLoaded;
    }

    public boolean isDownloaded() {
        return new File(context.getFilesDir(), MODEL_FILENAME).exists();
    }

    public void downloadModel(DownloadProgressCallback callback) {
        executor.execute(() -> {
            try {
                File dest = new File(context.getFilesDir(), MODEL_FILENAME);
                if (dest.exists()) {
                    isLoaded = false;
                    modelPath = dest.getAbsolutePath();
                    if (callback != null) callback.onProgress(1f);
                    return;
                }
                File tmp = new File(context.getFilesDir(), MODEL_TEMP_FILENAME);
                tmp.delete();
                URL url = new URL(MODEL_URL);
                java.net.URLConnection connection = url.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.connect();
                long fileLength = connection.getContentLengthLong();
                java.io.InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(tmp);
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (fileLength > 0 && callback != null) {
                        final float progress = totalRead / (float) fileLength;
                        callback.onProgress(progress);
                    }
                }
                output.close();
                input.close();
                tmp.renameTo(dest);
                isLoaded = false;
                modelPath = dest.getAbsolutePath();
                if (callback != null) callback.onProgress(1f);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                if (callback != null) callback.onProgress(-1f);
            }
        });
    }

    public boolean loadModel() {
        File dest = new File(context.getFilesDir(), MODEL_FILENAME);
        if (!dest.exists()) return false;
        try {
            useNative = NativeLlmEngine.loadModel(dest.getAbsolutePath());
            isLoaded = useNative;
            modelPath = dest.getAbsolutePath();
            return useNative;
        } catch (UnsatisfiedLinkError e) {
            isLoaded = true;
            modelPath = dest.getAbsolutePath();
            return false;
        }
    }

    public InstructionResult infer(String prompt, String userText) {
        String raw;
        if (!isLoaded) {
            raw = simulateInference(userText);
        } else if (useNative) {
            raw = NativeLlmEngine.infer(prompt);
        } else {
            raw = simulateInference(userText);
        }
        Action action = InstructionParser.parse(raw);
        boolean success = action.getType() != Action.ActionType.CLICK || !((Action.Click) action).getTarget().equals("error");
        return new InstructionResult(success, raw, action);
    }

    private String simulateInference(String userText) {
        String templateJson = CommandTemplates.match(userText);
        if (templateJson != null) return templateJson;

        if (userText.contains("朋友圈")) {
            return "{\"action\": \"open_wechat_page\", \"page\": \"moments\"}";
        }
        if (userText.contains("扫一扫") || userText.contains("扫码")) {
            return "{\"action\": \"open_wechat_page\", \"page\": \"scan\"}";
        }
        if (userText.contains("返回") || userText.contains("后退")) {
            return "{\"action\": \"navigate\", \"type\": \"back\"}";
        }
        if (userText.contains("主页") || userText.contains("桌面") || userText.contains("主界面")) {
            return "{\"action\": \"navigate\", \"type\": \"home\"}";
        }
        if (userText.contains("最近") || userText.contains("任务")) {
            return "{\"action\": \"navigate\", \"type\": \"recents\"}";
        }
        if (userText.contains("点击")) {
            String target = extractAfterKeyword(userText, Arrays.asList("点击"));
            return "{\"action\": \"click\", \"target\": \"" + target + "\"}";
        }
        if (userText.contains("打开")) {
            String target = extractAfterKeyword(userText, Arrays.asList("打开"));
            String pkg = AppResolver.resolve(target);
            return "{\"action\": \"open_app\", \"package\": \"" + pkg + "\", \"displayName\": \"" + target + "\"}";
        }
        if (userText.contains("上滑") || userText.contains("向上滑")) {
            return "{\"action\": \"scroll\", \"direction\": \"up\", \"distance\": \"half\"}";
        }
        if (userText.contains("下滑") || userText.contains("向下滑")) {
            return "{\"action\": \"scroll\", \"direction\": \"down\", \"distance\": \"half\"}";
        }
        if (userText.contains("左滑")) {
            return "{\"action\": \"scroll\", \"direction\": \"left\", \"distance\": \"half\"}";
        }
        if (userText.contains("右滑")) {
            return "{\"action\": \"scroll\", \"direction\": \"right\", \"distance\": \"half\"}";
        }
        if (userText.contains("输入")) {
            String text = extractAfterKeyword(userText, Arrays.asList("输入"));
            return "{\"action\": \"type\", \"text\": \"" + text + "\"}";
        }
        return "{\"action\": \"error\", \"message\": \"无法理解指令\"}";
    }

    private String extractAfterKeyword(String text, java.util.List<String> keywords) {
        for (String kw : keywords) {
            int idx = text.indexOf(kw);
            if (idx >= 0) {
                String after = text.substring(idx + kw.length()).trim();
                if (!after.isEmpty()) return after;
            }
        }
        return text;
    }

    public void unload() {
        isLoaded = false;
        if (useNative) {
            try {
                NativeLlmEngine.unloadModel();
            } catch (Exception e) {
                // ignore
            }
            useNative = false;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public interface DownloadProgressCallback {
        void onProgress(float progress);
    }
}
