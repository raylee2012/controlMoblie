package com.controlmoblie.model;

public class DownloadProgress {
    private final float progress;

    public DownloadProgress(float progress) {
        this.progress = progress;
    }

    public float getProgress() { return progress; }
    public boolean isInProgress() { return progress >= 0f && progress < 1f; }
}
