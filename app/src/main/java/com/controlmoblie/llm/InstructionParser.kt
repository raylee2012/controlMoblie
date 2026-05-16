package com.controlmoblie.llm

import com.controlmoblie.model.*

class InstructionParser {

    fun parse(rawJson: String): InstructionResult {
        val trimmed = rawJson.trim().removeSurrounding("```json").removeSurrounding("```").trim()
        return try {
            val json = org.json.JSONObject(trimmed)
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
        return """
你是一个手机语音助手。请将用户的语音指令解析为 JSON 格式的操作。

当前屏幕信息:
$screenContext

用户指令: $userText

请只输出 JSON，不要输出其他内容。JSON 格式说明:
- 点击: {"action": "click", "target": "目标文本"}
- 打开App: {"action": "open_app", "package": "包名"}
- 导航: {"action": "navigate", "type": "back|home|recents"}
- 滑动: {"action": "scroll", "direction": "up|down|left|right", "distance": "short|half|full"}
- 输入: {"action": "type", "text": "输入内容"}
- 等待: {"action": "wait", "ms": 毫秒数}
- 组合: {"action": "sequence", "steps": [...]}
""".trimIndent()
    }
}
