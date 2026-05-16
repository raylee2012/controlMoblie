package com.controlmoblie.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
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

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    private fun updateScreenState() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "updateScreenState: root is null")
            return
        }
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        root.recycle()
        Log.d(TAG, "updateScreenState: collected ${texts.size} texts")
        lastScreenState = ScreenState(
            nodeTexts = texts,
            packageName = packageName
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) texts.add(text)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, texts)
            child.recycle()
        }
    }

    fun executeAction(action: Action, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeAction: $action")
        when (action) {
            is Action.Click -> executeClick(action, onResult)
            is Action.OpenApp -> executeOpenApp(action, onResult)
            is Action.Navigate -> executeNavigate(action, onResult)
            is Action.Scroll -> executeScroll(action, onResult)
            is Action.Type -> executeType(action, onResult)
            is Action.Wait -> {
                Log.d(TAG, "executeAction: wait ${action.ms}ms")
                android.os.Handler(mainLooper).postDelayed({
                    onResult(true, "waited ${action.ms}ms")
                }, action.ms)
            }
            is Action.Sequence -> executeSequence(action, onResult)
        }
    }

    private fun executeClick(action: Action.Click, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeClick: target=${action.target}")
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "executeClick: no window root")
            onResult(false, "no window root")
            return
        }

        val nodes = root.findAccessibilityNodeInfosByText(action.target)
        if (nodes.isEmpty()) {
            Log.w(TAG, "executeClick: target '${action.target}' not found")
            root.recycle()
            onResult(false, "target '${action.target}' not found")
            return
        }

        val node = nodes[0]
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        nodes.forEach { it.recycle() }
        root.recycle()
        Log.d(TAG, "executeClick: target=${action.target} success=$clicked")
        onResult(clicked, if (clicked) "clicked ${action.target}" else "failed to click ${action.target}")
    }

    private fun executeOpenApp(action: Action.OpenApp, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeOpenApp: package=${action.`package`}")
        val intent = packageManager.getLaunchIntentForPackage(action.`package`)
        if (intent == null) {
            Log.w(TAG, "executeOpenApp: package not found ${action.`package`}")
            onResult(false, "package ${action.`package`} not found")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        onResult(true, "opened ${action.`package`}")
    }

    private fun executeNavigate(action: Action.Navigate, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeNavigate: type=${action.type}")
        val globalAction = when (action.type) {
            NavType.BACK -> GLOBAL_ACTION_BACK
            NavType.HOME -> GLOBAL_ACTION_HOME
            NavType.RECENTS -> GLOBAL_ACTION_RECENTS
        }
        val success = performGlobalAction(globalAction)
        Log.d(TAG, "executeNavigate: success=$success")
        onResult(success, if (success) "navigate ${action.type}" else "failed to navigate ${action.type}")
    }

    private fun executeScroll(action: Action.Scroll, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeScroll: direction=${action.direction} distance=${action.distance}")
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "executeScroll: no window root")
            onResult(false, "no window root")
            return
        }

        val wm = getSystemService(android.view.WindowManager::class.java) ?: run {
            Log.w(TAG, "executeScroll: no window service")
            onResult(false, "no window service")
            root.recycle()
            return
        }
        val display = wm.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)

        val fromX: Int
        val fromY: Int
        val toX: Int
        val toY: Int
        val midX = size.x / 2
        val midY = size.y / 2
        val offset = when (action.distance) {
            ScrollDistance.SHORT -> SCROLL_SHORT_OFFSET_PX
            ScrollDistance.HALF  -> size.y / 3
            ScrollDistance.FULL  -> size.y * 2 / 3
        }

        when (action.direction) {
            ScrollDirection.UP -> { fromX = midX; fromY = midY + offset / 2; toX = midX; toY = midY - offset / 2 }
            ScrollDirection.DOWN -> { fromX = midX; fromY = midY - offset / 2; toX = midX; toY = midY + offset / 2 }
            ScrollDirection.LEFT -> { fromX = midX + offset / 2; fromY = midY; toX = midX - offset / 2; toY = midY }
            ScrollDirection.RIGHT -> { fromX = midX - offset / 2; fromY = midY; toX = midX + offset / 2; toY = midY }
        }

        val path = Path().apply { moveTo(fromX.toFloat(), fromY.toFloat()); lineTo(toX.toFloat(), toY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_GESTURE_DURATION_MS))
            .build()
        root.recycle()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "executeScroll: completed ${action.direction}")
                onResult(true, "scrolled ${action.direction}")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "executeScroll: cancelled")
                onResult(false, "scroll cancelled")
            }
        }, null)
    }

    private fun executeType(action: Action.Type, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeType: text=${action.text}")
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "executeType: no window root")
            onResult(false, "no window root")
            return
        }

        val focusNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode == null) {
            Log.w(TAG, "executeType: no focused input field")
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
        Log.d(TAG, "executeType: text=${action.text} success=$success")
        onResult(success, if (success) "typed text" else "failed to type")
    }

    private fun executeSequence(action: Action.Sequence, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeSequence: ${action.steps.size} steps")
        val results = mutableListOf<String>()
        var failed = false
        val stepDuration = SEQUENCE_STEP_DELAY_MS

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

    private companion object {
        const val SCROLL_GESTURE_DURATION_MS = 200L
        const val SEQUENCE_STEP_DELAY_MS = 200L
        const val SCROLL_SHORT_OFFSET_PX = 200
        const val TAG = "ControlAccessibility"
    }
}
