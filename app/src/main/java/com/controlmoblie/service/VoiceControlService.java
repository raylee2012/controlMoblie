package com.controlmoblie.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.controlmoblie.asr.SenseVoiceModelManager;
import com.controlmoblie.asr.SpeechRecognizerManager;
import com.controlmoblie.execution.ExecutionEngine;
import com.controlmoblie.llm.LlmEngine;
import com.controlmoblie.model.Action;
import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.model.VoiceState;
import com.controlmoblie.service.binder.VoiceControlBinder;
import com.controlmoblie.tts.TtsModelManager;
import com.controlmoblie.tts.TtsSpeaker;
import com.controlmoblie.util.ScreenReader;

public class VoiceControlService extends Service {

    private static final String TAG = "VoiceControlService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "voice_control_channel";

    private SpeechRecognizerManager asrManager;
    private LlmEngine llmEngine;
    private ExecutionEngine executionEngine;
    private TtsSpeaker ttsSpeaker;
    private boolean isRunning = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VoiceControlBinder binder = new VoiceControlBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        llmEngine = new LlmEngine(this);
        executionEngine = new ExecutionEngine();
        ttsSpeaker = new TtsSpeaker(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (!isRunning) {
            isRunning = true;
            binder.setRecording(false);
            binder.setProcessing(false);
            binder.setExecuting(false);
            initAndStart();
        }

        return START_STICKY;
    }

    private void initAndStart() {
        try {
            if (!SenseVoiceModelManager.isModelReady(this)) {
                Log.w(TAG, "ASR model not ready");
                binder.setResult(new InstructionResult(false, "语音模型未下载", null));
                return;
            }
            String modelDir = SenseVoiceModelManager.getModelDir(this);
            SpeechRecognizerManager manager = new SpeechRecognizerManager(modelDir);
            if (!manager.init()) {
                Log.w(TAG, "ASR model init failed");
                binder.setResult(new InstructionResult(false, "语音模型加载失败", null));
                return;
            }
            asrManager = manager;

            loadLlmModel();
            loadTtsModel();
            startListening();
        } catch (Exception e) {
            Log.e(TAG, "initAndStart failed", e);
            binder.setResult(new InstructionResult(false, "初始化失败: " + e.getMessage(), null));
        }
    }

    private void loadLlmModel() {
        if (llmEngine.isDownloaded() && !llmEngine.isModelLoaded()) {
            binder.setProcessing(true);
            new Thread(() -> {
                boolean success = llmEngine.loadModel();
                if (success) {
                    Log.d(TAG, "LLM model loaded successfully");
                } else {
                    Log.w(TAG, "LLM model load failed, using simulateInference fallback");
                }
            }).start();
        }
    }

    private void loadTtsModel() {
        if (TtsModelManager.isModelReady(this) && !ttsSpeaker.isModelReady()) {
            new Thread(() -> {
                boolean success = ttsSpeaker.init();
                if (success) {
                    Log.d(TAG, "TTS model loaded successfully");
                } else {
                    Log.w(TAG, "TTS model load failed");
                }
            }).start();
        }
    }

    private void speakResult(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ttsSpeaker.isModelReady()) {
            mainHandler.postDelayed(() -> {
                if (isRunning) startListening();
            }, 1000);
            return;
        }
        if (asrManager != null) {
            asrManager.stopListening();
        }
        ttsSpeaker.speak(text, () -> {
            mainHandler.postDelayed(() -> {
                if (isRunning) startListening();
            }, 500);
        });
    }

    private void startListening() {
        SpeechRecognizerManager manager = asrManager;
        if (manager == null) return;

        binder.setRecording(true);
        binder.setProcessing(false);

        manager.setListener(new SpeechRecognizerManager.RecognitionListener() {
            @Override
            public void onPartialResult(String text) {
                binder.setRecognizedText(text);
            }

            @Override
            public void onFinalResult(String text) {
                binder.setRecognizedText(text);
                binder.setProcessing(true);
                binder.setRecording(false);
                processVoiceCommand(text);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "ASR error: " + message);
                binder.setRecording(false);
                binder.setResult(new InstructionResult(false, message, null));
                mainHandler.postDelayed(() -> {
                    if (isRunning) startListening();
                }, 1500);
            }

            @Override
            public void onReady() {
                // no-op
            }
        });

        manager.startListening();
    }

    private void processVoiceCommand(String userText) {
        ControlAccessibilityService service = ControlAccessibilityService.getInstance();
        String screenContext = "";
        if (service != null) {
            com.controlmoblie.model.ScreenState screenState = service.getLastScreenState();
            if (screenState != null) {
                screenContext = ScreenReader.buildScreenContext(screenState);
            }
        }

        String prompt = com.controlmoblie.llm.InstructionParser.buildPrompt(userText, screenContext);

        new Thread(() -> {
            try {
                InstructionResult result = llmEngine.infer(prompt, userText);
                mainHandler.post(() -> handleInferenceResult(result, userText));
            } catch (Exception e) {
                Log.e(TAG, "Inference failed", e);
                mainHandler.post(() -> {
                    InstructionResult errorResult = new InstructionResult(false, "处理失败: " + e.getMessage(), null);
                    binder.setResult(errorResult);
                    speakResult("处理失败");
                });
            }
        }).start();
    }

    private void handleInferenceResult(InstructionResult result, String userText) {
        Action action = result.getAction();
        if (action == null || (action.getType() == Action.ActionType.CLICK && ((Action.Click) action).getTarget().equals("error"))) {
            binder.setProcessing(false);
            binder.setResult(result);
            speakResult(result.getMessage());
            return;
        }

        binder.setExecuting(true);
        binder.setProcessing(false);
        executionEngine.execute(action, execResult -> {
            mainHandler.post(() -> {
                binder.setExecuting(false);
                binder.setResult(new InstructionResult(execResult.success, execResult.message, action));
                if (action.getType() == Action.ActionType.WAIT) {
                    mainHandler.postDelayed(() -> {
                        if (isRunning) startListening();
                    }, 1000);
                } else {
                    speakResult(execResult.message);
                }
            });
        });
    }

    private void stopListening() {
        isRunning = false;
        if (asrManager != null) {
            asrManager.stopListening();
        }
        binder.setRecording(false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (asrManager != null) {
            asrManager.release();
            asrManager = null;
        }
        llmEngine.unload();
        ttsSpeaker.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "语音控制",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("语音控制后台服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Mobile")
            .setContentText("语音控制运行中")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
}
