package com.controlmoblie.ui.main;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.controlmoblie.asr.SenseVoiceModelManager;
import com.controlmoblie.llm.LlmEngine;
import com.controlmoblie.model.DownloadProgress;
import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.model.VoiceState;
import com.controlmoblie.ocr.ScreenOcr;
import com.controlmoblie.service.VoiceControlService;
import com.controlmoblie.service.binder.VoiceControlBinder;
import com.controlmoblie.tts.TtsModelManager;
import com.controlmoblie.util.PermissionHelper;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> hasOverlay = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasAudio = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasAccessibility = new MutableLiveData<>();
    private final MutableLiveData<Boolean> asrModelReady = new MutableLiveData<>();
    private final MutableLiveData<Boolean> llmModelReady = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ttsModelReady = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ocrReady = new MutableLiveData<>();
    private final MutableLiveData<DownloadProgress> asrProgress = new MutableLiveData<>();
    private final MutableLiveData<DownloadProgress> llmProgress = new MutableLiveData<>();
    private final MutableLiveData<DownloadProgress> ttsProgress = new MutableLiveData<>();
    private final MutableLiveData<VoiceState> voiceState = new MutableLiveData<>();
    private final MutableLiveData<String> recognizedText = new MutableLiveData<>();
    private final MutableLiveData<InstructionResult> lastResult = new MutableLiveData<>();

    private final LlmEngine llmEngine;
    private VoiceControlBinder binder;
    private boolean bound = false;
    private final List<Observer> observers = new ArrayList<>();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VoiceControlBinder) service;
            observeBinder();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };

    public MainViewModel(@NonNull Application application) {
        super(application);
        llmEngine = new LlmEngine(application);
    }

    private void observeBinder() {
        if (binder == null) return;
        Observer<VoiceState> voiceStateObserver = vs -> MainViewModel.this.voiceState.postValue(vs);
        Observer<String> recognizedTextObserver = text -> MainViewModel.this.recognizedText.postValue(text);
        Observer<InstructionResult> lastResultObserver = result -> MainViewModel.this.lastResult.postValue(result);

        binder.getVoiceState().observeForever(voiceStateObserver);
        binder.getRecognizedText().observeForever(recognizedTextObserver);
        binder.getLastResult().observeForever(lastResultObserver);

        observers.add(voiceStateObserver);
        observers.add(recognizedTextObserver);
        observers.add(lastResultObserver);
    }

    private void removeObservers() {
        if (binder == null) return;
        for (Observer observer : observers) {
            binder.getVoiceState().removeObserver(observer);
            binder.getRecognizedText().removeObserver(observer);
            binder.getLastResult().removeObserver(observer);
        }
        observers.clear();
    }

    public void bindService(Context context) {
        if (!bound) {
            Intent intent = new Intent(context, VoiceControlService.class);
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            bound = true;
        }
    }

    public void unbindService(Context context) {
        if (bound) {
            removeObservers();
            context.unbindService(connection);
            bound = false;
            binder = null;
        }
    }

    @Override
    protected void onCleared() {
        unbindService(getApplication());
        llmEngine.shutdown();
    }

    public void refreshPermissions(Context context) {
        hasOverlay.postValue(PermissionHelper.hasOverlayPermission(context));
        hasAudio.postValue(PermissionHelper.hasRecordPermission(context));
        hasAccessibility.postValue(PermissionHelper.isAccessibilityServiceEnabled(context));
    }

    public void refreshModelStatus(Context context) {
        asrModelReady.postValue(SenseVoiceModelManager.isModelReady(context));
        llmModelReady.postValue(llmEngine.isDownloaded());
        ttsModelReady.postValue(TtsModelManager.isModelReady(context));
        ocrReady.postValue(ScreenOcr.isReady());
    }

    public void downloadAsrModel(Context context) {
        asrProgress.postValue(new DownloadProgress(0f));
        SenseVoiceModelManager.downloadAndExtract(context, progress -> {
            asrProgress.postValue(new DownloadProgress(progress));
            if (progress >= 1f) {
                asrModelReady.postValue(SenseVoiceModelManager.isModelReady(context));
            }
        });
    }

    public void downloadLlmModel(Context context) {
        llmProgress.postValue(new DownloadProgress(0f));
        llmEngine.downloadModel(progress -> {
            llmProgress.postValue(new DownloadProgress(progress));
            if (progress >= 1f) {
                llmModelReady.postValue(llmEngine.isDownloaded());
            } else if (progress < 0f) {
                llmModelReady.postValue(false);
            }
        });
    }

    public void downloadTtsModel(Context context) {
        ttsProgress.postValue(new DownloadProgress(0f));
        TtsModelManager.downloadAndExtract(context, progress -> {
            ttsProgress.postValue(new DownloadProgress(progress));
            if (progress >= 1f) {
                ttsModelReady.postValue(TtsModelManager.isModelReady(context));
            }
        });
    }

    public void setOcrReady(boolean ready) {
        ocrReady.setValue(ready);
    }

    public MutableLiveData<Boolean> getHasOverlay() { return hasOverlay; }
    public MutableLiveData<Boolean> getHasAudio() { return hasAudio; }
    public MutableLiveData<Boolean> getHasAccessibility() { return hasAccessibility; }
    public MutableLiveData<Boolean> getAsrModelReady() { return asrModelReady; }
    public MutableLiveData<Boolean> getLlmModelReady() { return llmModelReady; }
    public MutableLiveData<Boolean> getTtsModelReady() { return ttsModelReady; }
    public MutableLiveData<Boolean> getOcrReady() { return ocrReady; }
    public MutableLiveData<DownloadProgress> getAsrProgress() { return asrProgress; }
    public MutableLiveData<DownloadProgress> getLlmProgress() { return llmProgress; }
    public MutableLiveData<DownloadProgress> getTtsProgress() { return ttsProgress; }
    public MutableLiveData<VoiceState> getVoiceState() { return voiceState; }
    public MutableLiveData<String> getRecognizedText() { return recognizedText; }
    public MutableLiveData<InstructionResult> getLastResult() { return lastResult; }
}
