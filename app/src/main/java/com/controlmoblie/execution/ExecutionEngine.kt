package com.controlmoblie.execution

import com.controlmoblie.model.Action
import com.controlmoblie.service.ControlAccessibilityService

class ExecutionEngine(private val accessibilityService: ControlAccessibilityService?) {

    data class ExecutionResult(val success: Boolean, val message: String)

    fun execute(action: Action, onResult: (ExecutionResult) -> Unit) {
        if (accessibilityService == null) {
            onResult(ExecutionResult(false, "无障碍服务未连接"))
            return
        }
        accessibilityService.executeAction(action) { success, msg ->
            onResult(ExecutionResult(success, msg))
        }
    }

    fun executeSequence(actions: List<Action>, onComplete: (List<ExecutionResult>) -> Unit) {
        val results = mutableListOf<ExecutionResult>()
        fun runNext(index: Int) {
            if (index >= actions.size) {
                onComplete(results)
                return
            }
            execute(actions[index]) { result ->
                results.add(result)
                runNext(index + 1)
            }
        }
        runNext(0)
    }
}
