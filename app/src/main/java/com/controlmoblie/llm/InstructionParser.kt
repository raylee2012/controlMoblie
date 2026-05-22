package com.controlmoblie.llm

import com.controlmoblie.model.*

class InstructionParser {

    fun parse(rawJson: String): InstructionResult {
        var trimmed = rawJson.trim()
        if (trimmed.startsWith("```")) {
            val firstNewline = trimmed.indexOf('\n')
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline).trim()
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.dropLast(3).trim()
            }
        }
        return try {
            val json = org.json.JSONObject(trimmed)
            val actionType = json.optString("action", "")
            val message = json.optString("message", "")
            if (actionType == "error" || actionType == "") {
                return InstructionResult(
                    action = null,
                    error = if (message.isNotBlank()) message else "无法解析指令: $actionType"
                )
            }
            val action = parseAction(json)
            InstructionResult(action = action)
        } catch (e: Exception) {
            InstructionResult(
                action = null,
                error = "JSON 解析失败: ${e.message}"
            )
        }
    }

    private fun parseAction(json: org.json.JSONObject): Action? {
        val actionType = json.optString("action", "") ?: return null
        return when (actionType) {
            "click" -> Action.Click(json.optString("target", ""))
            "open_app" -> Action.OpenApp(json.optString("package", ""))
            "navigate" -> Action.Navigate(
                try { NavType.valueOf(json.optString("type", "").uppercase()) }
                catch (e: Exception) { return null }
            )
            "scroll" -> Action.Scroll(
                direction = try { ScrollDirection.valueOf(json.optString("direction", "").uppercase()) }
                    catch (e: Exception) { return null },
                distance = try { ScrollDistance.valueOf(json.optString("distance", "half").uppercase()) }
                    catch (e: Exception) { ScrollDistance.HALF }
            )
            "type" -> Action.Type(json.optString("text", ""))
            "wait" -> Action.Wait(json.optLong("ms", 1000))
            "sequence" -> {
                val steps = json.optJSONArray("steps")
                if (steps != null) {
                    val actions = mutableListOf<Action>()
                    for (i in 0 until steps.length()) {
                        steps.getJSONObject(i)?.let { parseAction(it)?.let { a -> actions.add(a) } }
                    }
                    Action.Sequence(actions)
                } else null
            }
            else -> null
        }
    }

    fun buildPrompt(userText: String, screenContext: String): String {
        val systemMsg = if (screenContext.isNotBlank()) {
            "你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。\n\n当前屏幕信息:\n$screenContext"
        } else {
            "你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。"
        }
        return "<|im_start|>system\n$systemMsg\n请只输出 JSON，不要输出其他内容。JSON 格式说明:\n- 点击: {\"action\": \"click\", \"target\": \"目标文本\"}\n- 打开App: {\"action\": \"open_app\", \"package\": \"包名\"}\n- 导航: {\"action\": \"navigate\", \"type\": \"back|home|recents\"}\n- 滑动: {\"action\": \"scroll\", \"direction\": \"up|down|left|right\", \"distance\": \"short|half|full\"}\n- 输入: {\"action\": \"type\", \"text\": \"输入内容\"}\n- 等待: {\"action\": \"wait\", \"ms\": 毫秒数}\n- 组合: {\"action\": \"sequence\", \"steps\": [...]}<|im_end|>\n<|im_start|>user\n$userText<|im_end|>\n<|im_start|>assistant\n"
    }
}
