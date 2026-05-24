package com.controlmoblie.service.binder;

import android.os.Binder;

import androidx.lifecycle.MutableLiveData;

import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.model.VoiceState;

public class VoiceControlBinder extends Binder {
    private final MutableLiveData<VoiceState> voiceState = new MutableLiveData<>(new VoiceState());
    private final MutableLiveData<String> recognizedText = new MutableLiveData<>();
    private final MutableLiveData<InstructionResult> lastResult = new MutableLiveData<>();

    public void setRecording(boolean recording) {
        VoiceState current = voiceState.getValue();
        if (current == null) current = new VoiceState();
        current.setRecording(recording);
        voiceState.postValue(current);
    }

    public void setProcessing(boolean processing) {
        VoiceState current = voiceState.getValue();
        if (current == null) current = new VoiceState();
        current.setProcessing(processing);
        voiceState.postValue(current);
    }

    public void setExecuting(boolean executing) {
        VoiceState current = voiceState.getValue();
        if (current == null) current = new VoiceState();
        current.setExecuting(executing);
        voiceState.postValue(current);
    }

    public void setRecognizedText(String text) {
        recognizedText.postValue(text);
    }

    public void setResult(InstructionResult result) {
        lastResult.postValue(result);
    }

    public MutableLiveData<VoiceState> getVoiceState() {
        return voiceState;
    }

    public MutableLiveData<String> getRecognizedText() {
        return recognizedText;
    }

    public MutableLiveData<InstructionResult> getLastResult() {
        return lastResult;
    }
}
