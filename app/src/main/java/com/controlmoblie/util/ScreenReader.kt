package com.controlmoblie.util

import android.view.accessibility.AccessibilityNodeInfo
import com.controlmoblie.model.ScreenState

object ScreenReader {

    fun readScreen(root: AccessibilityNodeInfo?): ScreenState {
        if (root == null) return ScreenState()
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return ScreenState(
            nodeTexts = texts,
            packageName = root.packageName?.toString() ?: ""
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && node.isVisibleToUser) {
            texts.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, texts)
            child.recycle()
        }
    }

    fun buildScreenContext(state: ScreenState): String {
        val sb = StringBuilder()
        sb.appendLine("当前应用: ${state.packageName}")
        if (state.nodeTexts.isNotEmpty()) {
            sb.appendLine("屏幕可见内容:")
            state.nodeTexts.forEach { sb.appendLine("- $it") }
        }
        return sb.toString()
    }
}
