package com.controlmoblie.util;

import android.view.accessibility.AccessibilityNodeInfo;

import com.controlmoblie.model.ScreenState;

import java.util.ArrayList;
import java.util.List;

public class ScreenReader {

    public static ScreenState readScreen(AccessibilityNodeInfo root) {
        if (root == null) return new ScreenState("", new ArrayList<>());
        List<String> texts = new ArrayList<>();
        collectTexts(root, texts);
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        return new ScreenState(pkg, texts);
    }

    private static void collectTexts(AccessibilityNodeInfo node, List<String> texts) {
        CharSequence txt = node.getText();
        if (txt != null && txt.length() > 0 && node.isVisibleToUser()) {
            texts.add(txt.toString());
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTexts(child, texts);
                child.recycle();
            }
        }
    }

    public static String buildScreenContext(ScreenState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前应用: ").append(state.getPackageName()).append("\n");
        List<String> texts = state.getTexts();
        if (texts != null && !texts.isEmpty()) {
            sb.append("屏幕可见内容:\n");
            for (String text : texts) {
                sb.append("- ").append(text).append("\n");
            }
        }
        return sb.toString();
    }
}
