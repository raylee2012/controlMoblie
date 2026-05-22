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
import com.controlmoblie.util.AppResolver
import com.controlmoblie.util.ScreenReader

class ControlAccessibilityService : AccessibilityService() {

    var lastScreenState: ScreenState = ScreenState()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

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
        lastScreenState = ScreenReader.readScreen(root)
        root.recycle()
        Log.d(TAG, "updateScreenState: collected ${lastScreenState.nodeTexts.size} texts")
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

    private fun findClickableByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        Log.d(TAG, "findClickableByText: '$text' found ${nodes.size} text matches")

        // also try contentDescription if text search yields nothing clickable
        var result: AccessibilityNodeInfo? = null
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    result = current
                    break
                }
                val parent = current.parent
                if (current !== node) current.recycle()
                current = parent
            }
            if (result != null) break
        }
        nodes.forEach { if (it !== result && it !== result?.parent) it.recycle() }

        // fallback: try contentDescription search
        if (result == null) {
            result = findClickableByContentDesc(root, text)
        }

        return result
    }

    private fun findClickableByContentDesc(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isEmpty()) {
            Log.w(TAG, "findClickableByContentDesc: '$text' - no text or contentDesc match at all")
            return null
        }
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    nodes.forEach { if (it !== current) it.recycle() }
                    return current
                }
                val parent = current.parent
                if (current !== node) current.recycle()
                current = parent
            }
        }
        nodes.forEach { it.recycle() }
        return null
    }

    private val wechatTabPositions = mapOf(
        "微信" to 0, "聊天" to 0,
        "通讯录" to 1, "联系人" to 1,
        "发现" to 2,
        "我" to 3, "我的" to 3,
    )

    private fun performCoordinateClick(x: Int, y: Int, onResult: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { onResult(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { onResult(false) }
        }, null)
    }

    private fun executeClick(action: Action.Click, onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "executeClick: target=${action.target}")
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "executeClick: no window root")
            onResult(false, "no window root")
            return
        }

        val clickable = findClickableByText(root, action.target)
        if (clickable != null) {
            val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            clickable.recycle()
            root.recycle()
            Log.d(TAG, "executeClick: target=${action.target} text-match success=$clicked")
            onResult(clicked, if (clicked) "已点击 ${action.target}" else "无法点击 ${action.target}")
            return
        }

        // fallback: coordinate-based click for WeChat bottom tabs (MIUI blocks text)
        val tabIndex = wechatTabPositions[action.target]
        if (tabIndex != null) {
            root.recycle()
            val wm = getSystemService(android.view.WindowManager::class.java)
            val size = android.graphics.Point()
            wm?.defaultDisplay?.getRealSize(size)
            val density = resources.displayMetrics.density
            val tabW = size.x / 4
            val x = tabW * tabIndex + tabW / 2
            val y = size.y - (4 * density).toInt()  // near screen bottom edge
            Log.d(TAG, "executeClick: target=${action.target} coordinate fallback x=$x y=$y")
            performCoordinateClick(x, y) { clicked ->
                onResult(clicked, if (clicked) "已点击 ${action.target}" else "无法点击 ${action.target}")
            }
            return
        }

        Log.w(TAG, "executeClick: target '${action.target}' not found")
        root.recycle()
        onResult(false, "未找到 ${action.target}")
    }

    private fun executeOpenApp(action: Action.OpenApp, onResult: (Boolean, String) -> Unit) {
        val packageName = AppResolver.resolve(action.`package`)
        Log.d(TAG, "executeOpenApp: name=${action.`package`} resolved=$packageName")
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "executeOpenApp: package not found $packageName")
            val displayName = action.displayName.ifBlank { action.`package` }
            onResult(false, "未找到应用: $displayName")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        val displayName = action.displayName.ifBlank { action.`package` }
        onResult(true, "已打开 $displayName")
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
        val msg = when (action.type) {
            NavType.BACK -> if (success) "已返回" else "返回失败"
            NavType.HOME -> if (success) "已回桌面" else "回桌面失败"
            NavType.RECENTS -> if (success) "最近任务" else "切换失败"
        }
        onResult(success, msg)
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
        val isHorizontal = action.direction == ScrollDirection.LEFT || action.direction == ScrollDirection.RIGHT
        val baseSize = if (isHorizontal) size.x else size.y
        val offset = when (action.distance) {
            ScrollDistance.SHORT -> SCROLL_SHORT_OFFSET_PX
            ScrollDistance.HALF  -> baseSize / 3
            ScrollDistance.FULL  -> baseSize * 2 / 3
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
                val directionText = when (action.direction) {
                    ScrollDirection.UP -> "上滑"
                    ScrollDirection.DOWN -> "下滑"
                    ScrollDirection.LEFT -> "左滑"
                    ScrollDirection.RIGHT -> "右滑"
                }
                onResult(true, "已$directionText")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "executeScroll: cancelled")
                onResult(false, "滑动失败")
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
            onResult(false, "未找到输入框")
            return
        }

        val bundle = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
        }
        val success = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        focusNode.recycle()
        root.recycle()
        Log.d(TAG, "executeType: text=${action.text} success=$success")
        onResult(success, if (success) "已输入" else "输入失败")
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

    companion object {
        var instance: ControlAccessibilityService? = null
        const val SCROLL_GESTURE_DURATION_MS = 200L
        const val SEQUENCE_STEP_DELAY_MS = 200L
        const val SCROLL_SHORT_OFFSET_PX = 200
        const val TAG = "ControlAccessibility"
    }
}
