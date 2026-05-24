package com.controlmoblie.asr;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.EndpointRule;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig;
import com.k2fsa.sherpa.onnx.OnlineCtcFstDecoderConfig;
import com.k2fsa.sherpa.onnx.OnlineLMConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import java.io.File;

public class SpeechRecognizerManager {
    private static final String TAG = "SpeechRecognizerManager";
    private static final int SAMPLE_RATE = 16000;

    public interface RecognitionListener {
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String message);
        void onReady();
    }

    private final String modelPath;
    private OnlineRecognizer recognizer;
    private OnlineStream stream;
    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean isListening = false;
    private RecognitionListener listener;

    public SpeechRecognizerManager(String modelPath) {
        this.modelPath = modelPath;
    }

    public void setListener(RecognitionListener listener) {
        this.listener = listener;
    }

    public boolean init() {
        try {
            File dir = new File(modelPath);
            File[] allFiles = dir.listFiles();
            java.util.List<File> onnxFiles = new java.util.ArrayList<>();
            if (allFiles != null) {
                collectOnnxFiles(dir, onnxFiles);
            }
            if (onnxFiles.isEmpty()) {
                Log.e(TAG, "No .onnx files found in " + modelPath);
                if (listener != null) listener.onError("模型文件缺失");
                return false;
            }
            String encoder = findFileByName(onnxFiles, "encoder");
            String decoder = findFileByName(onnxFiles, "decoder");
            String joiner = findFileByName(onnxFiles, "joiner");
            Log.d(TAG, "encoder=" + encoder + ", decoder=" + decoder + ", joiner=" + joiner);

            String tokensFile = findFileByName(dir, "tokens.txt");
            if (tokensFile == null) tokensFile = modelPath + "/tokens.txt";

            FeatureConfig featConfig = new FeatureConfig(SAMPLE_RATE, 80, 0.0f);

            OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig(encoder, decoder, joiner);
            OnlineModelConfig modelConfig = new OnlineModelConfig();
            modelConfig.setTransducer(transducer);
            modelConfig.setTokens(tokensFile);
            modelConfig.setNumThreads(2);
            modelConfig.setProvider("cpu");
            modelConfig.setDebug(false);

            EndpointRule rule1 = new EndpointRule(false, 2.4f, 0.0f);
            EndpointRule rule2 = new EndpointRule(true, 1.2f, 0.0f);
            EndpointRule rule3 = new EndpointRule(false, 0.0f, 20.0f);
            EndpointConfig endpointConfig = new EndpointConfig(rule1, rule2, rule3);

            OnlineRecognizerConfig config = new OnlineRecognizerConfig(
                featConfig,
                modelConfig,
                new OnlineLMConfig(),
                new OnlineCtcFstDecoderConfig(),
                new HomophoneReplacerConfig(),
                endpointConfig,
                true,
                "greedy_search",
                4,
                "",
                1.5f,
                "",
                "",
                0.0f
            );

            recognizer = new OnlineRecognizer(null, config);
            Log.d(TAG, "OnlineRecognizer initialized");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load model", e);
            if (listener != null) listener.onError("模型加载失败: " + e.getMessage());
            return false;
        }
    }

    private void collectOnnxFiles(File dir, java.util.List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectOnnxFiles(f, result);
            } else if (f.getName().endsWith(".onnx")) {
                result.add(f);
            }
        }
    }

    private String findFileByName(java.util.List<File> files, String keyword) {
        for (File f : files) {
            if (f.getName().toLowerCase().contains(keyword.toLowerCase())) {
                return f.getAbsolutePath();
            }
        }
        return files.get(0).getAbsolutePath();
    }

    private String findFileByName(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                String found = findFileByName(f, name);
                if (found != null) return found;
            } else if (f.getName().equals(name)) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    public void startListening() {
        if (isListening) stopListening();
        OnlineRecognizer rec = recognizer;
        if (rec == null) {
            if (listener != null) listener.onError("模型未加载");
            return;
        }

        try {
            stream = rec.createStream("");

            int minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = Math.max(minBufSize, SAMPLE_RATE / 5);

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                if (listener != null) listener.onError("麦克风初始化失败");
                return;
            }

            audioRecord.startRecording();
            isListening = true;
            if (listener != null) listener.onReady();

            captureThread = new Thread(() -> {
                short[] buffer = new short[bufferSize / 2];
                String lastPartialText = "";

                while (isListening) {
                    int len = audioRecord.read(buffer, 0, buffer.length);
                    if (len <= 0) continue;

                    float[] samples = new float[len];
                    for (int i = 0; i < len; i++) {
                        samples[i] = buffer[i] / 32768f;
                    }
                    OnlineStream s = stream;
                    if (s == null) break;
                    s.acceptWaveform(samples, SAMPLE_RATE);

                    while (rec.isReady(s)) {
                        rec.decode(s);
                    }

                    String text = rec.getResult(s).getText().trim();

                    if (!text.isEmpty() && rec.isEndpoint(s)) {
                        rec.reset(s);
                        if (listener != null) listener.onFinalResult(text);
                        lastPartialText = "";
                    } else if (!text.isEmpty() && !text.equals(lastPartialText)) {
                        if (listener != null) listener.onPartialResult(text);
                        lastPartialText = text;
                    }
                }
            });
            captureThread.setName("Asr-capture");
            captureThread.setPriority(Thread.MAX_PRIORITY);
            captureThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start listening", e);
            if (listener != null) listener.onError("启动识别失败: " + e.getMessage());
        }
    }

    public void stopListening() {
        isListening = false;
        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (stream != null) {
            stream.release();
            stream = null;
        }
    }

    public void release() {
        stopListening();
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
    }
}
