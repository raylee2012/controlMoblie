package com.controlmoblie.model

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo

sealed class Action {
    data class Click(val target: String) : Action()
    data class OpenApp(val `package`: String) : Action()
    data class Navigate(val type: NavType) : Action()
    data class Scroll(val direction: ScrollDirection, val distance: ScrollDistance = ScrollDistance.HALF) : Action()
    data class Type(val text: String) : Action()
    data class Wait(val ms: Long) : Action()
    data class Sequence(val steps: List<Action>) : Action()
}

enum class NavType { BACK, HOME, RECENTS }
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
enum class ScrollDistance { SHORT, HALF, FULL }

data class InstructionResult(
    val action: Action?,
    val error: String? = null
)

data class ScreenState(
    val nodeTexts: List<String> = emptyList(),
    val packageName: String = ""
)
