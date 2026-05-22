package com.controlmoblie.overlay

import android.content.Context
import android.view.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

enum class OverlayState { IDLE, LISTENING, PROCESSING, EXECUTING, ERROR }

private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore() {
        savedStateRegistryController.performRestore(null)
    }
}

private val TAG_LIFECYCLE_OWNER = com.controlmoblie.R.id.view_tree_lifecycle_owner
private val TAG_SAVED_STATE_OWNER = com.controlmoblie.R.id.view_tree_saved_state_registry_owner

private fun setLifecycleOwner(view: View, owner: LifecycleOwner) {
    view.setTag(TAG_LIFECYCLE_OWNER, owner)
}

private fun setSavedStateOwner(view: View, owner: SavedStateRegistryOwner) {
    view.setTag(TAG_SAVED_STATE_OWNER, owner)
}

class ControlOverlay(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var isShowing = false
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var _state by mutableStateOf(OverlayState.IDLE)
    private var _lastText by mutableStateOf("")
    private var _lastResult by mutableStateOf("")
    private var onToggleListener: (() -> Unit)? = null
    private var onStopListener: (() -> Unit)? = null

    fun setOnToggleListener(l: () -> Unit) { onToggleListener = l }
    fun setOnStopListener(l: () -> Unit) { onStopListener = l }

    fun show() {
        if (isShowing) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 200

        val owner = OverlayLifecycleOwner()
        owner.performRestore()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycleOwner = owner

        val frame = FrameLayout(context)
        setLifecycleOwner(frame, owner)
        setSavedStateOwner(frame, owner)

        val composeView = ComposeView(context)
        setLifecycleOwner(composeView, owner)
        setSavedStateOwner(composeView, owner)
        composeView.setContent {
            OverlayContent(
                state = _state,
                lastText = _lastText,
                lastResult = _lastResult,
                onToggle = { onToggleListener?.invoke() },
                onStop = { onStopListener?.invoke() }
            )
        }

        frame.addView(composeView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        overlayView = frame
        windowManager.addView(frame, params)
        isShowing = true
    }

    fun dismiss() {
        if (!isShowing) return
        overlayView?.let { windowManager.removeView(it) }
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        lifecycleOwner = null
        overlayView = null
        isShowing = false
    }

    fun updateState(state: OverlayState, text: String = "", result: String = "") {
        _state = state
        _lastText = text
        _lastResult = result
    }

    val isVisible: Boolean get() = isShowing

    @Composable
    private fun OverlayContent(
        state: OverlayState,
        lastText: String,
        lastResult: String,
        onToggle: () -> Unit,
        onStop: () -> Unit
    ) {
        val bgColor = when (state) {
            OverlayState.IDLE -> Color(0xAA, 0xAA, 0xAA, 0xCC)
            OverlayState.LISTENING -> Color(0x00, 0x96, 0x88, 0xCC)
            OverlayState.PROCESSING -> Color(0xFF, 0xA0, 0x00, 0xCC)
            OverlayState.EXECUTING -> Color(0x21, 0x96, 0xF3, 0xCC)
            OverlayState.ERROR -> Color(0xE5, 0x39, 0x35, 0xCC)
        }

        val statusText = when (state) {
            OverlayState.IDLE -> "\u5F85\u547D"
            OverlayState.LISTENING -> "\u503E\u542C\u4E2D..."
            OverlayState.PROCESSING -> "\u601D\u8003\u4E2D..."
            OverlayState.EXECUTING -> "\u6267\u884C\u4E2D..."
            OverlayState.ERROR -> "\u9519\u8BEF"
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE, 0xEE, 0xEE, 0xDD)),
            modifier = Modifier.widthIn(min = 80.dp, max = 240.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(bgColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("\u23F8", fontSize = 10.sp, modifier = Modifier.clickable(onClick = onToggle))
                    Spacer(Modifier.width(4.dp))
                    Text("\u2715", fontSize = 10.sp, color = Color.Red,
                        modifier = Modifier.clickable(onClick = onStop))
                }
                if (lastText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(lastText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (lastResult.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(lastResult, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                }
            }
        }
    }
}