package com.controlmoblie.ui.overlay;

import android.app.Service;
import android.widget.TextView;

import androidx.lifecycle.Observer;

import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.model.VoiceState;
import com.controlmoblie.service.binder.VoiceControlBinder;

import java.util.ArrayList;
import java.util.List;

public class OverlayPresenter {

    private final TextView statusView;
    private VoiceControlBinder binder;
    private final List<Observer> observers = new ArrayList<>();

    public OverlayPresenter(TextView statusView) {
        this.statusView = statusView;
    }

    public void attach(Service service, VoiceControlBinder binder) {
        this.binder = binder;
        Observer<VoiceState> voiceStateObserver = this::updateStatus;
        Observer<String> recognizedTextObserver = this::updateStatus;
        Observer<InstructionResult> lastResultObserver = this::updateStatus;

        binder.getVoiceState().observeForever(voiceStateObserver);
        binder.getRecognizedText().observeForever(recognizedTextObserver);
        binder.getLastResult().observeForever(lastResultObserver);

        observers.add(voiceStateObserver);
        observers.add(recognizedTextObserver);
        observers.add(lastResultObserver);
    }

    private void updateStatus(VoiceState vs) {
        if (vs == null || statusView == null) return;
        if (vs.isRecording()) {
            statusView.setText("正在聆听...");
        } else if (vs.isProcessing()) {
            statusView.setText("正在处理...");
        } else if (vs.isExecuting()) {
            statusView.setText("正在执行...");
        }
    }

    private void updateStatus(String text) {
        if (text != null && !text.isEmpty() && statusView != null) {
            statusView.setText(text);
        }
    }

    private void updateStatus(InstructionResult result) {
        if (result != null && statusView != null) {
            statusView.setText(result.getMessage());
        }
    }

    public void detach() {
        if (binder == null) return;
        for (Observer observer : observers) {
            binder.getVoiceState().removeObserver(observer);
            binder.getRecognizedText().removeObserver(observer);
            binder.getLastResult().removeObserver(observer);
        }
        observers.clear();
        binder = null;
    }
}
