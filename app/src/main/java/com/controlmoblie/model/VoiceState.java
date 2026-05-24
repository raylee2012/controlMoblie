package com.controlmoblie.model;

public class VoiceState {
    private boolean recording;
    private boolean processing;
    private boolean executing;

    public boolean isRecording() { return recording; }
    public void setRecording(boolean recording) { this.recording = recording; }
    public boolean isProcessing() { return processing; }
    public void setProcessing(boolean processing) { this.processing = processing; }
    public boolean isExecuting() { return executing; }
    public void setExecuting(boolean executing) { this.executing = executing; }
}
