package com.controlmoblie.llm

import com.controlmoblie.model.*

class InstructionParser {

    fun parse(rawJson: String): InstructionResult {
        android.util.Log.d("InstructionParser", "raw LLM output: <<<${rawJson}>>>")
        var trimmed = rawJson.trim()

        if (trimmed.startsWith("<|im_start|>")) {
            val nlIdx = trimmed.indexOf('\n')
            if (nlIdx > 0) {
                trimmed = trimmed.substring(nlIdx + 1).trim()
            }
        }
        if (trimmed.startsWith("assistant")) {
            trimmed = trimmed.removePrefix("assistant").trim()
        }

        val jsonStart = trimmed.indexOf('{')
        if (jsonStart > 0) {
            trimmed = trimmed.substring(jsonStart).trim()
        }
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonEnd >= 0 && jsonEnd < trimmed.length - 1) {
            trimmed = trimmed.substring(0, jsonEnd + 1).trim()
        }

        if (trimmed.startsWith("```")) {
            val firstNewline = trimmed.indexOf('\n')
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline).trim()
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.dropLast(3).trim()
            }
        }

        android.util.Log.d("InstructionParser", "cleaned for parsing: <<<${trimmed}>>>")
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
            "open_app" -> Action.OpenApp(json.optString("package", ""), json.optString("displayName", ""))
            "open_wechat_page" -> Action.OpenWeChatPage(json.optString("page", ""))
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
            "你是手机助手，将语音指令转为JSON操作。屏幕:$screenContext"
        } else {
            "你是手机助手，将语音指令转为JSON操作。"
        }
        return "<|im_start|>system\n$systemMsg<|im_end|>\n<|im_start|>user\n$userText<|im_end|>\n<|im_start|>assistant\n"
    }
}
