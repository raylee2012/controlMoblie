package com.controlmoblie.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.controlmoblie.model.*

class ControlAccessibilityService : AccessibilityService() {

    var lastScreenState: ScreenState = ScreenState()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            updateScreenState()
        }
    }

    override fun onInterrupt() {}

    private fun updateScreenState() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        root.recycle()
        lastScreenState = ScreenState(
            nodeTexts = texts,
            packageName = packageName
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) texts.add(text)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }

    fun executeAction(action: Action, onResult: (Boolean, String) -> Unit) {
        when (action) {
            is Action.Click -> executeClick(action, onResult)
            is Action.OpenApp -> executeOpenApp(action, onResult)
            is Action.Navigate -> executeNavigate(action, onResult)
            is Action.Scroll -> executeScroll(action, onResult)
            is Action.Type -> executeType(action, onResult)
            is Action.Wait -> {
                android.os.Handler(mainLooper).postDelayed({
                    onResult(true, "waited ${action.ms}ms")
                }, action.ms)
            }
            is Action.Sequence -> executeSequence(action, onResult)
        }
    }

    private fun executeClick(action: Action.Click, onResult: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { onResult(false, "no window root"); return }

        val nodes = root.findAccessibilityNodeInfosByText(action.target)
        if (nodes.isEmpty()) {
            root.recycle()
            onResult(false, "target '${action.target}' not found")
            return
        }

        val node = nodes[0]
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        root.recycle()
        onResult(clicked, if (clicked) "clicked ${action.target}" else "failed to click ${action.target}")
    }

    private fun executeOpenApp(action: Action.OpenApp, onResult: (Boolean, String) -> Unit) {
        val intent = packageManager.getLaunchIntentForPackage(action.`package`)
        if (intent == null) {
            onResult(false, "package ${action.`package`} not found")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        onResult(true, "opened ${action.`package`}")
    }

    private fun executeNavigate(action: Action.Navigate, onResult: (Boolean, String) -> Unit) {
        val globalAction = when (action.type) {
            NavType.BACK -> GLOBAL_ACTION_BACK
            NavType.HOME -> GLOBAL_ACTION_HOME
            NavType.RECENTS -> GLOBAL_ACTION_RECENTS
        }
        val success = performGlobalAction(globalAction)
        onResult(success, if (success) "navigate ${action.type}" else "failed to navigate ${action.type}")
    }

    private fun executeScroll(action: Action.Scroll, onResult: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { onResult(false, "no window root"); return }

        val wm = getSystemService(android.view.WindowManager::class.java) ?: run { onResult(false, "no window service"); root.recycle(); return }
        val display = wm.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)

        val fromX: Int
        val fromY: Int
        val toX: Int
        val toY: Int
        val midX = size.x / 2
        val midY = size.y / 2
        val offset = (if (action.distance == ScrollDistance.SHORT) 200
            else if (action.distance == ScrollDistance.HALF) size.y / 3
            else size.y * 2 / 3)

        when (action.direction) {
            ScrollDirection.UP -> { fromX = midX; fromY = midY + offset / 2; toX = midX; toY = midY - offset / 2 }
            ScrollDirection.DOWN -> { fromX = midX; fromY = midY - offset / 2; toX = midX; toY = midY + offset / 2 }
            ScrollDirection.LEFT -> { fromX = midX + offset / 2; fromY = midY; toX = midX - offset / 2; toY = midY }
            ScrollDirection.RIGHT -> { fromX = midX - offset / 2; fromY = midY; toX = midX + offset / 2; toY = midY }
        }

        val path = Path().apply { moveTo(fromX.toFloat(), fromY.toFloat()); lineTo(toX.toFloat(), toY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()
        root.recycle()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(true, "scrolled ${action.direction}")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(false, "scroll cancelled")
            }
        }, null)
    }

    private fun executeType(action: Action.Type, onResult: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { onResult(false, "no window root"); return }

        val focusNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode == null) {
            root.recycle()
            onResult(false, "no focused input field")
            return
        }

        val bundle = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
        }
        val success = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        focusNode.recycle()
        root.recycle()
        onResult(success, if (success) "typed text" else "failed to type")
    }

    private fun executeSequence(action: Action.Sequence, onResult: (Boolean, String) -> Unit) {
        val results = mutableListOf<String>()
        var failed = false
        val stepDuration = 200L

        fun runNext(index: Int) {
            if (failed || index >= action.steps.size) {
                onResult(!failed, results.joinToString("; "))
                return
            }
            executeAction(action.steps[index]) { success, msg ->
                results.add(msg)
                if (!success) failed = true
                android.os.Handler(mainLooper).postDelayed({ runNext(index + 1) }, stepDuration)
            }
        }
        runNext(0)
    }
}
