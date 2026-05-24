package com.controlmoblie.model;

import java.util.List;

public class ScreenState {
    private final String packageName;
    private final List<String> texts;

    public ScreenState(String packageName, List<String> texts) {
        this.packageName = packageName;
        this.texts = texts;
    }

    public String getPackageName() { return packageName; }
    public List<String> getTexts() { return texts; }
}
