package com.controlmoblie.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TtsSpeaker {
    private static final String TAG = "TtsSpeaker";
    private static final int SPEAKER_ID = 0;
    private static final float SPEED = 1.0f;

    private OfflineTts tts;
    private AudioTrack audioTrack;
    private volatile boolean isSpeaking = false;
    private boolean isInitialized = false;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TtsSpeaker(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean init() {
        if (!TtsModelManager.isModelReady(context)) {
            Log.w(TAG, "TTS model not ready");
            return false;
        }
        try {
            String modelDir = TtsModelManager.getModelDir(context);
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel(modelDir + "/echo.onnx");
            vits.setLexicon(modelDir + "/lexicon.txt");
            vits.setTokens(modelDir + "/tokens.txt");
            OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
            modelConfig.setVits(vits);
            modelConfig.setNumThreads(2);
            modelConfig.setDebug(false);
            modelConfig.setProvider("cpu");

            OfflineTtsConfig config = new OfflineTtsConfig();
            config.setModel(modelConfig);
            config.setRuleFsts(modelDir + "/phone.fst," + modelDir + "/date.fst," + modelDir + "/number.fst");

            tts = new OfflineTts(null, config);
            isInitialized = true;
            Log.d(TAG, "TTS initialized, sampleRate=" + tts.sampleRate() + ", speakers=" + tts.numSpeakers());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "TTS init failed", e);
            tts = null;
            return false;
        }
    }

    public void speak(String text, OnSpeakCompleteCallback callback) {
        if (isSpeaking) {
            Log.d(TAG, "Already speaking, dropping: " + text);
            if (callback != null) callback.onDone();
            return;
        }
        final OfflineTts engine = tts;
        if (engine == null) {
            Log.w(TAG, "TTS not initialized, skipping speak");
            if (callback != null) callback.onDone();
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onDone();
            return;
        }
        isSpeaking = true;
        executor.execute(() -> {
            AudioTrack track = null;
            try {
                int sampleRate = engine.sampleRate();
                int bufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                );
                track = new AudioTrack.Builder()
                    .setAudioAttributes(
                        new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setAudioFormat(
                        new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setSampleRate(sampleRate)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
                audioTrack = track;
                track.play();
                int[] totalFrames = {0};

                engine.generateWithCallback(text, SPEAKER_ID, SPEED, new kotlin.jvm.functions.Function1<float[], Integer>() {
                    @Override
                    public Integer invoke(float[] samples) {
                        if (!isSpeaking) return 0;
                        audioTrack.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
                        totalFrames[0] += samples.length;
                        return isSpeaking ? 1 : 0;
                    }
                });

                int remaining = totalFrames[0] - audioTrack.getPlaybackHeadPosition();
                if (remaining > 0) {
                    Thread.sleep(remaining * 1000L / sampleRate + 100);
                }

                track.stop();
                track.release();
                audioTrack = null;
            } catch (Exception e) {
                Log.e(TAG, "TTS speak error", e);
                if (track != null) {
                    track.release();
                }
                audioTrack = null;
            } finally {
                isSpeaking = false;
                if (callback != null) callback.onDone();
            }
        });
    }

    public void stop() {
        isSpeaking = false;
        if (audioTrack != null) {
            audioTrack.stop();
        }
    }

    public void release() {
        isSpeaking = false;
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        if (tts != null) {
            tts.release();
            tts = null;
        }
        isInitialized = false;
        executor.shutdown();
    }

    public boolean isModelReady() {
        return TtsModelManager.isModelReady(context) && isInitialized;
    }

    public interface OnSpeakCompleteCallback {
        void onDone();
    }
}
